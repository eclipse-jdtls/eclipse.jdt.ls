/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameAnalyzeUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler.ImportCandidate;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler.ImportSelection;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Handles paste events, modifying the pasted value and supplying additional
 * workspace edits to apply.
 */
public class PasteEventHandler {

	/**
	 * Represents the paste event context.
	 */
	public static class PasteEventParams {
		private final Location location;
		private final String text;
		private final String copiedDocumentUri;
		private final FormattingOptions formattingOptions;

		public PasteEventParams(Location location, String text, String copiedDocumentUri, FormattingOptions formattingOptions) {
			this.location = location;
			this.text = text;
			this.copiedDocumentUri = copiedDocumentUri;
			this.formattingOptions = formattingOptions;
		}

		public PasteEventParams() {
			this(null, null, null, null);
		}

		/**
		 * Returns the selection where the text will be inserted.
		 *
		 * @return the selection where the text will be inserted
		 */
		public Location getLocation() {
			return location;
		}

		/**
		 * Returns the text that will be inserted.
		 *
		 * @return the text that will be inserted
		 */
		public String getText() {
			return text;
		}

		/**
		 * Returns the uri of the document from which the text was copied.
		 *
		 * @return the uri of the document from which the text was copied
		 */
		public String getCopiedDocumentUri() {
			return copiedDocumentUri;
		}

		/**
		 * Returns the formating options for the document that was pasted into.
		 *
		 * @return the formating options for the document that was pasted into
		 */
		public FormattingOptions getFormattingOptions() {
			return formattingOptions;
		}
	}

	/**
	 * Represents a response to a paste event.
	 *
	 * This class is a copy of VS Code's proposed <code>DocumentPasteEdit</code>
	 */
	public static class DocumentPasteEdit {

		private final String insertText;
		private final WorkspaceEdit additionalEdit;

		public DocumentPasteEdit(String newText, WorkspaceEdit additionalEdit) {
			this.insertText = newText;
			this.additionalEdit = additionalEdit;
		}

		public DocumentPasteEdit(String modifiedText) {
			this(modifiedText, null);
		}

		public DocumentPasteEdit() {
			this(null);
		}

		public String getInsertText() {
			return insertText;
		}

		public WorkspaceEdit getAdditionalEdit() {
			return additionalEdit;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "DocumentPasteEdit [insertText=" + insertText + ", additionalEdit=" + additionalEdit + "]";
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return Objects.hash(additionalEdit, insertText);
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			DocumentPasteEdit other = (DocumentPasteEdit) obj;
			return Objects.equals(additionalEdit, other.additionalEdit) && Objects.equals(insertText, other.insertText);
		}

	}

	/**
	 * Returns the modified paste event, or null if the text can be pasted without
	 * being modified.
	 *
	 * @param params
	 *            the paste event context
	 * @param monitor
	 *            the progress monitor
	 * @return the modified paste event, or null if the text can be pasted without
	 *         being modified
	 */
	public static DocumentPasteEdit handlePasteEvent(PasteEventParams params, IProgressMonitor monitor) {
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(params.getLocation().getUri());
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(false);
		CompilationUnit ast = (CompilationUnit) parser.createAST(monitor);
		DocumentPasteEdit edit = handleStringPasteEvent(params, cu, ast, monitor);
		if (edit == null) {
			try {
				edit = getMissingImportsWorkspaceEdit(params, cu, monitor);
			} catch (CoreException e) {
				// Do nothing
			}
		}
		return edit;
	}

	private static DocumentPasteEdit handleStringPasteEvent(PasteEventParams params, ICompilationUnit cu, CompilationUnit ast, IProgressMonitor monitor) {
		StringRangeFinder finder = new StringRangeFinder(cu, params.getLocation().getRange());
		ast.accept(finder);
		if (!finder.isWithin()) {
			return null;
		}
		int stringStart = finder.getStringLiteral().getStartPosition();
		int lineNumber = ast.getLineNumber(stringStart);
		int lineStartOffset = ast.getPosition(lineNumber, 0);

		boolean isTabs = !params.getFormattingOptions().isInsertSpaces();
		int tabSize = params.getFormattingOptions().getTabSize();

		IBuffer buffer;
		try {
			buffer = cu.getBuffer();
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Error while accessing buffer while handling paste event", e);
			return null;
		}
		String text = buffer.getContents();

		// Calculate leading indentation
		StringBuilder leadingIndentationBuffer = new StringBuilder();
		for (int i = lineStartOffset; i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t'); i++) {
			leadingIndentationBuffer.append(text.charAt(i));
		}
		if (isTabs) {
			leadingIndentationBuffer.append("\t\t");
		} else {
			for (int i = 0; i < 2 * tabSize; i++) {
				leadingIndentationBuffer.append(' ');
			}
		}
		String leadingIndentation = leadingIndentationBuffer.toString();

		// Get EOL
		String eol = getEol(text);
		String newText = StringEscapeUtils.escapeJava(params.getText()).replaceAll("((?:\\\\r)?\\\\n)", "$1\" + //" + eol + leadingIndentation + "\"");
		return new DocumentPasteEdit(newText);

	}

	public static DocumentPasteEdit getMissingImportsWorkspaceEdit(PasteEventParams params, ICompilationUnit cu, IProgressMonitor monitor) throws CoreException {
		Range range = params.getLocation().getRange();
		String originalDocumentUri = params.getCopiedDocumentUri();
		String insertText = params.getText();
		if (params.getLocation().getUri().equals(originalDocumentUri)) {
			return new DocumentPasteEdit(insertText);
		}
		IType primaryType = cu.findPrimaryType();
		if (primaryType == null) {
			return new DocumentPasteEdit(insertText);
		}
		ISourceRange sourceRange = primaryType.getSourceRange();
		if (sourceRange == null) {
			return new DocumentPasteEdit(insertText);
		}
		int offset = JsonRpcHelpers.toOffset(cu, range.getStart().getLine(), range.getStart().getCharacter());
		int length = JsonRpcHelpers.toOffset(cu, range.getEnd().getLine(), range.getEnd().getCharacter()) - offset;
		if (offset <= sourceRange.getOffset() || offset + length >= sourceRange.getOffset() + sourceRange.getLength()) {
			return new DocumentPasteEdit(insertText);
		}
		Function<ImportSelection[], ImportCandidate[]> chooseFunc = null;
		ICompilationUnit tempUnit = RenameAnalyzeUtil.createNewWorkingCopy(cu, new TextChangeManager(true), new WorkingCopyOwner() {
		}, new SubProgressMonitor(monitor == null ? new NullProgressMonitor() : monitor, 1));
		tempUnit.applyTextEdit(new ReplaceEdit(offset, length, insertText), monitor);
		if (originalDocumentUri != null) {
			ICompilationUnit tempOriginalUnit = JDTUtils.resolveCompilationUnit(originalDocumentUri);
			boolean isClassFile = false;
			if (tempOriginalUnit == null) {
				IClassFile classFile = JDTUtils.resolveClassFile(originalDocumentUri);
				if (classFile != null) {
					isClassFile = true;
					tempOriginalUnit = classFile.getWorkingCopy(new WorkingCopyOwner() {
					}, monitor);
				}
			}
			if (tempOriginalUnit != null) {
				Set<String> names = Arrays.stream(tempOriginalUnit.getImports()).map(importDecl -> importDecl.getElementName()).filter(name -> name != null).collect(Collectors.toSet());
				chooseFunc = (selections) -> {
					List<ImportCandidate> candidates = new ArrayList<>();
					for (ImportSelection selection : selections) {
						for (ImportCandidate candidate : selection.candidates) {
							if (names.contains(candidate.fullyQualifiedName)) {
								candidates.add(candidate);
								break;
							}
						}
					}
					return candidates.toArray(new ImportCandidate[] {});
				};
			}
			if (isClassFile) {
				tempOriginalUnit.discardWorkingCopy();
			}
		}
		TextEdit edit = OrganizeImportsHandler.organizeImports(tempUnit, chooseFunc, true, monitor);
		if (edit == null) {
			return null;
		}
		WorkspaceEdit workspaceEdit = SourceAssistProcessor.convertToWorkspaceEdit(tempUnit, edit);
		tempUnit.discardWorkingCopy();
		return new DocumentPasteEdit(insertText, workspaceEdit);
	}

	private static String getEol(String text) {
		return text.contains("\r\n") ? "\r\n" : "\n";
	}

}

final class StringRangeFinder extends ASTVisitor {

	public StringRangeFinder(ICompilationUnit cu, Range range) {
		this.cu = cu;
		this.range = range;
	}

	private ICompilationUnit cu;
	private Range range;
	private boolean within = false;
	private StringLiteral stringLiteral = null;

	@Override
	public boolean visit(StringLiteral stringLiteral) {
		try {
			Location location = JDTUtils.toLocation(cu, stringLiteral.getStartPosition(), stringLiteral.getLength());
			Range stringRange = location.getRange();
			if ((range.getStart().getLine() > stringRange.getStart().getLine() || (range.getStart().getLine() == stringRange.getStart().getLine() && range.getStart().getCharacter() > stringRange.getStart().getCharacter()))
					&& (range.getEnd().getLine() < stringRange.getEnd().getLine() || (range.getEnd().getLine() == stringRange.getEnd().getLine() && range.getEnd().getCharacter() < stringRange.getEnd().getCharacter()))) {
				within = true;
				this.stringLiteral = stringLiteral;
			}
		} catch (CoreException e) {
		}
		return true;
	}

	public boolean isWithin() {
		return within;
	}

	public StringLiteral getStringLiteral() {
		return stringLiteral;
	}
}
