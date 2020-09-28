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

import org.eclipse.lsp4j.util.Preconditions;

public class SemanticTokens {

	/**
	* Tokens in a file are represented as an array of integers. The position of each token is expressed relative to
	* the token before it, because most tokens remain stable relative to each other when edits are made in a file.
	*
	* ---
	* In short, each token takes 5 integers to represent, so a specific token `i` in the file consists of the following array indices:
	*  - at index `5*i`   - `deltaLine`: token line number, relative to the previous token
	*  - at index `5*i+1` - `deltaStart`: token start character, relative to the previous token (relative to 0 or the previous token's start if they are on the same line)
	*  - at index `5*i+2` - `length`: the length of the token. A token cannot be multiline.
	*  - at index `5*i+3` - `tokenType`: will be looked up in `SemanticTokensLegend.tokenTypes`. We currently ask that `tokenType` < 65536.
	*  - at index `5*i+4` - `tokenModifiers`: each set bit will be looked up in `SemanticTokensLegend.tokenModifiers`
	*
	* ---
	* ### How to encode tokens
	*
	* Here is an example for encoding a file with 3 tokens in a uint32 array:
	* ```
	*    { line: 2, startChar:  5, length: 3, tokenType: "property",  tokenModifiers: ["private", "static"] },
	*    { line: 2, startChar: 10, length: 4, tokenType: "type",      tokenModifiers: [] },
	*    { line: 5, startChar:  2, length: 7, tokenType: "class",     tokenModifiers: [] }
	* ```
	*
	* 1. First of all, a legend must be devised. This legend must be provided up-front and capture all possible token types.
	* For this example, we will choose the following legend which must be passed in when registering the provider:
	* ```
	*    tokenTypes: ['property', 'type', 'class'],
	*    tokenModifiers: ['private', 'static']
	* ```
	*
	* 2. The first transformation step is to encode `tokenType` and `tokenModifiers` as integers using the legend. Token types are looked
	* up by index, so a `tokenType` value of `1` means `tokenTypes[1]`. Multiple token modifiers can be set by using bit flags,
	* so a `tokenModifier` value of `3` is first viewed as binary `0b00000011`, which means `[tokenModifiers[0], tokenModifiers[1]]` because
	* bits 0 and 1 are set. Using this legend, the tokens now are:
	* ```
	*    { line: 2, startChar:  5, length: 3, tokenType: 0, tokenModifiers: 3 },
	*    { line: 2, startChar: 10, length: 4, tokenType: 1, tokenModifiers: 0 },
	*    { line: 5, startChar:  2, length: 7, tokenType: 2, tokenModifiers: 0 }
	* ```
	*
	* 3. The next step is to represent each token relative to the previous token in the file. In this case, the second token
	* is on the same line as the first token, so the `startChar` of the second token is made relative to the `startChar`
	* of the first token, so it will be `10 - 5`. The third token is on a different line than the second token, so the
	* `startChar` of the third token will not be altered:
	* ```
	*    { deltaLine: 2, deltaStartChar: 5, length: 3, tokenType: 0, tokenModifiers: 3 },
	*    { deltaLine: 0, deltaStartChar: 5, length: 4, tokenType: 1, tokenModifiers: 0 },
	*    { deltaLine: 3, deltaStartChar: 2, length: 7, tokenType: 2, tokenModifiers: 0 }
	* ```
	*
	* 4. Finally, the last step is to inline each of the 5 fields for a token in a single array, which is a memory friendly representation:
	* ```
	*    // 1st token,  2nd token,  3rd token
	*    [  2,5,3,0,3,  0,5,4,1,0,  3,2,7,2,0 ]
	* ```
	*/
	private final int[] data;

	/**
	* The result id of the tokens (optional).
	*
	* This is the id that will be passed to incrementally calculate semantic tokens.
	*/
	private final String resultId;

	public SemanticTokens(int[] data) {
		this(data, null);
	}

	public SemanticTokens(int[] data, String resultId) {
		this.data = Preconditions.<int[]>checkNotNull(data, "data");
		this.resultId = resultId;
	}

	public String getResultId() {
		return resultId;
	}

	public int[] getData() {
		return data;
	}
}
