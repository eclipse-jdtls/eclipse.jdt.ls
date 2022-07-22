/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Gayan Perera (gayanper@gmail.com) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType.FULL_RANGE;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ALL_DEFAULT;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.M_APP_RETURNTYPE;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ROOT_VARIABLE;

import java.util.stream.Stream;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

public final class SymbolUtils {
	private static final Range DEFAULT_RANGE = new Range(new Position(0, 0), new Position(0, 0));

	private SymbolUtils() {
	}

	static IJavaElement[] filter(IJavaElement[] elements) {
		return Stream.of(elements).filter(e -> (!SymbolUtils.isInitializer(e) && !SymbolUtils.isSyntheticElement(e))).toArray(IJavaElement[]::new);
	}

	static boolean isInitializer(IJavaElement element) {
		if (element.getElementType() == IJavaElement.METHOD) {
			String name = element.getElementName();
			if ((name != null && name.indexOf('<') >= 0)) {
				return true;
			}
		}
		return false;
	}

	static boolean isSyntheticElement(IJavaElement element) {
		if (!(element instanceof IMember)) {
			return false;
		}
		IMember member = (IMember) element;
		if (!(member.isBinary())) {
			return false;
		}
		try {
			return Flags.isSynthetic(member.getFlags());
		} catch (JavaModelException e) {
			return false;
		}
	}

	public static SymbolKind mapKind(IJavaElement element) {
		switch (element.getElementType()) {
			case IJavaElement.TYPE:
				try {
					IType type = (IType) element;
					if (type.isInterface()) {
						return SymbolKind.Interface;
					} else if (type.isEnum()) {
						return SymbolKind.Enum;
					}
				} catch (JavaModelException ignore) {
				}
				return SymbolKind.Class;
			case IJavaElement.ANNOTATION:
				return SymbolKind.Property; // TODO: find a better mapping
			case IJavaElement.CLASS_FILE:
			case IJavaElement.COMPILATION_UNIT:
				return SymbolKind.File;
			case IJavaElement.FIELD:
				IField field = (IField) element;
				try {
					if (field.isEnumConstant()) {
						return SymbolKind.EnumMember;
					}
					int flags = field.getFlags();
					if (Flags.isStatic(flags) && Flags.isFinal(flags)) {
						return SymbolKind.Constant;
					}
				} catch (JavaModelException ignore) {
				}
				return SymbolKind.Field;
			case IJavaElement.IMPORT_CONTAINER:
			case IJavaElement.IMPORT_DECLARATION:
				//should we return SymbolKind.Namespace?
			case IJavaElement.JAVA_MODULE:
				return SymbolKind.Module;
			case IJavaElement.INITIALIZER:
				return SymbolKind.Constructor;
			case IJavaElement.LOCAL_VARIABLE:
				return SymbolKind.Variable;
			case IJavaElement.TYPE_PARAMETER:
				return SymbolKind.TypeParameter;
			case IJavaElement.METHOD:
				try {
					// TODO handle `IInitializer`. What should be the `SymbolKind`?
					if (element instanceof IMethod) {
						if (((IMethod) element).isConstructor()) {
							return SymbolKind.Constructor;
						}
					}
					return SymbolKind.Method;
				} catch (JavaModelException e) {
					return SymbolKind.Method;
				}
			case IJavaElement.PACKAGE_DECLARATION:
				return SymbolKind.Package;
		}
		return SymbolKind.String;
	}

	static String getName(IJavaElement element) {
		String name = JavaElementLabels.getElementLabel(element, ALL_DEFAULT);
		return name == null ? element.getElementName() : name;
	}

	static Range getRange(IJavaElement element) throws JavaModelException {
		Location location = JDTUtils.toLocation(element, FULL_RANGE);
		return location == null ? DEFAULT_RANGE : location.getRange();
	}

	static Range getSelectionRange(IJavaElement element) throws JavaModelException {
		Location location = JDTUtils.toLocation(element);
		return location == null ? DEFAULT_RANGE : location.getRange();
	}

	static String getDetail(IJavaElement element, String name) {
		return constructDetail(element, name, ALL_DEFAULT | M_APP_RETURNTYPE | ROOT_VARIABLE);
	}

	static String getDetail(IJavaElement element, String name, long additionalFlags) {
		return constructDetail(element, name, ALL_DEFAULT | M_APP_RETURNTYPE | ROOT_VARIABLE | additionalFlags);
	}

	private static String constructDetail(IJavaElement element, String name, long flags) {
		String nameWithDetails = JavaElementLabels.getElementLabel(element, flags);
		if (nameWithDetails != null && nameWithDetails.startsWith(name)) {
			return nameWithDetails.substring(name.length());
		}
		return "";
	}

}
