/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.code.CodeRefactoringUtil
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.code;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class CodeRefactoringUtil {

	public static RefactoringStatus checkMethodSyntaxErrors(int selectionStart, int selectionLength, CompilationUnit cuNode, String invalidSelectionMessage) {
		SelectionAnalyzer analyzer = new SelectionAnalyzer(Selection.createFromStartLength(selectionStart, selectionLength), true);
		cuNode.accept(analyzer);
		ASTNode coveringNode = analyzer.getLastCoveringNode();
		if (!(coveringNode instanceof Block) || !(coveringNode.getParent() instanceof MethodDeclaration)) {
			return RefactoringStatus.createFatalErrorStatus(invalidSelectionMessage);
		}
		if (ASTNodes.getMessages(coveringNode, ASTNodes.NODE_ONLY).length == 0) {
			return RefactoringStatus.createFatalErrorStatus(invalidSelectionMessage);
		}

		MethodDeclaration methodDecl = (MethodDeclaration) coveringNode.getParent();
		String message = Messages.format(RefactoringCoreMessages.CodeRefactoringUtil_error_message, BasicElementLabels.getJavaElementName(methodDecl.getName().getIdentifier()));
		return RefactoringStatus.createFatalErrorStatus(message);
	}

	//	public static int getIndentationLevel(ASTNode node, ICompilationUnit unit) throws CoreException {
	//		IPath fullPath = unit.getCorrespondingResource().getFullPath();
	//		try {
	//			FileBuffers.getTextFileBufferManager().connect(fullPath, LocationKind.IFILE, new NullProgressMonitor());
	//			ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(fullPath, LocationKind.IFILE);
	//			try {
	//				IRegion region = buffer.getDocument().getLineInformationOfOffset(node.getStartPosition());
	//				return Strings.computeIndentUnits(buffer.getDocument().get(region.getOffset(), region.getLength()), unit.getJavaProject());
	//			} catch (BadLocationException exception) {
	//				JavaPlugin.log(exception);
	//			}
	//			return 0;
	//		} finally {
	//			FileBuffers.getTextFileBufferManager().disconnect(fullPath, LocationKind.IFILE, new NullProgressMonitor());
	//		}
	//	}

	private CodeRefactoringUtil() {
	}
}
