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

import org.eclipse.jdt.core.ITypeRoot;
import org.jboss.tools.langs.Hover;
import org.jboss.tools.langs.TextDocumentPositionParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.HoverInfoProvider;
import org.jboss.tools.vscode.java.internal.JDTUtils;

public class HoverHandler implements RequestHandler<TextDocumentPositionParams, Hover>{

	public HoverHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_HOVER.getMethod().equals(request);
	}

	@Override
	public Hover handle(TextDocumentPositionParams param, CancelMonitor cm) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri());

		String hover = null;
		if(!cm.cancelled() && unit !=null){
			hover = computeHover(unit ,param.getPosition().getLine().intValue(),
					param.getPosition().getCharacter().intValue());
		}
		Hover $ = new Hover();
		if (hover != null && hover.length() > 0) {
			return $.withContents(hover);
		}
		return $.withContents("");
	}


	public String computeHover(ITypeRoot unit, int line, int column) {
		HoverInfoProvider provider = new HoverInfoProvider(unit);
		return provider.computeHover(line,column);
	}

}
