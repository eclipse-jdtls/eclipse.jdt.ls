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

import static java.util.Collections.emptyList;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;
import static org.eclipse.jdt.core.IJavaElement.FIELD;
import static org.eclipse.jdt.core.IJavaElement.METHOD;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_DECLARATION;
import static org.eclipse.jdt.core.IJavaElement.TYPE;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.filter;
import static org.eclipse.jdt.ls.core.internal.handlers.SymbolUtils.getName;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.F_POST_QUALIFIED;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.M_POST_QUALIFIED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Exceptions;
import org.eclipse.xtext.xbase.lib.Pure;

public class ExtendedDocumentSymbolHandler {
	private PreferenceManager preferenceManager;

	public ExtendedDocumentSymbolHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<ExtendedDocumentSymbol> handle(DocumentSymbolParams params, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (unit == null || !unit.exists()) {
			return Collections.emptyList();
		}
		return getHierarchicalOutline(unit, monitor);
	}

	private List<ExtendedDocumentSymbol> getHierarchicalOutline(ITypeRoot unit, IProgressMonitor monitor) {
		try {
			return Stream.of(getInheritedChildren(unit, monitor)).map(child -> toDocumentSymbol(child, monitor)).filter(Objects::nonNull).collect(Collectors.toList());
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

	private static IJavaElement[] getInheritedChildren(ITypeRoot unit, IProgressMonitor monitor) throws JavaModelException {
		IType type = unit.findPrimaryType();
		List<IJavaElement> children = new ArrayList<>();
		if (type != null) {
			children.addAll(Arrays.asList(filter(type.getChildren())));
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			ITypeHierarchy supertypeHierarchy = type.newSupertypeHierarchy(monitor);
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			IType[] superClasses = supertypeHierarchy.getAllSuperclasses(type);
			for (IType superType : superClasses) {
				children.addAll(Arrays.asList(filter(superType.getChildren())));
			}
		}
		return children.toArray(IJavaElement[]::new);
	}

	private ExtendedDocumentSymbol toDocumentSymbol(IJavaElement element, IProgressMonitor monitor) {
		int type = element.getElementType();
		if (type != TYPE && type != FIELD && type != METHOD && type != PACKAGE_DECLARATION && type != COMPILATION_UNIT) {
			return null;
		}
		if (monitor.isCanceled()) {
			throw new OperationCanceledException("User abort");
		}
		ExtendedDocumentSymbol symbol = new ExtendedDocumentSymbol();
		try {
			String name = getName(element);
			symbol.setName(name);
			symbol.setRange(SymbolUtils.getRange(element));
			symbol.setSelectionRange(SymbolUtils.getSelectionRange(element));
			symbol.setKind(SymbolUtils.mapKind(element));
			if (JDTUtils.isDeprecated(element)) {
				if (preferenceManager.getClientPreferences().isSymbolTagSupported()) {
					symbol.setTags(List.of(SymbolTag.Deprecated));
				} else {
					symbol.setDeprecated(true);
				}
			}
			symbol.setDetail(SymbolUtils.getDetail(element, name, M_POST_QUALIFIED | F_POST_QUALIFIED));
			symbol.setUri(JDTUtils.toLocation(element).getUri());
			if (element instanceof IType) {
				//@formatter:off
				IJavaElement[] children = filter(((IParent) element).getChildren());
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

	public static class ExtendedDocumentSymbol extends DocumentSymbol {
		/**
		 * The uri of the document of this symbol.
		 */
		@NonNull
		private String uri;

		/**
		 * The uri of the document of this symbol.
		 */
		public void setUri(@NonNull String uri) {
			this.uri = uri;
		}

		/**
		 * The uri of the document of this symbol.
		 */
		@NonNull
		@Pure
		public String getUri() {
			return this.uri;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + Objects.hash(uri);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			ExtendedDocumentSymbol other = (ExtendedDocumentSymbol) obj;
			return Objects.equals(uri, other.uri);
		}
	}

}
