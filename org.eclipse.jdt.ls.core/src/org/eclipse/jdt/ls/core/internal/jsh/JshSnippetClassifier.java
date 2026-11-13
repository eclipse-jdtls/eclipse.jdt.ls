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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

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
 * <p>
 * That exclusion has a knock-on effect this class only exposes the building blocks for -
 * {@link JshSnippetWrapper} does the actual propagation: a <em>non</em>-var field that depends on
 * an excluded {@code var} (e.g. {@code var n = 2; int m = n;} - {@code m}'s initializer references
 * {@code n}) can't be promoted either, since {@code n} stays local to {@code __run__} and wouldn't
 * be visible to {@code m} as a field. {@link #parseAsField} and {@link
 * #referencesAnyIdentifier} exist so the wrapper can compute this transitively (a chain of
 * dependent fields all collapse back to STATEMENT) before making the final per-chunk decision -
 * see {@code JshSnippetWrapper.wrap}.
 */
public final class JshSnippetClassifier {

	public enum Kind {
		IMPORT, DECLARATION, STATEMENT
	}

	private JshSnippetClassifier() {
	}

	/**
	 * A chunk's declared variable name(s) and whether it's {@code var}-typed, for a chunk that
	 * parses as a field declaration - regardless of whether it's actually promotable (a
	 * {@code var}-typed one isn't, but {@link JshSnippetWrapper} still needs to know its name, to
	 * recognize when a later, otherwise-promotable field depends on it). A field declaration can
	 * declare more than one name (e.g. {@code int a = 1, b = 2;}).
	 */
	public record FieldInfo(List<String> names, boolean isVar) {
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
		FieldInfo field = parseAsField(trimmed);
		if (field != null) {
			return field.isVar() ? Kind.STATEMENT : Kind.DECLARATION;
		}
		if (parsesAsPromotableMethodOrType(trimmed)) {
			return Kind.DECLARATION;
		}
		return Kind.STATEMENT;
	}

	/**
	 * @return the chunk's declared name(s) and var-ness if it parses as a field declaration
	 *         (whether or not that field is actually promotable), or {@code null} otherwise
	 */
	public static FieldInfo parseAsField(String chunkText) {
		String trimmed = chunkText.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		ASTNode declaration = parseSingleBodyDeclaration(trimmed);
		if (!(declaration instanceof FieldDeclaration field)) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (Object fragment : field.fragments()) {
			names.add(((VariableDeclarationFragment) fragment).getName().getIdentifier());
		}
		return new FieldInfo(names, field.getType().isVar());
	}

	/**
	 * @return {@code true} if any identifier token in {@code chunkText} has text matching one of
	 *         {@code names} - used to detect a field's initializer referencing a non-promotable (or
	 *         already-demoted) variable. A token-based check, not a real reference-resolution - a
	 *         false positive (e.g. matching a field access like {@code foo.n} or a shadowing
	 *         parameter named the same) only costs a missed promotion opportunity, never a
	 *         correctness bug, so erring conservative here is the right default.
	 */
	public static boolean referencesAnyIdentifier(String chunkText, Set<String> names) {
		if (names.isEmpty()) {
			return false;
		}
		IScanner scanner = ToolFactory.createScanner(false, false, false, JavaCore.latestSupportedJavaVersion());
		scanner.setSource(chunkText.toCharArray());
		try {
			int token;
			while ((token = scanner.getNextToken()) != ITerminalSymbols.TokenNameEOF) {
				if (token == ITerminalSymbols.TokenNameIdentifier && names.contains(new String(scanner.getCurrentTokenSource()))) {
					return true;
				}
			}
		} catch (InvalidInputException e) {
			// Malformed/incomplete trailing content - best effort, no match found so far stands.
		}
		return false;
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

	private static boolean parsesAsPromotableMethodOrType(String source) {
		ASTNode declaration = parseSingleBodyDeclaration(source);
		return declaration instanceof MethodDeclaration || declaration instanceof AbstractTypeDeclaration;
	}

	/**
	 * Parses {@code source} as a single class body declaration (field, method, type, or
	 * initializer block).
	 *
	 * @return the parsed declaration node, or {@code null} if {@code source} isn't exactly one
	 *         syntactically-clean declaration (malformed, a real compile problem, more than one
	 *         declaration, or not a declaration at all - e.g. a bare statement or an incomplete
	 *         chunk still being typed)
	 */
	private static ASTNode parseSingleBodyDeclaration(String source) {
		ASTParser parser = ASTParser.newParser(AST_LEVEL);
		parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
		parser.setSource(source.toCharArray());
		parser.setCompilerOptions(JavaCore.getOptions());
		ASTNode node = parser.createAST(null);
		if (!(node instanceof TypeDeclaration typeDecl)) {
			return null;
		}
		List<?> bodyDeclarations = typeDecl.bodyDeclarations();
		if (bodyDeclarations.size() != 1 || !(bodyDeclarations.get(0) instanceof BodyDeclaration declaration)) {
			return null;
		}
		if ((node.getFlags() & ASTNode.MALFORMED) != 0 || (declaration.getFlags() & ASTNode.MALFORMED) != 0) {
			return null;
		}
		CompilationUnit root = (CompilationUnit) node.getRoot();
		for (IProblem problem : root.getProblems()) {
			if (problem.isError()) {
				return null;
			}
		}
		return declaration;
	}

	// Kept in sync with the AST level jdt.ls's shared providers (CoreASTProvider etc.) use -
	// syntax-only classification here, so the exact level matters far less than for real compiles.
	private static final int AST_LEVEL = org.eclipse.jdt.core.dom.AST.getJLSLatest();

}
