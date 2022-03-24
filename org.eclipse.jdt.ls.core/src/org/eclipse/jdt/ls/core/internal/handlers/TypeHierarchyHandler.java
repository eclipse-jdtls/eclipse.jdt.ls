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
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
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
		} else {
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
			if (direction == TypeHierarchyDirection.Supertype) {
				typeHierarchy = type.newSupertypeHierarchy(DefaultWorkingCopyOwner.PRIMARY, monitor);
				hierarchyTypes = typeHierarchy.getSupertypes(type);
			} else {
				ICompilationUnit[] workingCopies = JavaModelManager.getJavaModelManager().getWorkingCopies(DefaultWorkingCopyOwner.PRIMARY, true);
				typeHierarchy = type.newTypeHierarchy(workingCopies, monitor);
				hierarchyTypes = typeHierarchy.getSubtypes(type);
			}
			for (IType hierarchyType : hierarchyTypes) {
				if (monitor.isCanceled()) {
					return Collections.emptyList();
				}
				TypeHierarchyItem item = null;
				if (targetMethod != null) {
					IMethod[] matches = hierarchyType.findMethods(targetMethod);
					boolean excludeMember = matches == null || matches.length == 0;
					// Do not show java.lang.Object unless target method is based there
					if (!excludeMember || !"java.lang.Object".equals(hierarchyType.getFullyQualifiedName())) {
						item = TypeHierarchyHandler.toTypeHierarchyItem(excludeMember ? hierarchyType : matches[0], excludeMember, targetMethod);
					}
				} else {
					item = TypeHierarchyHandler.toTypeHierarchyItem(hierarchyType);
				}
				if (item == null) {
					continue;
				}
				items.add(item);
			}
			return items;
		} catch (JavaModelException e) {
			return Collections.emptyList();
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
		SymbolKind kind = excludeMember ? SymbolKind.Null : DocumentSymbolHandler.mapKind(type);
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
