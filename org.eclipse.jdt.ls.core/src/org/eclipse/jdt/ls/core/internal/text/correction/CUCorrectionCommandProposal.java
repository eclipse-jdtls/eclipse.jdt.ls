/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;

public class CUCorrectionCommandProposal extends CUCorrectionProposal {

	private String command;
	private List<Object> commandArguments;

	/**
	 * @param name
	 * @param kind
	 * @param cu
	 * @param relevance
	 */
	public CUCorrectionCommandProposal(String name, String kind, ICompilationUnit cu, int relevance, String command, List<Object> commandArguments) {
		super(name, kind, cu, null, relevance);
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
