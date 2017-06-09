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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class ClassfileContentHandler {

	private static final String EMPTY_CONTENT = "";

	public String contents(TextDocumentIdentifier param, IProgressMonitor monitor) {
		String source = null;
		try {
			IClassFile cf = JDTUtils.resolveClassFile(param.getUri());
			if (cf != null) {
				IBuffer buffer = cf.getBuffer();
				if (buffer != null) {
					if (monitor.isCanceled()) {
						return EMPTY_CONTENT;
					}
					source = buffer.getContents();
					JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
				}
				if (source == null) {
					source = JDTUtils.disassemble(cf);
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting java element ", e);
		}
		if (source == null) {
			source = EMPTY_CONTENT;// need to return non null value
		}
		return source;
	}

}