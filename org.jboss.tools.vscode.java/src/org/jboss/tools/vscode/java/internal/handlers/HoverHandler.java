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

import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jboss.tools.vscode.java.internal.HoverInfoProvider;
import org.jboss.tools.vscode.java.internal.JDTUtils;

public class HoverHandler{

	public CompletableFuture<Hover> hover(TextDocumentPositionParams position){
		return CompletableFutures.computeAsync(cancelToken->{
			ITypeRoot unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());

			String hover = null;
			if(unit !=null){
				cancelToken.checkCanceled();
				hover = computeHover(unit ,position.getPosition().getLine(),
						position.getPosition().getCharacter());
			}
			Hover $ = new Hover();
			if (hover != null && !hover.isEmpty()) {
				$.setContents(hover);
			}
			return $;
		});
	}

	private String computeHover(ITypeRoot unit, int line, int column) {
		HoverInfoProvider provider = new HoverInfoProvider(unit);
		return provider.computeHover(line,column);
	}

}
