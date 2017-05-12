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
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.ClassFileBytesDisassembler;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class ClassfileContentHandler {

	public static final String MISSING_SOURCES_HEADER = " // Failed to get sources. Instead, stub sources have been generated.\n" +
			" // Implementation of methods is unavailable.\n";

	private static final String LF = "\n";

	public CompletableFuture<String> contents(TextDocumentIdentifier param) {
		return CompletableFutures.computeAsync(cm->{
			String source = null;
			try {
				IClassFile cf  = JDTUtils.resolveClassFile(param.getUri());
				if (cf != null) {
					IBuffer buffer = cf.getBuffer();
					if (buffer != null){
						cm.checkCanceled();
						JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
						source = buffer.getContents();
					}
					if (source == null) {
						source = disassemble(cf);
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Exception getting java element ", e);
			}
			if (source == null) {
				source = "";//need to return non null value
			}
			return source;
		});
	}

	private String disassemble(IClassFile classFile) {
		ClassFileBytesDisassembler disassembler= ToolFactory.createDefaultClassFileBytesDisassembler();
		String disassembledByteCode = null;
		try {
			disassembledByteCode = disassembler.disassemble(classFile.getBytes(), LF, ClassFileBytesDisassembler.WORKING_COPY);
			disassembledByteCode = MISSING_SOURCES_HEADER + LF + disassembledByteCode;
		} catch (Exception e) {
			JavaLanguageServerPlugin.logError("Unable to disassemble "+ classFile.getHandleIdentifier() );
		}
		return disassembledByteCode;
	}

}