/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;

public class AbstractQuickFixTest extends AbstractProjectsManagerBasedTest {

	private List<String> ignoredCommands;

	private List<String> ignoredKinds = Arrays.asList(CodeActionKind.Source + ".*");

	protected void assertCodeActionExists(ICompilationUnit cu, Expected expected) throws Exception {
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);
		for (Either<Command, CodeAction> c : codeActions) {
			String actual = evaluateCodeActionCommand(c);
			if (expected.content.equals(actual)) {
				assertEquals(expected.name, getCommand(c).getTitle());
				return;
			}
		}
		String res = "";
		for (Either<Command, CodeAction> command : codeActions) {
			if (res.length() > 0) {
				res += '\n';
			}
			res += getCommand(command).getTitle();
		}
		assertEquals("Not found.", expected.name, res);
	}

	protected void assertCodeActionNotExists(ICompilationUnit cu, String label) throws Exception {
		List<Either<Command, CodeAction>> codeActionCommands = evaluateCodeActions(cu);
		assertFalse("'" + label + "' should not be added to the code actions", codeActionCommands.stream().filter(ca -> getCommand(ca).getTitle().equals(label)).findAny().isPresent());
	}

	protected void assertCodeActions(ICompilationUnit cu, Collection<Expected> expected) throws Exception {
		assertCodeActions(cu, expected.toArray(new Expected[expected.size()]));
	}

	protected void assertCodeActions(ICompilationUnit cu, Expected... expecteds) throws Exception {
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);
		if (codeActions.size() < expecteds.length) {
			String res = "";
			for (Either<Command, CodeAction> command : codeActions) {
				res += " '" + getCommand(command).getTitle() + "'";
			}
			assertEquals("Number of code actions: " + res, expecteds.length, codeActions.size());
		}

		Map<String, Expected> expectedActions = new HashMap<>();
		for (Expected expected : expecteds) {
			for (Either<Command, CodeAction> command : codeActions) {
				if (Objects.equals(getCommand(command).getTitle(), expected.name)) {
					expectedActions.put(expected.name, expected);
					break;
				}
			}
			assertEquals("Should prompt code action: " + expected.name, expectedActions.containsKey(expected.name), true);
		}

		int k = 0;
		String aStr = "", eStr = "", testContent = "";
		for (Either<Command, CodeAction> c : codeActions) {
			String title = getCommand(c).getTitle();
			if (expectedActions.containsKey(title)) {
				String actual = evaluateCodeActionCommand(c);
				Expected e = expectedActions.get(title);
				if (!Objects.equals(e.content, actual)) {
					aStr += '\n' + title + '\n' + actual;
					eStr += '\n' + e.name + '\n' + e.content;
				}
				testContent += generateTest(actual, getCommand(c).getTitle(), k);
				k++;
			}
		}
		if (aStr.length() > 0) {
			aStr += '\n' + testContent;
		}
		assertEquals(eStr, aStr);
	}

	protected String generateTest(String actual, String name, int k) {
		StringBuilder builder = new StringBuilder();
		String[] lines = actual.split("\n");
		builder.append("		buf = new StringBuilder();\n");
		for (String line : lines) {
			wrapInBufAppend(line, builder);
		}
		builder.append("		Expected e" + k + " = new Expected(\"" + name + "\", buf.toString());\n");
		builder.append("\n");
		return builder.toString();
	}

	private static void wrapInBufAppend(String curr, StringBuilder buf) {
		buf.append("		buf.append(\"");

		int last = curr.length() - 1;
		for (int k = 0; k <= last; k++) {
			char ch = curr.charAt(k);
			if (ch == '\n') {
				buf.append("\\n\");\n");
				if (k < last) {
					buf.append("buf.append(\"");
				}
			} else if (ch == '\r') {
				// ignore
			} else if (ch == '\t') {
				buf.append("    "); // 4 spaces
			} else if (ch == '"' || ch == '\\') {
				buf.append('\\').append(ch);
			} else {
				buf.append(ch);
			}
		}
		if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '\n') {
			buf.append("\\n\");\n");
		}
	}

	public class Expected {
		String name;
		String content;

		public Expected(String name, String content) {
			this.content = content;
			this.name = name;
		}
	}

	protected Range getRange(ICompilationUnit cu, IProblem[] problems) throws JavaModelException {
		if (problems.length == 0) {
			return new Range(new Position(), new Position());
		}
		IProblem problem = problems[0];
		return JDTUtils.toRange(cu, problem.getSourceStart(), 0);
	}

	protected void setIgnoredCommands(String... ignoredCommands) {
		this.ignoredCommands = Arrays.asList(ignoredCommands);
	}

	protected void setIgnoredKind(String... ignoredKind) {
		this.ignoredKinds = Arrays.asList(ignoredKind);
	}

	protected List<Either<Command, CodeAction>> evaluateCodeActions(ICompilationUnit cu) throws JavaModelException {

		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
		IProblem[] problems = astRoot.getProblems();

		Range range = getRange(cu, problems);

		CodeActionParams parms = new CodeActionParams();

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(cu));
		parms.setTextDocument(textDocument);
		parms.setRange(range);
		CodeActionContext context = new CodeActionContext();
		context.setDiagnostics(DiagnosticsHandler.toDiagnosticsArray(cu, Arrays.asList(problems)));
		parms.setContext(context);

		List<Either<Command, CodeAction>> codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(parms, new NullProgressMonitor());

		if (this.ignoredKinds != null) {
			List<Either<Command, CodeAction>> filteredList = codeActions.stream().filter(Either::isRight).filter(codeAction -> {
				for (String str : this.ignoredKinds) {
					if (codeAction.getRight().getKind().matches(str)) {
						return true;
					}
				}
				return false;
			}).collect(Collectors.toList());
			codeActions.removeAll(filteredList);
		}

		if (this.ignoredCommands != null) {
			List<Either<Command, CodeAction>> filteredList = new ArrayList<>();
			for (Either<Command, CodeAction> codeAction : codeActions) {
				for (String str : this.ignoredCommands) {
					if (getCommand(codeAction).getTitle().matches(str)) {
						filteredList.add(codeAction);
						break;
					}
				}
			}
			codeActions.removeAll(filteredList);
		}
		return codeActions;
	}

	private String evaluateCodeActionCommand(Either<Command, CodeAction> codeAction)
			throws BadLocationException, JavaModelException {

		Command c = codeAction.isLeft() ? codeAction.getLeft() : codeAction.getRight().getCommand();
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
		Assert.assertNotNull(c.getArguments());
		Assert.assertTrue(c.getArguments().get(0) instanceof WorkspaceEdit);
		WorkspaceEdit we = (WorkspaceEdit) c.getArguments().get(0);
		if (we.getDocumentChanges() != null) {
			return evaluateChanges(we.getDocumentChanges());
		}
		return evaluateChanges(we.getChanges());
	}

	private String evaluateChanges(List<Either<TextDocumentEdit, ResourceOperation>> documentChanges) throws BadLocationException, JavaModelException {
		List<TextDocumentEdit> changes = documentChanges.stream().filter(e -> e.isLeft()).map(e -> e.getLeft()).collect(Collectors.toList());
		assertFalse("No edits generated", changes.isEmpty());
		Set<String> uris = changes.stream().map(tde -> tde.getTextDocument().getUri()).distinct().collect(Collectors.toSet());
		assertEquals("Only one resource should be modified", 1, uris.size());
		String uri = uris.iterator().next();
		List<TextEdit> edits = changes.stream().flatMap(e -> e.getEdits().stream()).collect(Collectors.toList());
		return evaluateChanges(uri, edits);
	}

	private String evaluateChanges(Map<String, List<TextEdit>> changes) throws BadLocationException, JavaModelException {
		Iterator<Entry<String, List<TextEdit>>> editEntries = changes.entrySet().iterator();
		Entry<String, List<TextEdit>> entry = editEntries.next();
		assertNotNull("No edits generated", entry);
		assertEquals("More than one resource modified", false, editEntries.hasNext());
		return evaluateChanges(entry.getKey(), entry.getValue());
	}

	private String evaluateChanges(String uri, List<TextEdit> edits) throws BadLocationException, JavaModelException {
		assertFalse("No edits generated: " + edits, edits == null || edits.isEmpty());
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		assertNotNull("CU not found: " + uri, cu);
		Document doc = new Document();
		doc.set(cu.getSource());
		return TextEditUtil.apply(doc, edits);
	}

	public Command getCommand(Either<Command, CodeAction> codeAction) {
		return codeAction.isLeft() ? codeAction.getLeft() : codeAction.getRight().getCommand();
	}

}
