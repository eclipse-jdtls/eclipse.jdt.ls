/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.code.flow.TryFlowInfo
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.code.flow;


class TryFlowInfo extends FlowInfo {

	public TryFlowInfo() {
		super();
	}

	public void mergeResources(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}

		mergeSequential(info, context);
	}

	public void mergeTry(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}

		mergeSequential(info, context);
	}

	public void mergeCatch(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}

		mergeConditional(info, context);
	}

	public void mergeFinally(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}

		mergeSequential(info, context);
	}
}

