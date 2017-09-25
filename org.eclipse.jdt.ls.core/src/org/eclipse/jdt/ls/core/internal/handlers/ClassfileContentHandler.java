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
import org.eclipse.jdt.ls.core.internal.managers.DecompilerManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class ClassfileContentHandler {

	private static final String EMPTY_CONTENT = "";

	private final PreferenceManager preferenceManager;

	public ClassfileContentHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public String contents(TextDocumentIdentifier param, IProgressMonitor monitor) {
		String source = null;
		IClassFile cf = JDTUtils.resolveClassFile(param.getUri());
		if (cf != null) {
			source = contents(cf, monitor);
		}
		if (source == null) {
			source = EMPTY_CONTENT;// need to return non null value
		}
		return source;
	}

	public String contents(IClassFile cf, IProgressMonitor monitor) {
		String source = null;
		try {
			IBuffer buffer = cf.getBuffer();
			if (buffer != null) {
				if (monitor.isCanceled()) {
					return EMPTY_CONTENT;
				}
				source = buffer.getContents();
				JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
			}
			if (source == null) {
				source = decompile(cf, monitor);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting java element ", e);
		}
		return source;
	}

	private String decompile(IClassFile cf, IProgressMonitor monitor) {
		DecompilerManager decompiler = new DecompilerManager(preferenceManager);
		try {
			return decompiler.decompile(cf, monitor);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logError("Unable to decompile " + cf.getHandleIdentifier());
			return null;
		}
	}

}