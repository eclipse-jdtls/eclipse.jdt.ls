/*******************************************************************************
* Copyright (c) 2023 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal;

public class DecompilerResult {
	String content;
	int[] originalLineMappings;
	int[] decompiledLineMappings;

	public DecompilerResult(String content) {
		this.content = content;
	}

	public DecompilerResult(String content, int[] originalLineMappings) {
		this.content = content;
		this.originalLineMappings = originalLineMappings;
	}

	public DecompilerResult(String content, int[] originalLineMappings,
			int[] decompiledLineMappings) {
		this.content = content;
		this.originalLineMappings = originalLineMappings;
		this.decompiledLineMappings = decompiledLineMappings;
	}

	/**
	 * Returns the decompiled source content.
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Returns the line mappings from original line to decompiled line.
	 * Its format is as follows, in ascending order by original line.
	 * - [i]: the original line number
	 * - [i+1]: the decompiled line number
	 */
	public int[] getOriginalLineMappings() {
		return originalLineMappings;
	}

	/**
	 * Returns the line mappings from decompiled line to original line.
	 * Its format is as follows, in ascending order by decompiled line.
	 * - [i]: the decompiled line number
	 * - [i+1]: the original line number
	 */
	public int[] getDecompiledLineMappings() {
		return decompiledLineMappings;
	}
}
