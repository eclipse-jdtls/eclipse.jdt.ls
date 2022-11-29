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
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResolveTypeHierarchyItemParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyDirection;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyParams;

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
			IType type = null;
			if (itemInput == null) {
				type = getType(uri, position, monitor);
			} else {
				String handleIdentifier = JSONUtility.toModel(itemInput.getData(), String.class);
				IJavaElement element = JavaCore.create(handleIdentifier);
				if (element instanceof IType theType) {
					type = theType;
				} else if (element instanceof IOrdinaryClassFile classFile) {
					type = classFile.getType();
				} else {
					return null;
				}
			}
			TypeHierarchyItem item = TypeHierarchyCommand.toTypeHierarchyItem(type);
			if (item == null) {
				return null;
			}
			resolve(item, type, direction, resolve, monitor);
			return item;
		} catch (JavaModelException e) {
			return null;
		}
	}

	private IType getType(String uri, Position position, IProgressMonitor monitor) throws JavaModelException {
		IJavaElement typeElement = findTypeElement(JDTUtils.resolveTypeRoot(uri), position, monitor);
		if (typeElement instanceof IType type) {
			return type;
		} else if (typeElement instanceof IMethod method) {
			return method.getDeclaringType();
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

	private static TypeHierarchyItem toTypeHierarchyItem(IType type) throws JavaModelException {
		if (type == null) {
			return null;
		}
		Location location = getLocation(type, LocationType.FULL_RANGE);
		Location selectLocation = getLocation(type, LocationType.NAME_RANGE);
		if (location == null || selectLocation == null) {
			return null;
		}
		TypeHierarchyItem item = new TypeHierarchyItem();
		item.setRange(location.getRange());
		item.setUri(location.getUri());
		item.setSelectionRange(selectLocation.getRange());
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
		item.setKind(DocumentSymbolHandler.mapKind(type));
		item.setDeprecated(JDTUtils.isDeprecated(type));
		item.setData(type.getHandleIdentifier());
		return item;
	}

	private static Location getLocation(IType type, LocationType locationType) throws JavaModelException {
		Location location = locationType.toLocation(type);
		if (location == null && type.getClassFile() != null) {
			location = JDTUtils.toLocation(type.getClassFile());
		}
		return location;
	}

	private void resolve(TypeHierarchyItem item, IType type, TypeHierarchyDirection direction, int resolve, IProgressMonitor monitor) throws JavaModelException {
		if (monitor.isCanceled() || resolve <= 0) {
			return;
		}
		ITypeHierarchy typeHierarchy = (direction == TypeHierarchyDirection.Parents) ? type.newSupertypeHierarchy(DefaultWorkingCopyOwner.PRIMARY, monitor) : type.newTypeHierarchy(type.getJavaProject(), DefaultWorkingCopyOwner.PRIMARY, monitor);
		if (direction == TypeHierarchyDirection.Children || direction == TypeHierarchyDirection.Both) {
			List<TypeHierarchyItem> childrenItems = new ArrayList<>();
			IType[] children = typeHierarchy.getSubtypes(type);
			for (IType childType : children) {
				TypeHierarchyItem childItem = TypeHierarchyCommand.toTypeHierarchyItem(childType);
				if (childItem == null) {
					continue;
				}
				resolve(childItem, childType, direction, resolve - 1, monitor);
				childrenItems.add(childItem);
			}
			item.setChildren(childrenItems);
		}
		if (direction == TypeHierarchyDirection.Parents || direction == TypeHierarchyDirection.Both) {
			List<TypeHierarchyItem> parentsItems = new ArrayList<>();
			IType[] parents = typeHierarchy.getSupertypes(type);
			for (IType parentType : parents) {
				TypeHierarchyItem parentItem = TypeHierarchyCommand.toTypeHierarchyItem(parentType);
				if (parentItem == null) {
					continue;
				}
				resolve(parentItem, parentType, direction, resolve - 1, monitor);
				parentsItems.add(parentItem);
			}
			item.setParents(parentsItems);
		}
	}
}
