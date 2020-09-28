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

public final class SemanticTokensLegend {
	private final String[] tokenTypes;
	private final String[] tokenModifiers;

	public SemanticTokensLegend() {
		tokenTypes = Arrays.stream(TokenType.values())
			.map(TokenType::toString)
			.toArray(String[]::new);
		tokenModifiers = Arrays.stream(TokenModifier.values())
			.map(TokenModifier::toString)
			.toArray(String[]::new);
	}

	public String[] getTokenTypes() {
		return tokenTypes;
	}

	public String[] getTokenModifiers() {
		return tokenModifiers;
	}
}
