/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *     0dinD - Semantic highlighting improvements - https://github.com/eclipse/eclipse.jdt.ls/pull/1501
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;

public class SemanticTokensVisitor extends ASTVisitor {
	private CompilationUnit cu;
	private List<SemanticToken> tokens;

	public SemanticTokensVisitor(CompilationUnit cu) {
		this.cu = cu;
		this.tokens = new ArrayList<>();
	}

	private class SemanticToken {
		private final TokenType tokenType;
		private final int tokenModifiers;
		private final int offset;
		private final int length;

		public SemanticToken(int offset, int length, TokenType tokenType, int tokenModifiers) {
			this.offset = offset;
			this.length = length;
			this.tokenType = tokenType;
			this.tokenModifiers = tokenModifiers;
		}

		public TokenType getTokenType() {
			return tokenType;
		}

		public int getTokenModifiers() {
			return tokenModifiers;
		}

		public int getOffset() {
			return offset;
		}

		public int getLength() {
			return length;
		}
	}

	public SemanticTokens getSemanticTokens() {
		return new SemanticTokens(encodedTokens());
	}

	private int[] encodedTokens() {
		int numTokens = tokens.size();
		int[] data = new int[numTokens * 5];
		int currentLine = 0;
		int currentColumn = 0;
		for (int i = 0; i < numTokens; i++) {
			SemanticToken token = tokens.get(i);
			int line = cu.getLineNumber(token.getOffset()) - 1;
			int column = cu.getColumnNumber(token.getOffset());
			int deltaLine = line - currentLine;
			if (deltaLine != 0) {
				currentLine = line;
				currentColumn = 0;
			}
			int deltaColumn = column - currentColumn;
			currentColumn = column;
			// Disallow duplicate/conflict token (if exists)
			if (deltaLine != 0 || deltaColumn != 0) {
				int tokenTypeIndex = token.getTokenType().ordinal();
				int tokenModifiers = token.getTokenModifiers();

				int offset = i * 5;
				data[offset] = deltaLine;
				data[offset + 1] = deltaColumn;
				data[offset + 2] = token.getLength();
				data[offset + 3] = tokenTypeIndex;
				data[offset + 4] = tokenModifiers;
			}
		}
		return data;
	}

	/**
	 * Adds a semantic token to the list of tokens being collected by this
	 * semantic token visitor.
	 *
	 * @param node The AST node representing the location of the semantic token.
	 * @param tokenType The type of the semantic token.
	 * @param modifiers The bitwise OR of the semantic token modifiers, see {@link TokenModifier#bitmask}.
	 *
	 * @apiNote This method is order-dependent because of {@link #encodedTokens()}.
	 * If semantic tokens are not added in the order they appear in the document,
	 * the encoding algorithm might discard them.
	 */
	private void addToken(ASTNode node, TokenType tokenType, int modifiers) {
		int offset = node.getStartPosition();
		int length = node.getLength();
		SemanticToken token = new SemanticToken(offset, length, tokenType, modifiers);
		tokens.add(token);
	}

	/**
	 * Indicates that the visitor is currently inside an {@link ImportDeclaration} node.
	 */
	private boolean isInsideImportDeclaration = false;

	@Override
	public boolean visit(ImportDeclaration node) {
		isInsideImportDeclaration = true;

		IBinding binding = node.resolveBinding();
		if (binding == null || binding instanceof IPackageBinding) {
			visitPackageName(node.getName());
		}
		else {
			visitNonPackageName(node.getName());
		}

		isInsideImportDeclaration = false;
		return false;
	}

	/**
	 * Visits a name node that represents a package name, giving it
	 * the semantic token type {@link TokenType#PACKAGE}. Also recursively
	 * visits qualifying name nodes, adding the same token type, since
	 * the qualifier of a package name should always be another package name.
	 *
	 * @param packageName The package name node to recursively visit.
	 */
	private void visitPackageName(Name packageName) {
		int modifiers = isInsideImportDeclaration ? TokenModifier.IMPORT_DECLARATION.bitmask : 0;
		if (packageName instanceof SimpleName) {
			addToken(packageName, TokenType.PACKAGE, modifiers);
		}
		else {
			QualifiedName qualifiedName = (QualifiedName) packageName;
			visitPackageName(qualifiedName.getQualifier());
			addToken(qualifiedName.getName(), TokenType.PACKAGE, modifiers);
		}
	}

	/**
	 * Visits a name node that does not represent a package name, making sure to
	 * call {@link #visitPackageName(Name)} on its qualifier if the qualifier is
	 * a package name. Uses {@link #hasPackageQualifier(QualifiedName)} to test
	 * whether or not the qualifier is a package name.
	 *
	 * @param nonPackageName
	 */
	private void visitNonPackageName(Name nonPackageName) {
		if (nonPackageName instanceof SimpleName) {
			nonPackageName.accept(this);
		}
		else {
			QualifiedName qualifiedName = (QualifiedName) nonPackageName;
			Name qualifier = qualifiedName.getQualifier();

			if (hasPackageQualifier(qualifiedName)) {
				visitPackageName(qualifier);
			}
			else {
				visitNonPackageName(qualifier);
			}

			qualifiedName.getName().accept(this);
		}
	}

	/**
	 * Returns whether a qualified name has a package name as its qualifier,
	 * and makes an "educated guess" if the qualifier's binding cannot be resolved.
	 * This is in order to work around an issue where package names in import statements
	 * cannot have their bindings resolved when their corresponding package is not explicitely
	 * exported and is part of a module.
	 *
	 * @param qualifiedName A qualified name.
	 * @return {@code true} if the qualified name has a package name as its qualifier,
	 * {@code false} otherwise.
	 */
	private boolean hasPackageQualifier(QualifiedName qualifiedName) {
		IBinding qualifierBinding = qualifiedName.getQualifier().resolveBinding();
		if (qualifierBinding != null) {
			return qualifierBinding instanceof IPackageBinding;
		}
		else {
			IBinding parentBinding = qualifiedName.resolveBinding();
			return parentBinding instanceof IPackageBinding || parentBinding instanceof ITypeBinding;
		}
	}

	@Override
	public boolean visit(Modifier node) {
		addToken(node, TokenType.MODIFIER, 0);
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding binding = node.resolveBinding();
		TokenType tokenType = TokenType.getApplicableType(binding);
		if (tokenType != null) {
			int modifiers = TokenModifier.getApplicableModifiers(binding);
			if (TokenModifier.isGeneric(binding)) {
				modifiers |= TokenModifier.GENERIC.bitmask;
			}
			if (isInsideImportDeclaration) {
				modifiers |= TokenModifier.IMPORT_DECLARATION.bitmask;
			}
			else if (TokenModifier.isDeclaration(node)) {
				modifiers |= TokenModifier.DECLARATION.bitmask;
			}
			addToken(node, tokenType, modifiers);
		}

		return super.visit(node);
	}

	@Override
	public boolean visit(ParameterizedType node) {
		node.getType().accept(this);
		for (Object typeArgument : node.typeArguments()) {
			visitTypeArgument((Type) typeArgument);
		}
		return false;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
		}

		for (Object typeArgument : node.typeArguments()) {
			((ASTNode) typeArgument).accept(this);
		}

		visitSimpleNameOfType(node.getType(), (simpleName) -> {
			IMethodBinding constructorBinding = node.resolveConstructorBinding();
			int modifiers = TokenModifier.getApplicableModifiers(constructorBinding);
			if (TokenModifier.isGeneric(constructorBinding) || node.getType().isParameterizedType()) {
				modifiers |= TokenModifier.GENERIC.bitmask;
			}
			addToken(simpleName, TokenType.METHOD, modifiers);
		});

		for (Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
		}

		if (node.getAnonymousClassDeclaration() != null) {
			node.getAnonymousClassDeclaration().accept(this);
		}

		return false;
	}

	/**
	 * Visits the given type argument, making sure to add the
	 * {@link TokenModifier#TYPE_ARGUMENT} modifier to its simple name.
	 *
	 * @param typeArgument A type node that is known to be a type argument.
	 */
	private void visitTypeArgument(Type typeArgument) {
		visitSimpleNameOfType(typeArgument, (simpleName) -> {
			IBinding binding = simpleName.resolveBinding();
			TokenType tokenType = TokenType.getApplicableType(binding);
			if (tokenType != null) {
				int modifiers = TokenModifier.getApplicableModifiers(binding);
				if (TokenModifier.isGeneric((ITypeBinding) binding)) {
					modifiers |= TokenModifier.GENERIC.bitmask;
				}
				modifiers |= TokenModifier.TYPE_ARGUMENT.bitmask;
				addToken(simpleName, tokenType, modifiers);
			}
		});
	}

	/**
	 * Visits the given type node, making sure to exaustively visit
	 * its child nodes, until the simple name of the type is found or
	 * until there are no more child nodes. If and when the simple name
	 * is found, the given {@link NodeVisitor} is called with the simple
	 * name as its argument.
	 *
	 * @param type A type node.
	 * @param visitor A node visitor which might get called if the simple
	 * name of the given type node is found.
	 */
	private void visitSimpleNameOfType(Type type, NodeVisitor<SimpleName> visitor) {
		if (type == null) {
			return;
		}
		else if (type instanceof SimpleType) {
			SimpleType simpleType = (SimpleType) type;

			for (Object annotation : simpleType.annotations()) {
				((ASTNode) annotation).accept(this);
			}

			Name simpleTypeName = simpleType.getName();
			if (simpleTypeName instanceof SimpleName) {
				visitor.visit((SimpleName) simpleTypeName);
			}
			else {
				QualifiedName qualifiedName = (QualifiedName) simpleTypeName;
				qualifiedName.getQualifier().accept(this);
				visitor.visit(qualifiedName.getName());
			}
		}
		else if (type instanceof QualifiedType) {
			QualifiedType qualifiedType = (QualifiedType) type;
			qualifiedType.getQualifier().accept(this);
			for (Object annotation : qualifiedType.annotations()) {
				((ASTNode) annotation).accept(this);
			}
			visitor.visit(qualifiedType.getName());
		}
		else if (type instanceof NameQualifiedType) {
			NameQualifiedType nameQualifiedType = (NameQualifiedType) type;
			nameQualifiedType.getQualifier().accept(this);
			for (Object annotation : nameQualifiedType.annotations()) {
				((ASTNode) annotation).accept(this);
			}
			visitor.visit(nameQualifiedType.getName());
		}
		else if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			visitSimpleNameOfType(parameterizedType.getType(), visitor);
			for (Object typeArgument : parameterizedType.typeArguments()) {
				visitTypeArgument((Type) typeArgument);
			}
		}
		else {
			type.accept(this);
		}
	}

	@FunctionalInterface
	private interface NodeVisitor<T extends ASTNode> {
		public void visit(T node);
	}
}
