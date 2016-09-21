/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.DocumentSymbolParams;
import org.jboss.tools.langs.SymbolInformation;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class DocumentSymbolHandler implements RequestHandler<DocumentSymbolParams, List<SymbolInformation>>{


	public DocumentSymbolHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_SYMBOL.getMethod().equals(request);
	}

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
			si.setKind(new Double(mapKind(element)));
			if(element.getParent() != null )
				si.setContainerName(element.getParent().getElementName());
			si.setLocation(JDTUtils.toLocation(element));
			symbols.add(si);
		}
	}

	@Override
	public List<SymbolInformation> handle(DocumentSymbolParams param, CancelMonitor cm) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri());
		SymbolInformation[] elements  = this.getOutline(unit);
		return Arrays.asList(elements);
	}

	public static int mapKind(IJavaElement element) {
		//		/**
		//		* A symbol kind.
		//		*/
		//		export enum SymbolKind {
		//		  File = 1,
		//		  Module = 2,
		//		  Namespace = 3,
		//		  Package = 4,
		//		  Class = 5,
		//		  Method = 6,
		//		  Property = 7,
		//		  Field = 8,
		//		  Constructor = 9,
		//		  Enum = 10,
		//		  Interface = 11,
		//		  Function = 12,
		//		  Variable = 13,
		//		  Constant = 14,
		//		  String = 15,
		//		  Number = 16,
		//		  Boolean = 17,
		//		  Array = 18,
		//		}
		switch (element.getElementType()) {
		case IJavaElement.ANNOTATION:
			return 7; // TODO: find a better mapping
		case IJavaElement.CLASS_FILE:
		case IJavaElement.COMPILATION_UNIT:
			return 1;
		case IJavaElement.FIELD:
			return 8;
		case IJavaElement.IMPORT_CONTAINER:
		case IJavaElement.IMPORT_DECLARATION:
			return 2;
		case IJavaElement.INITIALIZER:
			return 9;
		case IJavaElement.LOCAL_VARIABLE:
		case IJavaElement.TYPE_PARAMETER:
			return 13;
		case IJavaElement.METHOD:
			return 12;
		case IJavaElement.PACKAGE_DECLARATION:
			return 3;
		case IJavaElement.TYPE:
			try {
				return ( ((IType)element).isInterface() ? 11 : 5);
			} catch (JavaModelException e) {
				return 5; //fallback
			}
		}
		return 15;
	}

}