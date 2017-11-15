/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 ********************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;
import org.eclipse.jdt.ls.core.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 */
public class LinkedNameAssistProposal extends CUCorrectionProposal {
  private String valueSuggestion;
  private SimpleName node;

  public LinkedNameAssistProposal(String label, ICompilationUnit cu, SimpleName node, String suggestion) {
    super(label, cu, IProposalRelevance.RENAME_REFACTORING_QUICK_FIX);
    this.valueSuggestion = suggestion;
    this.node = node;
  }

  @Override
  protected void addEdits(IDocument doc, TextEdit root) throws CoreException {
    super.addEdits(doc, root);
    CompilationUnit unit = SharedASTProvider.getInstance().getAST(getCompilationUnit(), null);
    ASTNode[] sameNodes = LinkedNodeFinder.findByNode(unit, node);
    for (ASTNode curr : sameNodes) {
      root.addChild(new ReplaceEdit(curr.getStartPosition(), curr.getLength(), valueSuggestion));
    }

  }

}
