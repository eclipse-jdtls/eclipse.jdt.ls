/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

import java.util.Comparator;

import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler.CodeActionData;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CodeActionComparator implements Comparator<Either<Command, CodeAction>> {

	public static int ORGANIZE_IMPORTS_PRIORITY = 0;
	public static int ADD_ALL_MISSING_IMPORTS_PRIORITY = 5;
	public static int GENERATE_ACCESSORS_PRIORITY = 10;
	public static int GENERATE_CONSTRUCTORS_PRIORITY = 20;
	public static int GENERATE_HASHCODE_EQUALS_PRIORITY = 30;
	public static int GENERATE_TOSTRING_PRIORITY = 40;
	public static int GENERATE_OVERRIDE_IMPLEMENT_PRIORITY = 50;
	public static int GENERATE_DELEGATE_METHOD_PRIORITY = 60;
	public static int SORT_MEMBERS_PRIORITY = 65;
	public static int CHANGE_MODIFIER_TO_FINAL_PRIORITY = 70;
	public static int LOWEST_PRIORITY = 100;

	@Override
	public int compare(Either<Command, CodeAction> e1, Either<Command, CodeAction> e2) {
		if (e1.isRight() && e2.isRight()) {
			CodeAction action1 = e1.getRight();
			CodeAction action2 = e2.getRight();
			int kindDiff = getCodeActionKindOrdinal(action1.getKind()) - getCodeActionKindOrdinal(action2.getKind());
			if (kindDiff != 0) {
				return kindDiff;
			}
			Object data1 = action1.getData();
			Object data2 = action2.getData();
			if (data1 instanceof CodeActionData codeActionData1 && data2 instanceof CodeActionData codeActionData2) {
				int priority1 = codeActionData1.getPriority();
				int priority2 = codeActionData2.getPriority();
				return priority1 - priority2;
			} else if (data1 instanceof CodeActionData) {
				return 10;
			} else if (data2 instanceof CodeActionData) {
				return -10;
			}
		}
		return 0;
	}

	private int getCodeActionKindOrdinal(String kind) {
		if (kind.equals(CodeActionKind.QuickFix)) {
			return 0;
		} else if (kind.startsWith(CodeActionKind.Refactor)) {
			return 1000;
		} else if (kind.equals(JavaCodeActionKind.QUICK_ASSIST)) {
			return 2000;
		} else if (kind.startsWith(CodeActionKind.Source)) {
			return 3000;
		}
		return 4000;
	}
}
