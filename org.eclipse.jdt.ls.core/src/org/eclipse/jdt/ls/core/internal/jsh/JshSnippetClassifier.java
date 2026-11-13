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
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Classifies one {@link JshSnippetSplitter.Chunk}'s source text as an import, a declaration
 * (method/type/variable - things promoted to a real member of the synthetic class body) or a
 * statement (everything else, which goes in the synthetic <code>__run__</code> method body, same
 * as v1's whole-buffer wrapping did for a buffer with no imports/declarations).
 * <p>
 * Reuses {@link ASTParser}'s {@code K_CLASS_BODY_DECLARATIONS} parse kind - asking JDT's own
 * parser "does this parse cleanly as a class member?" - rather than a hand-rolled heuristic (e.g.
 * distinguishing a method declaration "foo() { }" from a control-flow statement with a block, like
 * "if (x) { }", by pattern-matching tokens is genuinely ambiguous without real grammar knowledge).
 * <p>
 * Variable declarations are promoted to fields (not left as STATEMENTs in __run__) specifically so
 * a later-declared method can see an earlier-declared script variable - a real, common JShell
 * pattern (declare some state, then a helper method that uses it), matching real JShell's own
 * semantics. This is safe to do unconditionally: Java's field forward-reference restriction
 * (JLS 8.3.3) only applies to a field's simple name used in *another field's initializer* that
 * textually precedes it, and since promoted declarations always keep their original relative
 * order, anything that was a backward reference before promotion stays one, and anything that was
 * a forward reference (a genuine "used before declared" error, already illegal as sequential local
 * statements) stays equally illegal - promotion can't newly break anything that compiled before.
 * The one declaration kind excluded is a {@code var}-typed variable, since {@code var} is
 * local-variable-only in Java and can't be used for a field - that one stays a STATEMENT, local to
 * __run__, and won't be visible to a later-declared method (a narrow, documented limitation).
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
		if (declaration instanceof FieldDeclaration field) {
			if (field.getType().isVar()) {
				// var is local-variable-only in Java - can't be promoted to a field.
				return false;
			}
		} else if (!(declaration instanceof MethodDeclaration) && !(declaration instanceof AbstractTypeDeclaration)) {
			// Initializer blocks (bare "{ ... }") fall through to STATEMENT instead.
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
