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

import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class ClassfileContentHandler {

	public CompletableFuture<String> contents(TextDocumentIdentifier param) {
		return CompletableFutures.computeAsync(cm->{
			try {
				IClassFile cf  = JDTUtils.resolveClassFile(param.getUri());
				if (cf != null) {
					IBuffer buffer = cf.getBuffer();
					if (buffer != null){
						cm.checkCanceled();
						JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
						return buffer.getContents();
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Exception getting java element ", e);
			}
			return null;
		});
	}

}