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
package org.eclipse.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.text.edits.CopyTargetEdit;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MoveSourceEdit;
import org.eclipse.text.edits.MoveTargetEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditVisitor;

/**
 * Converts an {@link org.eclipse.text.edits.TextEdit} to {@link org.eclipse.lsp4j.TextEdit}
 *
 * @author Gorkem Ercan
 *
 */
public class TextEditConverter extends TextEditVisitor{

	private final TextEdit source;
	protected ICompilationUnit compilationUnit;
	protected List<org.eclipse.lsp4j.TextEdit> converted;

	public TextEditConverter(ICompilationUnit unit, TextEdit edit) {
		this.source = edit;
		this.converted = new ArrayList<>();
		if(unit == null ){
			throw new IllegalArgumentException("Compilation unit can not be null");
		}
		this.compilationUnit = unit;
	}

	public List<org.eclipse.lsp4j.TextEdit> convert(){
		if(this.source != null){
			this.source.accept(this);
		}
		return converted;
	}

	public TextDocumentEdit convertToTextDocumentEdit(int version) {
		String uri = JDTUtils.toURI(compilationUnit);
		VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(version);
		identifier.setUri(uri);
		return new TextDocumentEdit(identifier, this.convert());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.InsertEdit)
	 */
	@Override
	public boolean visit(InsertEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
			te.setNewText(edit.getText());
			te.setRange(JDTUtils.toRange(compilationUnit,edit.getOffset(),edit.getLength()));
			converted.add(te);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.DeleteEdit)
	 */
	@Override
	public boolean visit(DeleteEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
			te.setNewText("");
			te.setRange(JDTUtils.toRange(compilationUnit,edit.getOffset(),edit.getLength()));
			converted.add(te);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.ReplaceEdit)
	 */
	@Override
	public boolean visit(ReplaceEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
			te.setNewText(edit.getText());
			te.setRange(JDTUtils.toRange(compilationUnit,edit.getOffset(),edit.getLength()));
			converted.add(te);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.
	 * CopyTargetEdit)
	 */
	@Override
	public boolean visit(CopyTargetEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));

			Document doc = new Document(compilationUnit.getSource());
			edit.apply(doc, TextEdit.UPDATE_REGIONS);
			String content = doc.get(edit.getSourceEdit().getOffset(), edit.getSourceEdit().getLength());
			te.setNewText(content);
			converted.add(te);
		} catch (MalformedTreeException | BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return false; // do not visit children
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.MoveSourceEdit)
	 */
	@Override
	public boolean visit(MoveSourceEdit edit) {
		try {
			// If MoveSourcedEdit & MoveTargetEdit are the same level, should delete the original contenxt.
			// See issue#https://github.com/redhat-developer/vscode-java/issues/253
			if (edit.getParent() != null && edit.getTargetEdit() != null && edit.getParent().equals(edit.getTargetEdit().getParent())) {
				org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
				te.setNewText("");
				te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
				converted.add(te);
				return false;
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.
	 * MoveTargetEdit)
	 */
	@Override
	public boolean visit(MoveTargetEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));

			Document doc = new Document(compilationUnit.getSource());
			edit.apply(doc, TextEdit.UPDATE_REGIONS);
			String content = doc.get(edit.getSourceEdit().getOffset(), edit.getSourceEdit().getLength());
			te.setNewText(content);
			converted.add(te);
		} catch (MalformedTreeException | BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return false; // do not visit children
	}
}
