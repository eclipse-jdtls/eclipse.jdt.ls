/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.tagging;

public interface IReferenceUpdating {

	/**
	 * Informs the refactoring object whether references should be updated.
	 *
	 * @param update
	 *            <code>true</code> to enable reference updating
	 */
	public void setUpdateReferences(boolean update);

	/**
	 * Asks the refactoring object whether references should be updated.
	 *
	 * @return <code>true</code> iff reference updating is enabled
	 */
	public boolean getUpdateReferences();

}

