/*******************************************************************************
 * Copyright (c) Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.Assert;

public class AbstractQuickFixTest extends AbstractProjectsManagerBasedTest {

	protected void assertCodeActions(ICompilationUnit cu, Expected... expected) throws Exception {
		CompilationUnit astRoot = SharedASTProvider.getInstance().getAST(cu, null);
		IProblem[] problems = astRoot.getProblems();

		IProblem problem = problems[0];
		Range range = JDTUtils.toRange(cu, problem.getSourceStart(), 0);

		CodeActionParams parms = new CodeActionParams();

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.getFileURI(cu));
		parms.setTextDocument(textDocument);
		parms.setRange(range);
		CodeActionContext context = new CodeActionContext();
		context.setDiagnostics(DiagnosticsHandler.toDiagnosticsArray(Arrays.asList(problems)));
		parms.setContext(context);

		List<Command> codeActionCommands = new CodeActionHandler().getCodeActionCommands(parms);
		assertEquals("Number of code actions", expected.length, codeActionCommands.size());

		int k = 0;
		for (Command c : codeActionCommands) {
			Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
			Assert.assertNotNull(c.getArguments());
			Assert.assertTrue(c.getArguments().get(0) instanceof WorkspaceEdit);
			WorkspaceEdit we = (WorkspaceEdit) c.getArguments().get(0);
			List<org.eclipse.lsp4j.TextEdit> edits = we.getChanges().get(JDTUtils.getFileURI(cu));

			Document doc = new Document();
			doc.set(cu.getSource());

			String actual = applyEdits(doc, edits);
			Expected e = expected[k++];
			assertEquals(e.name, c.getTitle());
			assertEquals("Unexpected content for '" + e.name + "'", e.content, actual);
		}
	}

	protected class Expected {
		String name;
		String content;

		Expected(String name, String content) {
			this.content = content;
			this.name = name;
		}
	}

	private String applyEdits(Document doc, List<TextEdit> edits) throws BadLocationException {
		Collections.sort(edits, new Comparator<TextEdit>() {
			@Override
			public int compare(TextEdit a, TextEdit b) {
				int startDiff = comparePositions(a.getRange().getStart(), b.getRange().getStart());
				if (startDiff == 0) {
					return comparePositions(a.getRange().getEnd(), b.getRange().getEnd());
				}
				return startDiff;
			}
		});
		String text = doc.get();
		for (TextEdit e : edits) {
			int startOffset = offsetAt(doc, e.getRange().getStart());
			int endOffset = offsetAt(doc, e.getRange().getEnd());
			text = text.substring(0, startOffset) + e.getNewText() + text.substring(endOffset, text.length());
		}
		return text;
	}

	private static int offsetAt(Document doc, Position pos) throws BadLocationException {
		return doc.getLineOffset(pos.getLine()) + pos.getCharacter();
	}

	private int comparePositions(Position p1, Position p2) {
		int diff = p2.getLine() - p1.getLine();
		if (diff == 0) {
			return p2.getCharacter() - p1.getCharacter();
		}
		return diff;
	}
}
