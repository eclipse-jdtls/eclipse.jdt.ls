/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.function.Function;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

final public class JsonRpcHelpers {

	private JsonRpcHelpers(){
		//avoid instantiation
	}

	/**
	 * Convert line, column to a document offset.
	 *
	 * @param openable
	 * @param line
	 * @param column
	 * @return
	 */
	public static int toOffset(IOpenable openable, int line, int column) {
		if (openable != null) {
			try {
				return convert(openable, (IDocument document) -> toOffset(document, line, column));
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}

		return -1;
	}

	/**
	 * Convert line, column to a document offset.
	 *
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
		if (document != null) {
			try {
				return document.getLineOffset(line) + column;
			} catch (BadLocationException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
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
	 * Converts an offset to line number and column in an openable. If the
	 * {@code openable} argument is {@link IOpenable#isOpen() <b>not</b> open}, this
	 * method tries to
	 * {@link IOpenable#open(org.eclipse.core.runtime.IProgressMonitor) open} it.
	 *
	 * @param buffer
	 * @param line
	 * @param column
	 * @return
	 */
	public static int[] toLine(IOpenable openable, int offset) {
		try {
			return convert(openable, (IDocument document) -> toLine(document, offset));
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return null;
	}

	private static <T> T convert(IOpenable openable, Function<IDocument, T> consumer) throws JavaModelException {
		Assert.isNotNull(openable, "openable");
		boolean mustClose = false;
		try {
			if (!openable.isOpen()) {
				openable.open(new NullProgressMonitor());
				mustClose = openable.isOpen();
			}
			IBuffer buffer = openable.getBuffer();
			return consumer.apply(toDocument(buffer));
		} finally {
			if (mustClose) {
				try {
					openable.close();
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException("Error when closing openable: " + openable, e);
				}
			}
		}
	}

	/**
	 * Convert the document offset to line number and column.
	 *
	 * @param document
	 * @param line
	 * @return
	 */
	public static int[] toLine(IDocument document, int offset) {
		if (document != null) {
			try {
				int line = document.getLineOfOffset(offset);
				int column = offset - document.getLineOffset(line);
				return new int[] { line, column };
			} catch (BadLocationException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
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
		if (buffer instanceof IDocument doc) {
			return doc;
		} else if (buffer instanceof org.eclipse.jdt.ls.core.internal.DocumentAdapter adapter) {
			IDocument document = adapter.getDocument();
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
