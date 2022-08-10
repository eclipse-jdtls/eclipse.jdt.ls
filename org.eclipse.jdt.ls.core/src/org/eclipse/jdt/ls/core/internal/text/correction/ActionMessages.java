/*******************************************************************************
* Copyright (c) 2018-2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.text.correction;

import org.eclipse.osgi.util.NLS;

public final class ActionMessages extends NLS {
	private static final String BUNDLE_NAME = ActionMessages.class.getName();

	private ActionMessages() {
		// Do not instantiate
	}

	public static String OverrideMethodsAction_label;
	public static String GenerateGetterSetterAction_label;
	public static String GenerateGetterSetterAction_ellipsisLabel;
	public static String GenerateGetterSetterAction_templateLabel;
	public static String GenerateGetterAction_label;
	public static String GenerateGetterAction_ellipsisLabel;
	public static String GenerateGetterAction_templateLabel;
	public static String GenerateSetterAction_label;
	public static String GenerateSetterAction_ellipsisLabel;
	public static String GenerateSetterAction_templateLabel;
	public static String GenerateHashCodeEqualsAction_label;
	public static String GenerateToStringAction_label;
	public static String GenerateToStringAction_ellipsisLabel;
	public static String GenerateConstructorsAction_label;
	public static String GenerateConstructorsAction_ellipsisLabel;
	public static String GenerateDelegateMethodsAction_label;
	public static String GenerateFinalModifiersAction_label;
	public static String GenerateFinalModifiersAction_templateLabel;
	public static String GenerateFinalModifiersAction_selectionLabel;
	public static String SortMembers_templateLabel;
	public static String SortMembers_selectionLabel;
	public static String MoveRefactoringAction_label;
	public static String MoveRefactoringAction_templateLabel;
	public static String InlineMethodRefactoringAction_label;
	public static String InlineConstantRefactoringAction_label;
	public static String ReportAllErrorsForThisFile;
	public static String ReportAllErrorsForAnyNonProjectFile;
	public static String ReportSyntaxErrorsForThisFile;
	public static String ReportSyntaxErrorsForAnyNonProjectFile;

	static {
		NLS.initializeMessages(BUNDLE_NAME, ActionMessages.class);
	}
}
