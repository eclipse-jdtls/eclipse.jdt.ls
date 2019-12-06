/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.code.flow.EnhancedForFlowInfo
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.code.flow;


class EnhancedForFlowInfo extends FlowInfo {

	public void mergeParameter(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}
		mergeAccessModeSequential(info, context);
	}

	public void mergeExpression(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}
		mergeAccessModeSequential(info, context);
	}

	public void mergeAction(FlowInfo info, FlowContext context) {
		if (info == null) {
			return;
		}

		info.mergeEmptyCondition(context);

		mergeSequential(info, context);
	}
}

