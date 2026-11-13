/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.jsh;

import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Classifies one {@link JshSnippetSplitter.Chunk}'s source text as an import, a declaration
 * (method/type - things that must live directly in the synthetic class body, since Java has no
 * such thing as a local method), or a statement (variable declarations and everything else, which
 * all go in the synthetic <code>__run__</code> method body, same as v1's whole-buffer wrapping
 * did, and stay visible to each other there since every statement chunk lands in that one
 * combined method, not a separate block per chunk).
 * <p>
 * Reuses {@link ASTParser}'s {@code K_CLASS_BODY_DECLARATIONS} parse kind - asking JDT's own
 * parser "does this parse cleanly as a class member?" - rather than a hand-rolled heuristic (e.g.
 * distinguishing a method declaration "foo() { }" from a control-flow statement with a block, like
 * "if (x) { }", by pattern-matching tokens is genuinely ambiguous without real grammar knowledge).
 * A field declaration also parses cleanly in that mode, but is deliberately classified as a
 * STATEMENT, not promoted - keeping it as a local variable in __run__ is simpler (no cross-chunk
 * visibility issue to begin with, since all statements share one method) and sidesteps fields not
 * being able to use {@code var}.
 */
public final class JshSnippetClassifier {

	public enum Kind {
		IMPORT, DECLARATION, STATEMENT
	}

	private JshSnippetClassifier() {
	}

	public static Kind classify(String chunkText) {
		String trimmed = chunkText.strip();
		if (trimmed.isEmpty()) {
			// An empty/blank chunk (e.g. trailing whitespace at EOF) is harmless wherever it lands;
			// treating it as a statement keeps it in __run__, the most permissive destination.
			return Kind.STATEMENT;
		}
		if (looksLikeImport(trimmed)) {
			return Kind.IMPORT;
		}
		if (parsesAsPromotableClassBodyDeclaration(trimmed)) {
			return Kind.DECLARATION;
		}
		return Kind.STATEMENT;
	}

	/**
	 * Whether the chunk's first real token (skipping leading comments and whitespace - a plain
	 * {@code String.startsWith("import")} check would be fooled by a chunk like
	 * {@code "// note\nimport java.util.List;"}) is the {@code import} keyword. Uses JDT's own
	 * scanner, same as {@link JshSnippetSplitter}, rather than hand-rolled comment-stripping.
	 */
	private static boolean looksLikeImport(String source) {
		IScanner scanner = ToolFactory.createScanner(false, false, false, JavaCore.latestSupportedJavaVersion());
		scanner.setSource(source.toCharArray());
		try {
			return scanner.getNextToken() == ITerminalSymbols.TokenNameimport;
		} catch (InvalidInputException e) {
			return false;
		}
	}

	private static boolean parsesAsPromotableClassBodyDeclaration(String source) {
		ASTParser parser = ASTParser.newParser(AST_LEVEL);
		parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
		parser.setSource(source.toCharArray());
		parser.setCompilerOptions(JavaCore.getOptions());
		ASTNode node = parser.createAST(null);
		if (!(node instanceof TypeDeclaration typeDecl)) {
			return false;
		}
		List<?> bodyDeclarations = typeDecl.bodyDeclarations();
		if (bodyDeclarations.size() != 1 || !(bodyDeclarations.get(0) instanceof BodyDeclaration declaration)) {
			return false;
		}
		if (!(declaration instanceof MethodDeclaration) && !(declaration instanceof AbstractTypeDeclaration)) {
			// Field declarations and initializer blocks fall through to STATEMENT instead.
			return false;
		}
		if ((node.getFlags() & ASTNode.MALFORMED) != 0 || (declaration.getFlags() & ASTNode.MALFORMED) != 0) {
			return false;
		}
		CompilationUnit root = (CompilationUnit) node.getRoot();
		for (IProblem problem : root.getProblems()) {
			if (problem.isError()) {
				return false;
			}
		}
		return true;
	}

	// Kept in sync with the AST level jdt.ls's shared providers (CoreASTProvider etc.) use -
	// syntax-only classification here, so the exact level matters far less than for real compiles.
	private static final int AST_LEVEL = org.eclipse.jdt.core.dom.AST.getJLSLatest();

}
