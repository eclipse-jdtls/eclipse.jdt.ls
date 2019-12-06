/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.code.flow.TypeVariableFlowInfo
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.code.flow;

import org.eclipse.jdt.core.dom.ITypeBinding;

class TypeVariableFlowInfo extends FlowInfo {

	public TypeVariableFlowInfo(ITypeBinding binding, FlowContext context) {
		super(NO_RETURN);
		addTypeVariable(binding);
	}
}

