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

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

final public class JsonRpcHelpers {

	private JsonRpcHelpers(){
		//avoid instantiation
	}

	/**
	 * Convert line, column to a document offset.
	 * @param buffer
	 * @param line
	 * @param column
	 * @return
	 */
	public static int toOffset(IBuffer buffer, int line, int column){
		if (buffer != null) {
			return toOffset(toDocument(buffer), line, column);
		}
		return -1;
	}

	/**
	 * Convert line, column to a document offset.
	 * 
	 * @param document
	 * @param line
	 * @param column
	 * @return
	 */
	public static int toOffset(IDocument document, int line, int column) {
		try {
			return document.getLineOffset(line) + column;
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return -1;
	}

	/**
	 * Convert offset to line number and column.
	 * @param buffer
	 * @param line
	 * @param column
	 * @return
	 */
	public static int[] toLine(IBuffer buffer, int offset){
		return toLine(toDocument(buffer), offset);
	}

	/**
	 * Convert the document offset to line number and column.
	 *
	 * @param document
	 * @param line
	 * @return
	 */
	public static int[] toLine(IDocument document, int offset) {
		try {
			int line = document.getLineOfOffset(offset);
			int column = offset - document.getLineOffset(line);
			return new int[] { line, column };
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Returns an {@link IDocument} for the given buffer.
	 * The implementation tries to avoid copying the buffer unless required.
	 * The returned document may or may not be connected to the buffer.
	 *
	 * @param buffer a buffer
	 * @return a document with the same contents as the buffer or <code>null</code> is the buffer is <code>null</code>
	 */
	public static IDocument toDocument(IBuffer buffer) {
		if (buffer == null) {
			return null;
		}
		if (buffer instanceof IDocument) {
			return (IDocument) buffer;
		} else if (buffer instanceof org.eclipse.jdt.ls.core.internal.DocumentAdapter) {
			IDocument document = ((org.eclipse.jdt.ls.core.internal.DocumentAdapter) buffer).getDocument();
			if (document != null) {
				return document;
			}
		}
		return new org.eclipse.jdt.internal.core.DocumentAdapter(buffer);
	}


	/**
	 * Returns an {@link IDocument} for the given {@link IFile}.
	 *
	 * @param file an {@link IFile}
	 * @return a document with the contents of the file,
	 * or <code>null</code> if the file can not be opened.
	 */
	public static IDocument toDocument(IFile file) {
		if (file != null && file.isAccessible()) {
			IPath path = file.getFullPath();
			ITextFileBufferManager fileBufferManager = FileBuffers.getTextFileBufferManager();
			LocationKind kind = LocationKind.IFILE;
			try {
				fileBufferManager.connect(path, kind, new NullProgressMonitor());
				ITextFileBuffer fileBuffer = fileBufferManager.getTextFileBuffer(path, kind);
				if (fileBuffer != null) {
					return fileBuffer.getDocument();
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to convert "+ file +"  to an IDocument", e);
			} finally {
				try {
					fileBufferManager.disconnect(path, kind, new NullProgressMonitor());
				} catch (CoreException slurp) {
					//Don't care
				}
			}
		}
		return null;
	}
}
