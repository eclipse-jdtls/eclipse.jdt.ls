/*******************************************************************************
 * Copyright (c) 2000, 2023 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.FastJavaPartitionScanner
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.partition;


import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.text.AbstractFastJavaPartitionScanner;

/**
 * This scanner recognizes the JavaDoc comments, Java multi line comments, Java single line comments,
 * Java strings and Java characters.
 */
public class FastJavaPartitionScanner extends AbstractFastJavaPartitionScanner {
	public FastJavaPartitionScanner(boolean emulate) {
		super(emulate);
	}

	public FastJavaPartitionScanner() {
		super(false);
	}

	public FastJavaPartitionScanner(IJavaProject javaProject) {
		super(javaProject);
	}

	@Override
	protected void setJavaProject() {
	}
}
