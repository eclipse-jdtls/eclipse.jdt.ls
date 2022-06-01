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

import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler.CodeActionData;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class QuickAssistComparator implements Comparator<Either<Command, CodeAction>> {

	public static int ORGANIZE_IMPORTS_PRIORITY = 0;
	public static int GENERATE_ACCESSORS_PRIORITY = 100;
	public static int GENERATE_CONSTRUCTORS_PRIORITY = 200;
	public static int GENERATE_HASHCODE_EQUALS_PRIORITY = 300;
	public static int GENERATE_TOSTRING_PRIORITY = 400;
	public static int GENERATE_OVERRIDE_IMPLEMENT_PRIORITY = 500;
	public static int GENERATE_DELEGATE_METHOD_PRIORITY = 600;
	public static int CHANGE_MODIFIER_TO_FINAL_PRIORITY = 700;
	public static int MAX_PRIORITY = 1000;

	public int compare(Either<Command, CodeAction> e1, Either<Command, CodeAction> e2) {
		if (e1.isRight() && e2.isRight()) {
			Object data1 = e1.getRight().getData();
			Object data2 = e2.getRight().getData();
			if (data1 instanceof CodeActionData && data2 instanceof CodeActionData) {
				int priority1 = ((CodeActionData) data1).getPriority();
				int priority2 = ((CodeActionData) data2).getPriority();
				return priority1 - priority2;
			} else if (data1 instanceof CodeActionData) {
				return -100;
			} else if (data2 instanceof CodeActionData) {
				return 100;
			}
		}
		return 0;
	}
}
