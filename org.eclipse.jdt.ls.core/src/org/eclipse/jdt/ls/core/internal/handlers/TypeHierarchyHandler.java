/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
import org.eclipse.jdt.ls.core.internal.SearchUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;

public class TypeHierarchyHandler {

	public enum TypeHierarchyDirection {
		Subtype, Supertype;
	}

	private static class TypeHierarchyItemData {
		private String handleIdentifier;
		private String methodIdentifier;
		private String methodName;

		public TypeHierarchyItemData(String handleIdentifier, String methodIdentifier, String methodName) {
			this.handleIdentifier = handleIdentifier;
			this.methodIdentifier = methodIdentifier;
			this.methodName = methodName;
		}

		private static TypeHierarchyItemData getTypeHierarchyItemData(Object data) {
			if (data == null) {
				return null;
			}
			@SuppressWarnings("unchecked")
			Map<String, String> map = JSONUtility.toModel(data, Map.class);
			String handleIdentifier = map.get("element");
			String methodIdentifier = map.get("method");
			String methodName = map.get("method_name");
			return new TypeHierarchyItemData(handleIdentifier, methodIdentifier, methodName);
		}
	}

	public List<TypeHierarchyItem> prepareTypeHierarchy(TypeHierarchyPrepareParams params, IProgressMonitor monitor) {
		if (params == null) {
			return Collections.emptyList();
		}
		TextDocumentIdentifier textDocument = params.getTextDocument();
		if (textDocument == null) {
			return Collections.emptyList();
		}
		Position position = params.getPosition();
		String uri = textDocument.getUri();
		return getTypeHierarchyItems(uri, position, monitor);
	}

	private List<TypeHierarchyItem> getTypeHierarchyItems(String uri, Position position, IProgressMonitor monitor) {
		if (uri == null || position == null) {
			return Collections.emptyList();
		}
		try {
			IMember member = getMember(uri, position, monitor);
			IMethod targetMethod = null;
			if (member instanceof IMethod) {
				targetMethod = (IMethod) member;
			}
			TypeHierarchyItem item = targetMethod == null ? TypeHierarchyHandler.toTypeHierarchyItem(member) : TypeHierarchyHandler.toTypeHierarchyItem(member, false, targetMethod);
			if (item == null) {
				return Collections.emptyList();
			}
			return Arrays.asList(item);
		} catch (JavaModelException e) {
			return Collections.emptyList();
		}
	}

	public List<TypeHierarchyItem> getSupertypeItems(TypeHierarchySupertypesParams params, IProgressMonitor monitor) {
		return getTypeHierarchyItems(params.getItem(), TypeHierarchyDirection.Supertype, monitor);
	}

	public List<TypeHierarchyItem> getSubtypeItems(TypeHierarchySubtypesParams params, IProgressMonitor monitor) {
		return getTypeHierarchyItems(params.getItem(), TypeHierarchyDirection.Subtype, monitor);
	}

	private List<TypeHierarchyItem> getTypeHierarchyItems(TypeHierarchyItem item, TypeHierarchyDirection direction, IProgressMonitor monitor) {
		TypeHierarchyItemData data = TypeHierarchyItemData.getTypeHierarchyItemData(item.getData());
		if (data == null) {
			return Collections.emptyList();
		}
		IJavaElement element = JavaCore.create(data.handleIdentifier);
		IMember member = null;
		IMethod targetMethod = null;
		if (data.methodIdentifier != null) {
			targetMethod = (IMethod) JavaCore.create(data.methodIdentifier);
		}
		if (element instanceof IType || element instanceof IMethod) {
			member = (IMember) element;
		} else if (element instanceof IOrdinaryClassFile classFile) {
			member = classFile.getType();
		}
		if (member == null) {
			// Element could not be reconstituted via JavaCore.create() —
			// this happens for contributed (non-Java) elements whose
			// handles are not resolvable by the Java model. Re-resolve
			// via codeSelect using the item's URI and position.
			String uri = item.getUri();
			if (uri != null) {
				try {
					ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
					if (typeRoot != null) {
						Position pos = item.getSelectionRange().getStart();
						IJavaElement resolved = JDTUtils.findElementAtSelection(
								typeRoot, pos.getLine(), pos.getCharacter(),
								JavaLanguageServerPlugin.getPreferencesManager(),
								monitor);
						if (resolved instanceof IType || resolved instanceof IMethod) {
							member = (IMember) resolved;
						}
					}
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException(
							"Failed to resolve type hierarchy element from " + uri, e);
				}
			}
		}
		if (member == null) {
			return Collections.emptyList();
		}
		return resolveTypeHierarchyItems(member, targetMethod, direction, monitor);
	}

	private List<TypeHierarchyItem> resolveTypeHierarchyItems(IMember member, IMethod targetMethod, TypeHierarchyDirection direction, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		IType type = null;
		if (member instanceof IType) {
			type = (IType) member;
		} else {
			type = member.getDeclaringType();
		}
		try {
			ITypeHierarchy typeHierarchy = null;
			List<TypeHierarchyItem> items = new ArrayList<>();
			IType[] hierarchyTypes = null;
			boolean isContributedElement = false;
			ICompilationUnit cu = type.getCompilationUnit();
			if (cu != null) {
				isContributedElement = !JavaCore.isJavaLikeFileName(
						cu.getElementName());
			}
			if (direction == TypeHierarchyDirection.Supertype) {
				if (isContributedElement) {
					// JDT's newSupertypeHierarchy() doesn't work for
					// contributed (non-Java) types. Resolve supertypes
					// from the element's declared supertype names.
					hierarchyTypes = resolveContributedSupertypes(
							type, monitor);
				} else {
					typeHierarchy = type.newSupertypeHierarchy(
							DefaultWorkingCopyOwner.PRIMARY, monitor);
					hierarchyTypes = typeHierarchy.getSupertypes(type);
				}
			} else {
				if (!isContributedElement) {
					ICompilationUnit[] workingCopies = JavaModelManager
							.getJavaModelManager().getWorkingCopies(
									DefaultWorkingCopyOwner.PRIMARY,
									true);
					typeHierarchy = type.newTypeHierarchy(
							workingCopies, monitor);
					hierarchyTypes = typeHierarchy.getSubtypes(type);
				} else {
					hierarchyTypes = new IType[0];
				}
			}
			Set<String> seen = new HashSet<>();
			for (IType hierarchyType : hierarchyTypes) {
				if (monitor.isCanceled()) {
					return Collections.emptyList();
				}
				TypeHierarchyItem item = toHierarchyItem(
						hierarchyType, targetMethod);
				if (item != null) {
					items.add(item);
					seen.add(hierarchyType.getFullyQualifiedName());
				}
			}
			// Supplement subtypes with contributed search participants
			// to discover non-Java types (e.g., Kotlin) that extend
			// or implement this type.
			if (direction == TypeHierarchyDirection.Subtype) {
				supplementWithContributedSubtypes(
						type, items, seen, targetMethod, monitor);
			}
			return items;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(
					"Failed to resolve type hierarchy", e);
			return Collections.emptyList();
		}
	}

	private TypeHierarchyItem toHierarchyItem(IType hierarchyType,
			IMethod targetMethod) throws JavaModelException {
		if (targetMethod != null) {
			IMethod[] matches = hierarchyType.findMethods(targetMethod);
			boolean excludeMember = matches == null
					|| matches.length == 0;
			if (!excludeMember || !"java.lang.Object".equals(
					hierarchyType.getFullyQualifiedName())) {
				return TypeHierarchyHandler.toTypeHierarchyItem(
						excludeMember ? hierarchyType : matches[0],
						excludeMember, targetMethod);
			}
			return null;
		}
		return TypeHierarchyHandler.toTypeHierarchyItem(hierarchyType);
	}

	/**
	 * Resolves supertypes for a contributed (non-Java) type element by
	 * reading its declared supertype names and resolving them. Simple
	 * names are resolved first via the Java model (as FQN), then via
	 * type declaration search across all search participants.
	 */
	private IType[] resolveContributedSupertypes(IType type,
			IProgressMonitor monitor) throws JavaModelException {
		IJavaProject javaProject = type.getJavaProject();
		SearchParticipant[] participants =
				SearchEngine.getSearchParticipants();
		List<IType> supertypes = new ArrayList<>();
		String superclassName = type.getSuperclassName();
		if (superclassName != null) {
			IType resolved = resolveTypeName(
					superclassName, javaProject,
					participants, monitor);
			if (resolved != null) {
				supertypes.add(resolved);
			}
		}
		String[] interfaceNames = type.getSuperInterfaceNames();
		for (String ifName : interfaceNames) {
			IType resolved = resolveTypeName(
					ifName, javaProject,
					participants, monitor);
			if (resolved != null) {
				supertypes.add(resolved);
			}
		}
		return supertypes.toArray(new IType[0]);
	}

	/**
	 * Resolves a type name (simple or fully qualified) to an IType.
	 * Tries direct lookup first, then falls back to a type declaration
	 * search across all search participants.
	 */
	private IType resolveTypeName(String typeName,
			IJavaProject javaProject,
			SearchParticipant[] participants,
			IProgressMonitor monitor) {
		if (typeName == null) {
			return null;
		}
		// Try direct FQN lookup first
		if (javaProject != null) {
			try {
				IType type = javaProject.findType(typeName);
				if (type != null && type.exists()) {
					return type;
				}
			} catch (JavaModelException e) {
				// Fall through to search
			}
		}
		// Search for type declarations matching the simple name
		try {
			SearchPattern pattern = SearchPattern.createPattern(
					typeName, IJavaSearchConstants.TYPE,
					IJavaSearchConstants.DECLARATIONS,
					SearchPattern.R_EXACT_MATCH
							| SearchPattern.R_CASE_SENSITIVE);
			if (pattern == null) {
				return null;
			}
			IJavaSearchScope scope = javaProject != null
					? SearchEngine.createJavaSearchScope(
							new IJavaElement[]{javaProject})
					: SearchEngine.createWorkspaceScope();
			IType[] result = new IType[1];
			SearchRequestor requestor = new SearchRequestor() {
				@Override
				public void acceptSearchMatch(SearchMatch match) {
					if (result[0] == null
							&& match.getElement() instanceof IType t
							&& t.exists()) {
						result[0] = t;
					}
				}
			};
			new SearchEngine().search(pattern,
					participants, scope, requestor, monitor);
			return result[0];
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(
					"Error resolving type name: " + typeName, e);
			return null;
		}
	}

	/**
	 * Supplements the subtype list with types found via contributed
	 * search participants (e.g., Kotlin types that extend a Java type).
	 * Uses a SUPERTYPE_TYPE_REFERENCE search to find types that declare
	 * the given type as their supertype.
	 */
	private void supplementWithContributedSubtypes(IType type,
			List<TypeHierarchyItem> items, Set<String> seen,
			IMethod targetMethod, IProgressMonitor monitor) {
		try {
			SearchPattern pattern = SearchPattern.createPattern(
					type.getFullyQualifiedName(),
					IJavaSearchConstants.TYPE,
					IJavaSearchConstants.IMPLEMENTORS,
					SearchPattern.R_EXACT_MATCH
							| SearchPattern.R_CASE_SENSITIVE);
			if (pattern == null) {
				return;
			}
			SearchParticipant[] contributed =
					SearchUtils.getContributedSearchParticipants();
			if (contributed.length == 0) {
				return;
			}
			IJavaSearchScope scope =
					SearchEngine.createWorkspaceScope();
			List<IType> foundTypes = new ArrayList<>();
			SearchRequestor requestor = new SearchRequestor() {
				@Override
				public void acceptSearchMatch(SearchMatch match) {
					Object element = match.getElement();
					if (element instanceof IType t) {
						foundTypes.add(t);
					}
				}
			};
			new SearchEngine().search(pattern,
					contributed, scope, requestor, monitor);
			for (IType foundType : foundTypes) {
				if (monitor.isCanceled()) {
					return;
				}
				String fqn = foundType.getFullyQualifiedName();
				if (seen.contains(fqn)) {
					continue;
				}
				TypeHierarchyItem item = toHierarchyItem(
						foundType, targetMethod);
				if (item != null) {
					items.add(item);
					seen.add(fqn);
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(
					"Error supplementing type hierarchy with "
					+ "contributed participants", e);
		}
	}

	private IMember getMember(String uri, Position position, IProgressMonitor monitor) throws JavaModelException {
		IJavaElement typeElement = findTypeElement(JDTUtils.resolveTypeRoot(uri), position, monitor);
		if (typeElement instanceof IType type) {
			return type;
		} else if (typeElement instanceof IMethod method) {
			return method;
		} else {
			return null;
		}
	}

	private static IJavaElement findTypeElement(ITypeRoot unit, Position position, IProgressMonitor monitor) throws JavaModelException {
		if (unit == null) {
			return null;
		}
		IJavaElement element = JDTUtils.findElementAtSelection(unit, position.getLine(), position.getCharacter(), JavaLanguageServerPlugin.getPreferencesManager(), monitor);
		if (element == null) {
			if (unit instanceof IOrdinaryClassFile classFile) {
				element = classFile.getType();
			} else if (unit instanceof ICompilationUnit) {
				element = unit.findPrimaryType();
			}
		}
		return element;
	}

	private static TypeHierarchyItem toTypeHierarchyItem(IMember member) throws JavaModelException {
		return toTypeHierarchyItem(member, false, null);
	}

	private static TypeHierarchyItem toTypeHierarchyItem(IMember member, boolean excludeMember, IMethod targetMethod) throws JavaModelException {
		if (member == null) {
			return null;
		}
		Location location = getLocation(member, LocationType.FULL_RANGE);
		Location selectLocation = getLocation(member, LocationType.NAME_RANGE);
		if (location == null || selectLocation == null) {
			return null;
		}

		Range range = location.getRange();
		String uri = location.getUri();
		Range selectionRange = selectLocation.getRange();

		IType type = null;
		if (member instanceof IType) {
			type = (IType) member;
		} else {
			type = member.getDeclaringType();
		}

		String name = null;
		String detail = null;
		String fullyQualifiedName = type.getFullyQualifiedName();
		int index = fullyQualifiedName.lastIndexOf('.');
		if (index >= 1 && index < fullyQualifiedName.length() - 1 && !type.isAnonymous()) {
			name = fullyQualifiedName.substring(index + 1);
			detail = fullyQualifiedName.substring(0, index);
		} else {
			name = JDTUtils.getName(type);
			IPackageFragment packageFragment = type.getPackageFragment();
			if (packageFragment != null) {
				detail = packageFragment.getElementName();
			}
		}
		SymbolKind kind = excludeMember ? SymbolKind.Null : SymbolUtils.mapKind(type);
		List<SymbolTag> tags = new ArrayList<>();
		if (JDTUtils.isDeprecated(member)) {
			tags.add(SymbolTag.Deprecated);
		}
		Map<String, String> data = new HashMap<>();
		data.put("element", member.getHandleIdentifier());
		if (targetMethod != null) {
			data.put("method", targetMethod.getHandleIdentifier());
			data.put("method_name", targetMethod.getElementName());
		} else if (member instanceof IMethod) {
			data.put("method", member.getHandleIdentifier());
			data.put("method_name", member.getElementName());
		}
		TypeHierarchyItem item = new TypeHierarchyItem(name, kind, uri, range, selectionRange, detail);
		item.setTags(tags);
		item.setData(data);
		return item;
	}

	private static Location getLocation(IMember member, LocationType locationType) throws JavaModelException {
		Location location = locationType.toLocation(member);
		if (location == null && member.getClassFile() != null) {
			location = JDTUtils.toLocation(member.getClassFile());
		}
		return location;
	}
}
