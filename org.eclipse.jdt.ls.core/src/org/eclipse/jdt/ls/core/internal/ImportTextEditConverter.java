/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.text.edits.MoveSourceEdit;
import org.eclipse.text.edits.TextEdit;

public class ImportTextEditConverter extends TextEditConverter {

	public ImportTextEditConverter(ICompilationUnit unit, TextEdit edit) {
		super(unit, edit);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.text.edits.TextEditVisitor#visit(org.eclipse.text.edits.DeleteEdit)
	 */
	@Override
	public boolean visit(MoveSourceEdit edit) {
		try {
			org.eclipse.lsp4j.TextEdit te = new org.eclipse.lsp4j.TextEdit();
			te.setNewText("");
			te.setRange(JDTUtils.toRange(compilationUnit, edit.getOffset(), edit.getLength()));
			converted.add(te);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error converting TextEdits", e);
		}
		return super.visit(edit);
	}

}
