/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;
import static org.eclipse.jdt.core.IJavaElement.FIELD;
import static org.eclipse.jdt.core.IJavaElement.METHOD;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_DECLARATION;
import static org.eclipse.jdt.core.IJavaElement.TYPE;
import static org.eclipse.jdt.ls.core.internal.JDTUtils.LocationType.FULL_RANGE;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ALL_DEFAULT;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.M_APP_RETURNTYPE;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ROOT_VARIABLE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.xtext.xbase.lib.Exceptions;

public class DocumentSymbolHandler {
	private static Range DEFAULT_RANGE = new Range(new Position(0, 0), new Position(0, 0));

	private PreferenceManager preferenceManager;

	public DocumentSymbolHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<Either<SymbolInformation, DocumentSymbol>> documentSymbol(DocumentSymbolParams params, IProgressMonitor monitor) {

		ITypeRoot unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (unit == null || !unit.exists()) {
			return Collections.emptyList();
		}

		if (preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported()) {
			List<DocumentSymbol> symbols = this.getHierarchicalOutline(unit, monitor);
			return symbols.stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).collect(toList());
		} else {
			SymbolInformation[] elements = this.getOutline(unit, monitor);
			return Arrays.asList(elements).stream().map(Either::<SymbolInformation, DocumentSymbol>forLeft).collect(toList());
		}
	}

	private SymbolInformation[] getOutline(ITypeRoot unit, IProgressMonitor monitor) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<SymbolInformation> symbols = new ArrayList<>(elements.length);
			collectChildren(unit, elements, symbols, monitor);
			return symbols.toArray(new SymbolInformation[symbols.size()]);
		} catch (JavaModelException e) {
			if (!unit.exists()) {
				JavaLanguageServerPlugin.logError("Problem getting outline for " + unit.getElementName() + ": File not found.");
			} else {
				JavaLanguageServerPlugin.logException("Problem getting outline for " + unit.getElementName(), e);
			}
		}
		return new SymbolInformation[0];
	}

	private void collectChildren(ITypeRoot unit, IJavaElement[] elements, ArrayList<SymbolInformation> symbols,
			IProgressMonitor monitor)
			throws JavaModelException {
		for (IJavaElement element : elements) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			if (element instanceof IParent parent) {
				collectChildren(unit, filter(parent.getChildren()), symbols, monitor);
			}
			int type = element.getElementType();
			if (type != IJavaElement.TYPE && type != IJavaElement.FIELD && type != IJavaElement.METHOD) {
				continue;
			}

			Location location = JDTUtils.toLocation(element);
			if (location != null) {
				SymbolInformation si = new SymbolInformation();
				String name = JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_DEFAULT);
				si.setName(name == null ? element.getElementName() : name);
				si.setKind(mapKind(element));
				if (JDTUtils.isDeprecated(element)) {
					if (preferenceManager.getClientPreferences().isSymbolTagSupported()) {
						si.setTags(List.of(SymbolTag.Deprecated));
					}
					else {
						si.setDeprecated(true);
					}
				}
				if (element.getParent() != null) {
					si.setContainerName(element.getParent().getElementName());
				}
				location.setUri(ResourceUtils.toClientUri(location.getUri()));
				si.setLocation(location);
				if (!symbols.contains(si)) {
					symbols.add(si);
				}
			}
		}
	}

	private List<DocumentSymbol> getHierarchicalOutline(ITypeRoot unit, IProgressMonitor monitor) {
		try {
			return Stream.of(filter(unit.getChildren())).map(child -> toDocumentSymbol(child, monitor)).filter(Objects::nonNull).collect(Collectors.toList());
		} catch (OperationCanceledException e) {
			logInfo("User abort while collecting the document symbols.");
		} catch (JavaModelException e) {
			if (!unit.exists()) {
				JavaLanguageServerPlugin.logError("Problem getting outline for " + unit.getElementName() + ": File not found.");
			} else {
				JavaLanguageServerPlugin.logException("Problem getting outline for " + unit.getElementName(), e);
			}
		}
		return emptyList();
	}

	private DocumentSymbol toDocumentSymbol(IJavaElement unit, IProgressMonitor monitor) {
		int type = unit.getElementType();
		if (type != TYPE && type != FIELD && type != METHOD && type != PACKAGE_DECLARATION && type != COMPILATION_UNIT) {
			return null;
		}
		if (monitor.isCanceled()) {
			throw new OperationCanceledException("User abort");
		}
		DocumentSymbol symbol = new DocumentSymbol();
		try {
			String name = getName(unit);
			symbol.setName(name);
			symbol.setRange(getRange(unit));
			symbol.setSelectionRange(getSelectionRange(unit));
			symbol.setKind(mapKind(unit));
			if (JDTUtils.isDeprecated(unit)) {
				if (preferenceManager.getClientPreferences().isSymbolTagSupported()) {
					symbol.setTags(List.of(SymbolTag.Deprecated));
				}
				else {
					symbol.setDeprecated(true);
				}
			}
			symbol.setDetail(getDetail(unit, name));
			if (unit instanceof IParent parent) {
				//@formatter:off
				IJavaElement[] children = filter(parent.getChildren());
				symbol.setChildren(Stream.of(children)
						.map(child -> toDocumentSymbol(child, monitor))
						.filter(Objects::nonNull)
						.collect(Collectors.toList()));
				//@formatter:off
			}
		} catch (JavaModelException e) {
			Exceptions.sneakyThrow(e);
		}
		return symbol;
	}

	private String getName(IJavaElement element) {
		String name = JavaElementLabels.getElementLabel(element, ALL_DEFAULT);
		return name == null ? element.getElementName() : name;
	}

	private Range getRange(IJavaElement element) throws JavaModelException {
		Location location = JDTUtils.toLocation(element, FULL_RANGE);
		return location == null ? DEFAULT_RANGE : location.getRange();
	}

	private Range getSelectionRange(IJavaElement element) throws JavaModelException {
		Location location = JDTUtils.toLocation(element);
		return location == null ? DEFAULT_RANGE : location.getRange();
	}

	private String getDetail(IJavaElement element, String name) {
		String nameWithDetails = JavaElementLabels.getElementLabel(element, ALL_DEFAULT | M_APP_RETURNTYPE | ROOT_VARIABLE);
		if (nameWithDetails != null && nameWithDetails.startsWith(name)) {
			return nameWithDetails.substring(name.length());
		}
		return "";
	}

	private IJavaElement[] filter(IJavaElement[] elements) {
		return Stream.of(elements)
				.filter(e -> (!isInitializer(e) && !isSyntheticElement(e)))
				.toArray(IJavaElement[]::new);
	}

	private boolean isInitializer(IJavaElement element) {
		if (element.getElementType() == IJavaElement.METHOD) {
			String name = element.getElementName();
			if ((name != null && name.indexOf('<') >= 0)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSyntheticElement(IJavaElement element) {
		if (!(element instanceof IMember)) {
			return false;
		}
		IMember member= (IMember)element;
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
					IType type = (IType)element;
					if (type.isInterface()) {
						return SymbolKind.Interface;
					}
					else if (type.isEnum()) {
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
				if (element instanceof IMethod method && method.isConstructor()) {
					return SymbolKind.Constructor;
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

}
