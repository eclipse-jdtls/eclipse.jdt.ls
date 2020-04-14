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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SemanticTokenManager {
    private TokenModifiers tokenModifiers;
    private List<TokenType> tokenTypes;
    private SemanticTokensLegend legend;

    private SemanticTokenManager() {
        this.tokenModifiers = new TokenModifiers();
        this.tokenTypes = Arrays.asList(TokenType.values());
        List<String> modifiers = tokenModifiers.list().stream().map(mod -> mod.toString()).collect(Collectors.toList());
        List<String> types = tokenTypes.stream().map(TokenType::toString).collect(Collectors.toList());
        this.legend = new SemanticTokensLegend(types, modifiers);
    }

    private static class SingletonHelper{
        private static final SemanticTokenManager INSTANCE = new SemanticTokenManager();
    }

    public static SemanticTokenManager getInstance(){
        return SingletonHelper.INSTANCE;
    }

    public SemanticTokensLegend getLegend() {
        return this.legend;
    }

    public TokenModifiers getTokenModifiers() {
        return tokenModifiers;
    }

    public List<TokenType> getTokenTypes() {
        return tokenTypes;
    }

}
