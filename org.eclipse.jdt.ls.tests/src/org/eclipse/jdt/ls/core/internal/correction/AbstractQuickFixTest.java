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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
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

	protected void assertCodeActionExists(ICompilationUnit cu, Expected expected) throws Exception {
		List<Command> codeActionCommands = evaluateCodeActions(cu);
		for (Command c : codeActionCommands) {
			String actual = evaluateCodeActionCommand(c);
			if (expected.content.equals(actual)) {
				assertEquals(expected.name, c.getTitle());
				return;
			}
		}
		String res = "";
		for (Command command : codeActionCommands) {
			res += " '" + command.getTitle() + "'";
		}
		fail("Not found: " + expected.name + ", has: " + res);
	}

	protected void assertCodeActions(ICompilationUnit cu, Expected... expected) throws Exception {
		List<Command> codeActionCommands = evaluateCodeActions(cu);
		if (codeActionCommands.size() != expected.length) {
			String res = "";
			for (Command command : codeActionCommands) {
				res += " '" + command.getTitle() + "'";
			}
			assertEquals("Number of code actions: " + res, expected.length, codeActionCommands.size());
		}

		int k = 0;
		String aStr = "", eStr = "";
		for (Command c : codeActionCommands) {
			String actual = evaluateCodeActionCommand(c);
			Expected e = expected[k++];
			if (!e.name.equals(c.getTitle()) || !e.content.equals(actual)) {
				aStr += '\n' + c.getTitle() + '\n' + actual;
				eStr += '\n' + e.name + '\n' + e.content;
			}
		}
		assertEquals(eStr, aStr);
	}

	protected class Expected {
		String name;
		String content;

		Expected(String name, String content) {
			this.content = content;
			this.name = name;
		}
	}

	private List<Command> evaluateCodeActions(ICompilationUnit cu) throws JavaModelException {
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

		return new CodeActionHandler().getCodeActionCommands(parms);
	}

	private String evaluateCodeActionCommand(Command c)
			throws BadLocationException, JavaModelException {
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
		Assert.assertNotNull(c.getArguments());
		Assert.assertTrue(c.getArguments().get(0) instanceof WorkspaceEdit);
		WorkspaceEdit we = (WorkspaceEdit) c.getArguments().get(0);
		Iterator<Entry<String, List<TextEdit>>> editEntries = we.getChanges().entrySet().iterator();
		Entry<String, List<TextEdit>> entry = editEntries.next();
		assertNotNull("No edits generated", entry);
		assertEquals("More than one resource modified", false, editEntries.hasNext());

		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(entry.getKey());
		assertNotNull("CU not found: " + entry.getKey(), cu);

		Document doc = new Document();
		doc.set(cu.getSource());

		return applyEdits(doc, entry.getValue());
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
		for (int i = edits.size() - 1; i >= 0; i--) {
			TextEdit e = edits.get(i);
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
		int diff = p1.getLine() - p2.getLine();
		if (diff == 0) {
			return p1.getCharacter() - p2.getCharacter();
		}
		return diff;
	}
}
