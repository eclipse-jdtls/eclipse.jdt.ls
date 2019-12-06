/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.tagging.IQualifiedNameUpdating
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.tagging;

public interface IQualifiedNameUpdating {

	/**
	 * Performs a dynamic check whether this refactoring object is capable of
	 * updating qualified names in non Java files. The return value of this
	 * method may change according to the state of the refactoring.
	 */
	public boolean canEnableQualifiedNameUpdating();

	/**
	 * If <code>canEnableQualifiedNameUpdating</code> returns <code>true</code>,
	 * then this method is used to ask the refactoring object whether references
	 * in non Java files should be updated. This call can be ignored if
	 * <code>canEnableQualifiedNameUpdating</code> returns <code>false</code>.
	 */
	public boolean getUpdateQualifiedNames();

	/**
	 * If <code>canEnableQualifiedNameUpdating</code> returns <code>true</code>,
	 * then this method is used to inform the refactoring object whether
	 * references in non Java files should be updated. This call can be ignored
	 * if <code>canEnableQualifiedNameUpdating</code> returns
	 * <code>false</code>.
	 */
	public void setUpdateQualifiedNames(boolean update);

	public String getFilePatterns();

	public void setFilePatterns(String patterns);
}

