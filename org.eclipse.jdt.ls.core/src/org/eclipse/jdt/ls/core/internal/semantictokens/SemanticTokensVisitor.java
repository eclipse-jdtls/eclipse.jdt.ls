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
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.IDocument;

public class SemanticTokensVisitor extends ASTVisitor {
	private IDocument document;
	private SemanticTokenManager manager;
	private List<SemanticToken> tokens;

	public SemanticTokensVisitor(IDocument document, SemanticTokenManager manager) {
		this.manager = manager;
		this.document = document;
		this.tokens = new ArrayList<>();
	}

	private class SemanticToken {
		private final TokenType tokenType;
		private final TokenModifier[] tokenModifiers;
		private final int offset;
		private final int length;

		public SemanticToken(int offset, int length, TokenType tokenType, TokenModifier[] tokenModifiers) {
			this.offset = offset;
			this.length = length;
			this.tokenType = tokenType;
			this.tokenModifiers = tokenModifiers;
		}

		public TokenType getTokenType() {
			return tokenType;
		}

		public TokenModifier[] getTokenModifiers() {
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
		List<Integer> data = encodedTokens();
		return new SemanticTokens(data);
	}

	private List<Integer> encodedTokens() {
		List<Integer> data = new ArrayList<>();
		int currentLine = 0;
		int currentColumn = 0;
		for (SemanticToken token : this.tokens) {
			int[] lineAndColumn = JsonRpcHelpers.toLine(this.document, token.getOffset());
			int line = lineAndColumn[0];
			int column = lineAndColumn[1];
			int deltaLine = line - currentLine;
			if (deltaLine != 0) {
				currentLine = line;
				currentColumn = 0;
			}
			int deltaColumn = column - currentColumn;
			currentColumn = column;
			// Disallow duplicate/conflict token (if exists)
			if (deltaLine != 0 || deltaColumn != 0) {
				int tokenTypeIndex = manager.getTokenTypes().indexOf(token.getTokenType());
				TokenModifier[] modifiers = token.getTokenModifiers();
				int encodedModifiers = 0;
				for (TokenModifier modifier : modifiers) {
					int bit = manager.getTokenModifiers().indexOf(modifier);
					encodedModifiers = encodedModifiers | (0b00000001 << bit);
				}
				data.add(deltaLine);
				data.add(deltaColumn);
				data.add(token.getLength());
				data.add(tokenTypeIndex);
				data.add(encodedModifiers);
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
	 * @param modifiers The modifiers of the semantic token.
	 *
	 * @apiNote This method is order-dependent because of {@link #encodedTokens()}.
	 * If semantic tokens are not added in the order they appear in the document,
	 * the encoding algorithm might discard them.
	 */
	private void addToken(ASTNode node, TokenType tokenType, TokenModifier[] modifiers) {
		int offset = node.getStartPosition();
		int length = node.getLength();
		SemanticToken token = new SemanticToken(offset, length, tokenType, modifiers);
		tokens.add(token);
	}

	/**
	 * Adds a semantic token to the list of tokens being collected by this
	 * semantic token provider. Overload for {@link #addToken(ASTNode, TokenType, TokenModifier[])}
	 * that adds no modifiers to the semantic token.
	 *
	 * @param node The AST node representing the location of the semantic token.
	 * @param tokenType The type of the semantic token.
	 *
	 * @apiNote This method is order-dependent because of {@link #encodedTokens()}.
	 * If semantic tokens are not added in the order they appear in the document,
	 * the encoding algorithm might discard them.
	 */
	private void addToken(ASTNode node, TokenType tokenType) {
		addToken(node, tokenType, new TokenModifier[0]);
	}

	@Override
	public boolean visit(ImportDeclaration node) {
		if (node.isOnDemand()) {
			visitPackageName(node.getName());
		}
		else {
			visitNonPackageName(node.getName());
		}

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
		if (packageName instanceof SimpleName) {
			addToken(packageName, TokenType.PACKAGE);
		}
		else {
			QualifiedName qualifiedName = (QualifiedName) packageName;
			visitPackageName(qualifiedName.getQualifier());
			addToken(qualifiedName.getName(), TokenType.PACKAGE);
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
	public boolean visit(SimpleName node) {
		TokenType tokenType = TokenType.getApplicableType(node.resolveBinding());
		if (tokenType != null) {
			addToken(node, tokenType, TokenModifier.getApplicableModifiers(node));
		}

		return super.visit(node);
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
		}

		for (Object typeArgument : node.typeArguments()) {
			((ASTNode) typeArgument).accept(this);
		}

		visitClassInstanceCreationType(node, node.getType());

		for (Object argument : node.arguments()) {
			((ASTNode) argument).accept(this);
		}

		if (node.getAnonymousClassDeclaration() != null) {
			node.getAnonymousClassDeclaration().accept(this);
		}

		return false;
	}

	/**
	 * Visits the type node of a class instance creation, and recursively tries to find
	 * a simple name which represents the constructor method binding. If it does,
	 * a {@link TokenType#METHOD} is added to the simple name, with the modifiers
	 * of the constructor method binding.
	 *
	 * @param classInstanceCreation The class instance creation which parents the type node.
	 * @param type The type node to visit.
	 */
	private void visitClassInstanceCreationType(ClassInstanceCreation classInstanceCreation, Type type) {
		if (type instanceof SimpleType) {
			SimpleType simpleType = (SimpleType) type;

			for (Object annotation : simpleType.annotations()) {
				((ASTNode) annotation).accept(this);
			}

			if (simpleType.getName() instanceof SimpleName) {
				addToken(simpleType.getName(), TokenType.METHOD,
					TokenModifier.getApplicableModifiers(classInstanceCreation.resolveConstructorBinding()));
			}
			else {
				QualifiedName qualifiedName = (QualifiedName) simpleType.getName();
				qualifiedName.getQualifier().accept(this);
				addToken(qualifiedName.getName(), TokenType.METHOD,
					TokenModifier.getApplicableModifiers(classInstanceCreation.resolveConstructorBinding()));
			}
		}
		else if (type instanceof QualifiedType) {
			QualifiedType qualifiedType = (QualifiedType) type;

			qualifiedType.getQualifier().accept(this);

			for (Object annotation : qualifiedType.annotations()) {
				((ASTNode) annotation).accept(this);
			}

			addToken(qualifiedType.getName(), TokenType.METHOD,
				TokenModifier.getApplicableModifiers(classInstanceCreation.resolveConstructorBinding()));
		}
		else if (type instanceof NameQualifiedType) {
			NameQualifiedType nameQualifiedType = (NameQualifiedType) type;

			nameQualifiedType.getQualifier().accept(this);

			for (Object annotation : nameQualifiedType.annotations()) {
				((ASTNode) annotation).accept(this);
			}

			addToken(nameQualifiedType.getName(), TokenType.METHOD,
				TokenModifier.getApplicableModifiers(classInstanceCreation.resolveConstructorBinding()));
		}
		else if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;

			visitClassInstanceCreationType(classInstanceCreation, parameterizedType.getType());

			for (Object typeParameter : parameterizedType.typeArguments()) {
				((ASTNode) typeParameter).accept(this);
			}
		}
		else {
			type.accept(this);
		}
	}
}
