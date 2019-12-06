/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.reorg.IPackageFragmentRootManipulationQuery
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;

/**
 * Query that can be used when manipulating package fragment roots.
 * Depending on the context, <code>confirmManipulation</code> can be used to
 * determine whether, for example, the package fragment root is to be deleted or
 * not or if the classpath of the referencing projects is to be updated.
 */
public interface IPackageFragmentRootManipulationQuery {

	public boolean confirmManipulation(IPackageFragmentRoot root, IJavaProject[] referencingProjects);
}
