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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.text.edits.InsertEdit;
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
	private final ICompilationUnit compilationUnit;
	private final List<org.eclipse.lsp4j.TextEdit> converted;

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
}
