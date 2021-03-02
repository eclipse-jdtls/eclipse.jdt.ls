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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType;
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
		return getTypeHierarchy(uri, position, direction, resolve, monitor);
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
		return getTypeHierarchy(uri, position, direction, resolve, monitor);
	}

	private TypeHierarchyItem getTypeHierarchy(String uri, Position position, TypeHierarchyDirection direction, int resolve, IProgressMonitor monitor) {
		if (uri == null || position == null || direction == null) {
			return null;
		}
		try {
			IType type = getType(uri, position, monitor);
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
		if (typeElement instanceof IType) {
			return (IType)typeElement;
		} else if (typeElement instanceof IMethod) {
			return ((IMethod)typeElement).getDeclaringType();
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
			if (unit instanceof IOrdinaryClassFile) {
				element = ((IOrdinaryClassFile) unit).getType();
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
		item.setName(JDTUtils.getName(type));
		item.setKind(DocumentSymbolHandler.mapKind(type));
		IPackageFragment packageFragment = type.getPackageFragment();
		if (packageFragment != null) {
			item.setDetail(packageFragment.getElementName());
		}
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
		ITypeHierarchy typeHierarchy = (direction == TypeHierarchyDirection.Parents) ? type.newSupertypeHierarchy(monitor) : type.newTypeHierarchy(monitor);
		if (direction == TypeHierarchyDirection.Children || direction == TypeHierarchyDirection.Both) {
			List<TypeHierarchyItem> childrenItems = new ArrayList<TypeHierarchyItem>();
			IType[] children = typeHierarchy.getSubtypes(type);
			Set<String> fullyQualifiedNameSet = new HashSet<>();
			for (IType childType : children) {
				if (!fullyQualifiedNameSet.add(childType.getFullyQualifiedName())) {
					continue;
				}
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
			List<TypeHierarchyItem> parentsItems = new ArrayList<TypeHierarchyItem>();
			IType[] parents = typeHierarchy.getSupertypes(type);
			Set<String> fullyQualifiedNameSet = new HashSet<>();
			for (IType parentType : parents) {
				if (!fullyQualifiedNameSet.add(parentType.getFullyQualifiedName())) {
					continue;
				}
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
