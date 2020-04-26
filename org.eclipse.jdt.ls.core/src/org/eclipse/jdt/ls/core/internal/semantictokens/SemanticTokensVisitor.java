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
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.semantictokens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.IDocument;

public class SemanticTokensVisitor extends ASTVisitor {
    private IDocument document;
    private SemanticTokenManager manager;
    private List<SemanticToken> tokens;

    public final static ITokenModifier[] NO_MODIFIERS = {};

    public SemanticTokensVisitor(IDocument document, SemanticTokenManager manager) {
        this.manager = manager;
        this.document = document;
        this.tokens = new ArrayList<>();
    }

    private class SemanticToken {
        private final TokenType tokenType;
        private final ITokenModifier[] tokenModifiers;
        private final int offset;
        private final int length;

        public SemanticToken(int offset, int length, TokenType tokenType, ITokenModifier[] tokenModifiers) {
            this.offset = offset;
            this.length = length;
            this.tokenType = tokenType;
            this.tokenModifiers = tokenModifiers;
        }

        public TokenType getTokenType() {
            return tokenType;
        }

        public ITokenModifier[] getTokenModifiers() {
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
        List<Integer> data = encoded();
        return new SemanticTokens(data);
    }

    private List<Integer> encoded() {
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
            int tokenTypeIndex = manager.getTokenTypes().indexOf(token.getTokenType());
            ITokenModifier[] modifiers = token.getTokenModifiers();
            int encodedModifiers = 0;
            for (ITokenModifier modifier : modifiers) {
                int bit = manager.getTokenModifiers().indexOf(modifier);
                if (bit >= 0) {
                    encodedModifiers = encodedModifiers | (0b00000001 << bit);
                }
            }
            data.add(deltaLine);
            data.add(deltaColumn);
            data.add(token.getLength());
            data.add(tokenTypeIndex);
            data.add(encodedModifiers);
        }
        return data;
    }

    private void addToken(ASTNode node, TokenType tokenType, ITokenModifier[] modifiers) {
        int offset = node.getStartPosition();
        int length = node.getLength();
        SemanticToken token = new SemanticToken(offset, length, tokenType, modifiers);
        tokens.add(token);
    }

    @Override
    public boolean visit(SimpleName node) {
        IBinding binding = node.resolveBinding();
        if (binding == null) {
            return super.visit(node);
        }

        TokenType tokenType = null;
        switch (binding.getKind()) {
            case IBinding.VARIABLE: {
                if (((IVariableBinding) binding).isField()) {
                    tokenType = TokenType.PROPERTY;
                } else {
                    tokenType = TokenType.VARIABLE;
                }
                break;
            }
            case IBinding.METHOD: {
                tokenType = TokenType.FUNCTION;
                break;
            }
            case IBinding.TYPE: {
                tokenType = TokenType.TYPE;
                break;
            }
            case IBinding.PACKAGE: {
                tokenType = TokenType.NAMESPACE;
                break;
            }
            default:
                break;
        }

        if (tokenType == null) {
            return super.visit(node);
        }

        switch (tokenType) {
            case FUNCTION:
            case VARIABLE:
            case PROPERTY:
            case MEMBER: {
                ITokenModifier[] modifiers = getModifiers(binding);
                addToken(node, tokenType, modifiers);
                break;
            }
            case TYPE:
            case NAMESPACE:
                addToken(node, tokenType, NO_MODIFIERS);
                break;
            default:
                break;
        }

        return super.visit(node);
    }

    @Override
    public boolean visit(MethodInvocation node) {
        return super.visit(node);
    }

    private ITokenModifier[] getModifiers(IBinding binding) {
        return manager.getTokenModifiers().values().stream()
                      .filter(tm -> tm.applies(binding))
                      .toArray(size -> new ITokenModifier[size]);
    }

    @Override
    public boolean visit(SimpleType node) {
        ASTNode parent = node.getParent();
        if (parent instanceof ClassInstanceCreation) { // For ClassInstanceCreation "new E()", "E" should be highlighted as 'function' instead of 'type'
            addToken(node, TokenType.FUNCTION, NO_MODIFIERS);
            return false;
        }
        return super.visit(node);
    }

}
