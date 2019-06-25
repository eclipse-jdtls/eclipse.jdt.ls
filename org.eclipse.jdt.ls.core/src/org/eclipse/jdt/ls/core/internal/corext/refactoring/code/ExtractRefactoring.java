/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.refactoring.code;

import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.ltk.core.refactoring.Refactoring;

public abstract class ExtractRefactoring extends Refactoring {
	public abstract ITrackedNodePosition getExtractedNodePosition();
}
