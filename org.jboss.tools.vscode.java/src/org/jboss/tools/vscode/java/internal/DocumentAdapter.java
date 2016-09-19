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
package org.jboss.tools.vscode.java.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.BufferChangedEvent;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IBufferChangedListener;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ISynchronizable;

public class DocumentAdapter implements IBuffer, IDocumentListener {

	private static class NullBuffer implements IBuffer {
		public void addBufferChangedListener(IBufferChangedListener listener) {}
		public void append(char[] text) {}
		public void append(String text) {}
		public void close() {}
		public char getChar(int position) { return 0; }
		public char[] getCharacters() { return null; }
		public String getContents() { return null; }
		public int getLength() { return 0; }
		public IOpenable getOwner() { return null; }
		public String getText(int offset, int length) { return null; }
		public IResource getUnderlyingResource() { return null; }
		public boolean hasUnsavedChanges() { return false; }
		public boolean isClosed() { return false; }
		public boolean isReadOnly() { return true; }
		public void removeBufferChangedListener(IBufferChangedListener listener) {}
		public void replace(int position, int length, char[] text) {}
		public void replace(int position, int length, String text) {}
		public void save(IProgressMonitor progress, boolean force) throws JavaModelException {}
		public void setContents(char[] contents) {}
		public void setContents(String contents) {}
	}
	
	public static final IBuffer Null = new NullBuffer();
	
	private Object lock = new Object();	
	
	private IOpenable fOwner;
	private IFile fFile;
	private boolean fIsClosed;
	
	private List<IBufferChangedListener> fBufferListeners;
	
	private ITextFileBuffer fTextFileBuffer;
	private IDocument fDocument;
	
	
	public DocumentAdapter(IOpenable owner, IFile file) {
		fOwner = owner;
		fFile = file;
		fBufferListeners = new ArrayList<IBufferChangedListener>(3);
		fIsClosed = false;
		
		ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
		try {
			manager.connect(file.getFullPath(), LocationKind.IFILE, null);
			fTextFileBuffer= manager.getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
		} catch (CoreException e) {
		}
	}

	public IDocument getDocument() {
		return fDocument;
	}

	@Override
	public void addBufferChangedListener(IBufferChangedListener listener) {
		synchronized (lock) {			
			if (!fBufferListeners.contains(listener)) {
				fBufferListeners.add(listener);			
			}
		}
	}

	@Override
	public synchronized void removeBufferChangedListener(IBufferChangedListener listener) {
		synchronized (lock) {
			fBufferListeners.remove(listener);			
		}
	}

	@Override
	public void append(char[] text) {
		append(new String(text));
	}

	@Override
	public void append(String text) {
		try {
			fDocument.replace(fDocument.getLength(), 0, text);			
		} catch (BadLocationException e) {
			new IndexOutOfBoundsException();
		}
	}

	@Override
	public void close() {
		synchronized (lock) {			
			if (fIsClosed)
				return;
			
			fIsClosed= true;
			fDocument.removeDocumentListener(this);
			
			if (fTextFileBuffer != null) {
				try {
					ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
					manager.disconnect(fFile.getFullPath(), LocationKind.NORMALIZE, null);
				} catch (CoreException x) {
					// ignore
				}
				fTextFileBuffer= null;
			}
			
			fireBufferChanged(new BufferChangedEvent(this, 0, 0, null));
			fBufferListeners.clear();
			fDocument = null;
		}
	}

	@Override
	public char getChar(int position) {
		try {
			return fDocument.getChar(position);
		} catch (BadLocationException x) {
			throw new IndexOutOfBoundsException();
		}
	}

	@Override
	public char[] getCharacters() {
		String content = getContents();
		return content != null ? content.toCharArray() : null;
	}

	@Override
	public String getContents() {
		return fDocument != null ? fDocument.get() : null;
	}

	@Override
	public int getLength() {
		return fDocument.getLength();
	}

	@Override
	public IOpenable getOwner() {
		return fOwner;
	}

	@Override
	public String getText(int offset, int length) throws IndexOutOfBoundsException {
		try {
			return fDocument.get(offset, length);
		} catch (BadLocationException x) {
			throw new IndexOutOfBoundsException();
		}
	}

	@Override
	public IResource getUnderlyingResource() {
		return fFile;
	}

	@Override
	public boolean hasUnsavedChanges() {
		return fTextFileBuffer != null ? fTextFileBuffer.isDirty() : false;
	}

	@Override
	public boolean isClosed() {
		return fIsClosed;
	}

	@Override
	public boolean isReadOnly() {
		if (fTextFileBuffer != null) {
			return fTextFileBuffer.isCommitable();
		}
		
		ResourceAttributes attributes = fFile.getResourceAttributes();
		return attributes != null ? attributes.isReadOnly() : false;
	}

	@Override
	public void replace(int position, int length, char[] text) {
		replace(position, length, new String(text));
	}

	@Override
	public void replace(int position, int length, String text) {
		try {
			fDocument.replace(position, length, text);			
		} catch (BadLocationException e) {
			throw new IndexOutOfBoundsException();
		}
	}

	@Override
	public void save(IProgressMonitor progress, boolean force) throws JavaModelException {
		try {
			if (fTextFileBuffer != null) {
				fTextFileBuffer.commit(progress, force);				
			}
		} catch (CoreException e) {
			throw new JavaModelException(e);
		}
	}

	@Override
	public void setContents(char[] contents) {
		setContents(new String(contents));
	}

	@Override
	public void setContents(String contents) {
		synchronized (lock) {			
			if (fDocument == null) {
				if (fTextFileBuffer != null) {
					fDocument = fTextFileBuffer.getDocument(); 
				} else {
					ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
					fDocument =  manager.createEmptyDocument(fFile.getFullPath(), LocationKind.IFILE);
				}
				fDocument.addDocumentListener(this);
				((ISynchronizable)fDocument).setLockObject(lock);
			}
		}
		fDocument.set(contents);
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// no about to be changed on IBuffer
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		fireBufferChanged(new BufferChangedEvent(this, event.getOffset(), event.getLength(), event.getText()));
	}
	
	private void fireBufferChanged(BufferChangedEvent event) {
		IBufferChangedListener[] listeners = null;	
		synchronized (lock) {
			listeners = fBufferListeners.toArray(new IBufferChangedListener[fBufferListeners.size()]);			
		}
		for (IBufferChangedListener listener : listeners) {
			listener.bufferChanged(event);
		}
	}
}