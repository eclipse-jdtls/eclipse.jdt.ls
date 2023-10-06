/*******************************************************************************
 * Copyright (c) 2012, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Billy Huang <billyhuang31@gmail.com> - [quick assist] concatenate/merge string literals - https://bugs.eclipse.org/77632
 *     Lukas Hanke <hanke@yatta.de> - Bug 241696 [quick fix] quickfix to iterate over a collection - https://bugs.eclipse.org/bugs/show_bug.cgi?id=241696
 *     Sandra Lions <sandra.lions-piron@oracle.com> - [quick fix] for qualified enum constants in switch-case labels - https://bugs.eclipse.org/bugs/90140
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

/**
 * Interface defining relevance values for quick fixes/assists.
 *
 * @see org.eclipse.jdt.ui.text.java.IJavaCompletionProposal#getRelevance()
 */
public interface IProposalRelevance extends org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance {
	public static final int LAMBDA_EXPRESSION_AND_METHOD_REF_CLEANUP = 1;
	public static final int MOVE_REFACTORING = 6;
	public static final int EXTRACT_INTERFACE = 7;
	public static final int ADD_MISSING_ANNOTATION_ATTRIBUTES = 5;
}
