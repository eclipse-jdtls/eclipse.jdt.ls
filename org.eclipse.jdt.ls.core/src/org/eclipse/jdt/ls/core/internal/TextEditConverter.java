/*******************************************************************************
 * Copyright (c) 2016-2023 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Microsoft Corporation - Support AnnotatedTextEdit
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.util.SimpleDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.text.edits.CopySourceEdit;
import org.eclipse.text.edits.CopyTargetEdit;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.ISourceModifier;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MoveSourceEdit;
import org.eclipse.text.edits.MoveTargetEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditVisitor;

/**
 * Converts an {@link org.eclipse.text.edits.TextEdit} to
 * {@link org.eclipse.lsp4j.TextEdit}
 *
 * @author Gorkem Ercan
 *
 */
public class TextEditConverter extends TextEditVisitor {

	private final TextEdit source;
	private boolean isChangeAnnotationSupported;
	private String changeAnnotation;
	protected ICompilationUnit compilationUnit;
	protected List<org.eclipse.lsp4j.TextEdit> converted;

	public TextEditConverter(ICompilationUnit unit, TextEdit edit) {
		this.source = edit;
		this.converted = new ArrayList<>();
		if (unit == null) {
			throw new IllegalArgumentException("Compilation unit can not be null");
		}
		this.compilationUnit = unit;
		this.isChangeAnnotationSupported = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isChangeAnnotationSupport();
	}

	public List<org.eclipse.lsp4j.TextEdit> convert() {
		return this.convert(null);
	}

	public List<org.eclipse.lsp4j.TextEdit> convert(String changeAnnotation) {
		if (this.isChangeAnnotationSupported && changeAnnotation != null) {
			this.changeAnnotation = changeAnnotation;
		}
		if (this.source != null) {
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
			org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
			te.setNewText(edit.getText());
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
			converted.add(te);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.CopySourceEdit)
	 */
	@Override
	public boolean visit(CopySourceEdit edit) {
		try {
			if (edit.getTargetEdit() != null) {
				org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
				te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
				Document doc = new Document(compilationUnit.getSource());
				edit.apply(doc, TextEdit.UPDATE_REGIONS);
				String content = doc.get(edit.getOffset(), edit.getLength());
				if (edit.getSourceModifier() != null) {
					content = applySourceModifier(content, edit.getSourceModifier());
				}
				te.setNewText(content);
				converted.add(te);
			}
			return false;
		} catch (JavaModelException | MalformedTreeException | BadLocationException e) {
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
			org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
			te.setNewText("");
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
			converted.add(te);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.MultiTextEdit)
	 */
	@Override
	public boolean visit(MultiTextEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
			Document doc = new Document(compilationUnit.getSource());
			edit.apply(doc, TextEdit.UPDATE_REGIONS);
			String content = doc.get(edit.getOffset(), edit.getLength());
			te.setNewText(content);
			converted.add(te);
			return false;
		} catch (JavaModelException | MalformedTreeException | BadLocationException e) {
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
			org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
			te.setNewText(edit.getText());
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
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
			if (edit.getSourceEdit() != null) {
				org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
				te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));

				Document doc = new Document(compilationUnit.getSource());
				edit.apply(doc, TextEdit.UPDATE_REGIONS);
				String content = doc.get(edit.getSourceEdit().getOffset(), edit.getSourceEdit().getLength());

				if (edit.getSourceEdit().getSourceModifier() != null) {
					content = applySourceModifier(content, edit.getSourceEdit().getSourceModifier());
				}

				te.setNewText(content);
				converted.add(te);
			}
			return false; // do not visit children
		} catch (MalformedTreeException | BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
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
				org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
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
			if (edit.getSourceEdit() != null) {
				org.eclipse.lsp4j.TextEdit te = this.createTextEdit(this.changeAnnotation);
				te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));

				Document doc = new Document(compilationUnit.getSource());
				edit.apply(doc, TextEdit.UPDATE_REGIONS);
				String content = doc.get(edit.getSourceEdit().getOffset(), edit.getSourceEdit().getLength());
				if (edit.getSourceEdit().getSourceModifier() != null) {
					content = applySourceModifier(content, edit.getSourceEdit().getSourceModifier());
				}
				te.setNewText(content);
				converted.add(te);
				return false; // do not visit children
			}
		} catch (MalformedTreeException | BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

	private String applySourceModifier(String content, ISourceModifier modifier) {
		if (StringUtils.isBlank(content) || modifier == null) {
			return content;
		}

		SimpleDocument subDocument = new SimpleDocument(content);
		TextEdit newEdit = new MultiTextEdit(0, subDocument.getLength());
		ReplaceEdit[] replaces = modifier.getModifications(content);
		for (ReplaceEdit replace : replaces) {
			newEdit.addChild(replace);
		}
		try {
			newEdit.apply(subDocument, TextEdit.NONE);
		} catch (BadLocationException e) {
			JavaLanguageServerPlugin.logException("Error applying edit to document", e);
		}
		return subDocument.get();
	}

	private org.eclipse.lsp4j.TextEdit createTextEdit(String changeAnnotation) {
		if (changeAnnotation == null) {
			return new org.eclipse.lsp4j.TextEdit();
		}
		AnnotatedTextEdit ate = new org.eclipse.lsp4j.AnnotatedTextEdit();
		ate.setAnnotationId(changeAnnotation);
		return ate;
	}
}
