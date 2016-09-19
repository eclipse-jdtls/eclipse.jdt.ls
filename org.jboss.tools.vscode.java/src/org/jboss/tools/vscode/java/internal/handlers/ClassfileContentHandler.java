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

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.TextDocumentIdentifier;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class ClassfileContentHandler implements RequestHandler<TextDocumentIdentifier, String> {

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.CLASSFILECONTENTS.getMethod().equals(request);
	}

	@Override
	public String handle(TextDocumentIdentifier param) {
		try {
			IClassFile cf  = JDTUtils.resolveClassFile(param.getUri());
			if (cf != null) {
				IBuffer buffer = cf.getBuffer();
				if (buffer != null) {
					JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
					return buffer.getContents();
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting java element ", e);
		}
		return null;
	}

}