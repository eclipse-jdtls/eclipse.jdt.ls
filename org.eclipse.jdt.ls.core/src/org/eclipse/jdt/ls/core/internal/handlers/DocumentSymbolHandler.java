/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;

public class DocumentSymbolHandler {

	private SymbolInformation[] getOutline(ITypeRoot unit) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<SymbolInformation> symbols = new ArrayList<>(elements.length);
			collectChildren(unit, elements, symbols);
			return symbols.toArray(new SymbolInformation[symbols.size()]);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting outline for" +  unit.getElementName(), e);
		}
		return new SymbolInformation[0];
	}

	private void collectChildren(ITypeRoot unit, IJavaElement[] elements, ArrayList<SymbolInformation> symbols)
			throws JavaModelException {
		for(IJavaElement element : elements ){
			if(element.getElementType() == IJavaElement.TYPE){
				collectChildren(unit, ((IType)element).getChildren(),symbols);
			}
			if(element.getElementType() != IJavaElement.FIELD &&
					element.getElementType() != IJavaElement.METHOD
					){
				continue;
			}

			SymbolInformation si = new SymbolInformation();
			si.setName(element.getElementName());
			si.setKind(mapKind(element));
			if(element.getParent() != null )
				si.setContainerName(element.getParent().getElementName());
			si.setLocation(JDTUtils.toLocation(element));
			symbols.add(si);
		}
	}

	CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params){
		return CompletableFuture.supplyAsync(()->{
			ITypeRoot unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
			if(unit == null )
				return Collections.emptyList();
			SymbolInformation[] elements  = this.getOutline(unit);
			return Arrays.asList(elements);
		});
	}

	public static SymbolKind mapKind(IJavaElement element) {
		switch (element.getElementType()) {
		case IJavaElement.ANNOTATION:
			return SymbolKind.Property; // TODO: find a better mapping
		case IJavaElement.CLASS_FILE:
		case IJavaElement.COMPILATION_UNIT:
			return SymbolKind.File;
		case IJavaElement.FIELD:
			return SymbolKind.Field;
		case IJavaElement.IMPORT_CONTAINER:
		case IJavaElement.IMPORT_DECLARATION:
			return SymbolKind.Module;
		case IJavaElement.INITIALIZER:
			return SymbolKind.Constructor;
		case IJavaElement.LOCAL_VARIABLE:
		case IJavaElement.TYPE_PARAMETER:
			return SymbolKind.Variable;
		case IJavaElement.METHOD:
			return SymbolKind.Function;
		case IJavaElement.PACKAGE_DECLARATION:
			return SymbolKind.Package;
		case IJavaElement.TYPE:
			try {
				return ( ((IType)element).isInterface() ? SymbolKind.Interface : SymbolKind.Class);
			} catch (JavaModelException e) {
				return SymbolKind.Class;
			}
		}
		return SymbolKind.String;
	}

}