/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/ui/text/java/correction/ASTRewriteCorrectionProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

/**
 * A proposal for quick fixes and quick assists that works on an AST rewrite. Either a rewrite is
 * directly passed in the constructor or the method {@link #getRewrite()} is overridden to provide
 * the AST rewrite that is evaluated on the document when the proposal is applied.
 */
public class ASTRewriteCorrectionProposal extends CUCorrectionProposal {

	private ASTRewrite fRewrite;
	private ImportRewrite fImportRewrite;

	/**
	 * Constructs an AST rewrite correction proposal.
	 *
	 * @param name the display name of the proposal
	 * @param cu the compilation unit that is modified
	 * @param rewrite the AST rewrite that is invoked when the proposal is applied or
	 *            <code>null</code> if {@link #getRewrite()} is overridden
	 * @param relevance the relevance of this proposal
	 */
	public ASTRewriteCorrectionProposal(String name, String kind, ICompilationUnit cu, ASTRewrite rewrite, int relevance) {
		super(name, kind, cu, null, relevance);
		fRewrite= rewrite;
	}

	/**
	 * Returns the import rewrite used for this compilation unit.
	 *
	 * @return the import rewrite or <code>null</code> if no import rewrite has been set
	 * @nooverride This method is not intended to be re-implemented or extended by clients.
	 */
	public ImportRewrite getImportRewrite() {
		return fImportRewrite;
	}

	/**
	 * Sets the import rewrite used for this compilation unit.
	 *
	 * @param rewrite the import rewrite
	 * @nooverride This method is not intended to be re-implemented or extended by clients.
	 */
	public void setImportRewrite(ImportRewrite rewrite) {
		fImportRewrite= rewrite;
	}

	/**
	 * Creates and sets the import rewrite used for this compilation unit.
	 *
	 * @param astRoot the AST for the current CU
	 * @return the created import rewrite
	 * @nooverride This method is not intended to be re-implemented or extended by clients.
	 */
	public ImportRewrite createImportRewrite(CompilationUnit astRoot) {
		fImportRewrite = CodeStyleConfiguration.createImportRewrite(astRoot, true);
		return fImportRewrite;
	}


	@Override
	protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
		super.addEdits(document, editRoot);
		ASTRewrite rewrite= getRewrite();
		if (rewrite != null) {
			try {
				TextEdit edit= rewrite.rewriteAST();
				editRoot.addChild(edit);
			} catch (IllegalArgumentException e) {
				throw new CoreException(StatusFactory.newErrorStatus("Invalid AST rewriter", e));
			}
		}
		if (fImportRewrite != null) {
			editRoot.addChild(fImportRewrite.rewriteImports(new NullProgressMonitor()));
		}
	}

	/**
	 * Returns the rewrite that has been passed in the constructor. Implementors can override this
	 * method to create the rewrite lazily. This method will only be called once.
	 *
	 * @return the rewrite to be used
	 * @throws CoreException when the rewrite could not be created
	 */
	protected ASTRewrite getRewrite() throws CoreException {
		if (fRewrite == null) {
			IStatus status = StatusFactory.newErrorStatus("Rewrite not initialized", null); //$NON-NLS-1$
			throw new CoreException(status);
		}
		return fRewrite;
	}
}
