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
package org.eclipse.jdt.ls.core.internal.commands;

import java.util.ArrayList;
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
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.legacy.typeHierarchy.ResolveTypeHierarchyItemParams;
import org.eclipse.lsp4j.legacy.typeHierarchy.TypeHierarchyDirection;
import org.eclipse.lsp4j.legacy.typeHierarchy.TypeHierarchyItem;
import org.eclipse.lsp4j.legacy.typeHierarchy.TypeHierarchyParams;

public class TypeHierarchyCommand {

	public TypeHierarchyItem typeHierarchy(TypeHierarchyParams params, IProgressMonitor monitor) {
		if (params == null) {
			return null;
		}
		TextDocumentIdentifier textDocument = params.getTextDocument();
		if (textDocument == null) {
			return null;
		}
		Position position = params.getPosition();
		String uri = textDocument.getUri();
		TypeHierarchyDirection direction = params.getDirection();
		int resolve = params.getResolve();
		return getTypeHierarchy(uri, position, direction, resolve, null, monitor);
	}

	public TypeHierarchyItem resolveTypeHierarchy(ResolveTypeHierarchyItemParams params, IProgressMonitor monitor) {
		if (params == null) {
			return null;
		}
		TypeHierarchyItem item = params.getItem();
		if (item == null) {
			return null;
		}
		Range range = item.getRange();
		if (range == null) {
			return null;
		}
		Position position = range.getStart();
		String uri = item.getUri();
		TypeHierarchyDirection direction = params.getDirection();
		int resolve = params.getResolve();
		return getTypeHierarchy(uri, position, direction, resolve, item, monitor);
	}

	private TypeHierarchyItem getTypeHierarchy(String uri, Position position, TypeHierarchyDirection direction, int resolve, TypeHierarchyItem itemInput, IProgressMonitor monitor) {
		if (uri == null || position == null || direction == null) {
			return null;
		}
		try {
			IMember member = null;
			IMethod targetMethod = null;
			if (itemInput == null) {
				member = getMember(uri, position, monitor);
				if (member instanceof IMethod) {
					targetMethod = (IMethod) member;
				}
			} else {
				Map<String, String> data = JSONUtility.toModel(itemInput.getData(), Map.class);
				String handleIdentifier = data.get("element");
				IJavaElement element = JavaCore.create(handleIdentifier);
				String methodIdentifier = data.get("method");
				if (methodIdentifier != null) {
					targetMethod = (IMethod) JavaCore.create(methodIdentifier);
				}
				if (element instanceof IType || element instanceof IMethod) {
					member = (IMember) element;
				} else if (element instanceof IOrdinaryClassFile classFile) {
					member = classFile.getType();
				} else {
					return null;
				}
			}
			TypeHierarchyItem item = TypeHierarchyCommand.toTypeHierarchyItem(member);
			if (item == null) {
				return null;
			}
			resolve(item, member, targetMethod, direction, resolve, monitor);
			return item;
		} catch (JavaModelException e) {
			return null;
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
		TypeHierarchyItem item = new TypeHierarchyItem();
		item.setRange(location.getRange());
		item.setUri(location.getUri());
		item.setSelectionRange(selectLocation.getRange());

		IType type = null;
		if (member instanceof IType) {
			type = (IType) member;
		} else {
			type = member.getDeclaringType();
		}

		String fullyQualifiedName = type.getFullyQualifiedName();
		int index = fullyQualifiedName.lastIndexOf('.');
		if (index >= 1 && index < fullyQualifiedName.length() - 1 && !type.isAnonymous()) {
			item.setName(fullyQualifiedName.substring(index + 1));
			item.setDetail(fullyQualifiedName.substring(0, index));
		} else {
			item.setName(JDTUtils.getName(type));
			IPackageFragment packageFragment = type.getPackageFragment();
			if (packageFragment != null) {
				item.setDetail(packageFragment.getElementName());
			}
		}
		item.setKind(excludeMember ? SymbolKind.Null : DocumentSymbolHandler.mapKind(type));
		item.setDeprecated(JDTUtils.isDeprecated(member));
		Map<String, String> data = new HashMap<>();
		data.put("element", member.getHandleIdentifier());
		if (targetMethod != null) {
			data.put("method", targetMethod.getHandleIdentifier());
			data.put("method_name", targetMethod.getElementName());
		} else if (member instanceof IMethod) {
			data.put("method", member.getHandleIdentifier());
			data.put("method_name", member.getElementName());
		}
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

	private void resolve(TypeHierarchyItem item, IMember member, IMethod targetMethod, TypeHierarchyDirection direction, int resolve, IProgressMonitor monitor) throws JavaModelException {
		if (monitor.isCanceled() || resolve <= 0) {
			return;
		}

		IType type = null;
		if (member instanceof IType) {
			type = (IType) member;
		} else {
			type = member.getDeclaringType();
		}

		ITypeHierarchy typeHierarchy;
		if (direction == TypeHierarchyDirection.Parents) {
			typeHierarchy = type.newSupertypeHierarchy(DefaultWorkingCopyOwner.PRIMARY, monitor);
		} else {
			ICompilationUnit[] workingCopies = JavaModelManager.getJavaModelManager().getWorkingCopies(DefaultWorkingCopyOwner.PRIMARY, true);
			typeHierarchy = type.newTypeHierarchy(workingCopies, monitor);
		}
		if (direction == TypeHierarchyDirection.Children || direction == TypeHierarchyDirection.Both) {
			List<TypeHierarchyItem> childrenItems = new ArrayList<>();
			IType[] children = typeHierarchy.getSubtypes(type);
			for (IType childType : children) {
				if (monitor.isCanceled()) {
					return;
				}
				TypeHierarchyItem childItem = null;
				if (targetMethod != null) {
					IMethod[] matches = childType.findMethods(targetMethod);
					boolean excludeMember = matches == null || matches.length == 0;
					childItem = TypeHierarchyCommand.toTypeHierarchyItem(excludeMember ? childType : matches[0], excludeMember, targetMethod);
				} else {
					childItem = TypeHierarchyCommand.toTypeHierarchyItem(childType);
				}
				if (childItem == null) {
					continue;
				}
				resolve(childItem, childType, targetMethod, direction, resolve - 1, monitor);
				childrenItems.add(childItem);
			}
			item.setChildren(childrenItems);
		}
		if (direction == TypeHierarchyDirection.Parents || direction == TypeHierarchyDirection.Both) {
			List<TypeHierarchyItem> parentsItems = new ArrayList<>();
			IType[] parents = typeHierarchy.getSupertypes(type);
			for (IType parentType : parents) {
				if (monitor.isCanceled()) {
					return;
				}
				TypeHierarchyItem parentItem = null;
				if (targetMethod != null) {
					IMethod[] matches = parentType.findMethods(targetMethod);
					boolean excludeMember = matches == null || matches.length == 0;
					// Do not show java.lang.Object unless target method is based there
					if (!excludeMember || !"java.lang.Object".equals(parentType.getFullyQualifiedName())) {
						parentItem = TypeHierarchyCommand.toTypeHierarchyItem(excludeMember ? parentType : matches[0], excludeMember, targetMethod);
					}
				} else {
					parentItem = TypeHierarchyCommand.toTypeHierarchyItem(parentType);
				}
				if (parentItem == null) {
					continue;
				}
				resolve(parentItem, parentType, targetMethod, direction, resolve - 1, monitor);
				parentsItems.add(parentItem);
			}
			item.setParents(parentsItems);
		}
	}
}
