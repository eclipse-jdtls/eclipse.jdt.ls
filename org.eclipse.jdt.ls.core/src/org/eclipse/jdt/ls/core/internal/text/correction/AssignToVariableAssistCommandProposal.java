/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.AssignToVariableAssistProposal;

public class AssignToVariableAssistCommandProposal extends AssignToVariableAssistProposal {

	private String command;
	private List<Object> commandArguments;

	public AssignToVariableAssistCommandProposal(ICompilationUnit cu, String kind, int variableKind, ExpressionStatement node, ITypeBinding typeBinding, int relevance, String command, List<Object> commandArguments) {
		super(cu, kind, variableKind, node, typeBinding, relevance);
		this.command = command;
		this.commandArguments = commandArguments;
	}

	public String getCommand() {
		return command;
	}

	public List<Object> getCommandArguments() {
		return commandArguments;
	}
}
