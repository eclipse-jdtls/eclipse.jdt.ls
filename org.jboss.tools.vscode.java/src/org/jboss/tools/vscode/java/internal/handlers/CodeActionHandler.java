/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.SharedASTProvider;
import org.jboss.tools.vscode.java.internal.TextEditConverter;
import org.jboss.tools.vscode.java.internal.corrections.DiagnosticsHelper;
import org.jboss.tools.vscode.java.internal.corrections.UnusedCodeCorrections;

/**
 * @author Gorkem Ercan
 *
 */
public class CodeActionHandler {

	/**
	 *
	 */
	private static final String COMMAND_ID_APPLY_EDIT = "java.apply.workspaceEdit";



	/**
	 * @param params
	 * @return
	 */
	public List<Command> getCodeActionCommands(CodeActionParams params) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if(unit == null ) return Collections.emptyList();
		List<Command> $ = new ArrayList<>();
		params.getContext().getDiagnostics().stream()
		.filter(this::hasCodeAssist)
		.forEach(diagnostic-> {$.addAll(getCommandForDiagnostic(unit, params.getTextDocument().getUri(), diagnostic));});
		return $;
	}


	private boolean hasCodeAssist(Diagnostic diagnostic){

		final int problemId= getProblemId(diagnostic);
		switch(problemId){
		case IProblem.UnterminatedString:
		case IProblem.UnusedImport:
		case IProblem.DuplicateImport:
		case IProblem.CannotImportPackage:
		case IProblem.ConflictingImport:
		case IProblem.ImportNotFound:
			return true;
		default:
			return false;
		}
	}

	private List<Command> getCommandForDiagnostic(ICompilationUnit unit, String uri,Diagnostic diagnostic){
		final int problemId = getProblemId(diagnostic);
		switch(problemId){
		case IProblem.UnterminatedString:
			String label = "Insert missing quote";
			WorkspaceEdit edit = new WorkspaceEdit();
			try {
				int offset = DiagnosticsHelper.getEndOffset(unit, diagnostic);
				int start = DiagnosticsHelper.getStartOffset(unit, diagnostic);
				int pos = moveBack(offset, start, "\n\r", unit.getBuffer());
				TextEdit te = new TextEdit(JDTUtils.toRange(unit, pos, 0), "\"");
				edit.getChanges().put(uri, Arrays.asList(te));
				return Arrays.asList(new Command(label, COMMAND_ID_APPLY_EDIT, Arrays.asList(edit)));
			} catch (JavaModelException e) {
				return Collections.emptyList();
			}
		case IProblem.UnusedImport:
		case IProblem.DuplicateImport:
		case IProblem.CannotImportPackage:
		case IProblem.ConflictingImport:
		case IProblem.ImportNotFound:
			//TODO: Add Organize imports command when/if we support it
			int start = DiagnosticsHelper.getStartOffset(unit, diagnostic);
			int end = DiagnosticsHelper.getEndOffset(unit, diagnostic);
			org.eclipse.text.edits.TextEdit te = UnusedCodeCorrections.createUnusedImportTextEdit(getASTRoot(unit), start, end-start-1);
			return Arrays.asList(textEditToCommand(unit, uri, "Remove unused import", te));
		default:
			return Collections.emptyList();
		}
	}

	private int getProblemId(Diagnostic diagnostic){
		int $=0;
		try{
			$ = Integer.parseInt(diagnostic.getCode());
		}catch(NumberFormatException e){
			// return 0
		}
		return $;
	}



	private static Command textEditToCommand(ICompilationUnit unit, String uri, String label, org.eclipse.text.edits.TextEdit textEdit){
		TextEditConverter converter = new TextEditConverter(unit, textEdit);
		List<TextEdit> edits = converter.convert();
		WorkspaceEdit $ = new WorkspaceEdit();
		$.getChanges().put(uri, edits);
		return new Command(label, COMMAND_ID_APPLY_EDIT, Arrays.asList($));
	}

	private static CompilationUnit getASTRoot(ICompilationUnit unit){
		return SharedASTProvider.getInstance().getAST(unit, new NullProgressMonitor());
	}

	private static int moveBack(int offset, int start, String ignoredCharacters, IBuffer buffer) {
		while (offset >= start) {
			if (ignoredCharacters.indexOf(buffer.getChar(offset - 1)) == -1) {
				return offset;
			}
			offset--;
		}
		return start;
	}


}
