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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SemanticTokenManager {
	private List<TokenType> tokenTypes;
	private List<TokenModifier> tokenModifiers;
	private SemanticTokensLegend legend;

	private SemanticTokenManager() {
		this.tokenTypes = Arrays.asList(TokenType.values());
		this.tokenModifiers = Arrays.asList(TokenModifier.values());
		List<String> types = tokenTypes.stream().map(TokenType::toString).collect(Collectors.toList());
		List<String> modifiers = tokenModifiers.stream().map(TokenModifier::toString).collect(Collectors.toList());
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

	public List<TokenType> getTokenTypes() {
		return tokenTypes;
	}

	public List<TokenModifier> getTokenModifiers() {
		return tokenModifiers;
	}

}
