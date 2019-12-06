/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgDestination
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg;

/**
 * {@link ReorgDestinationFactory} can create concrete instances
 */
public interface IReorgDestination {

	public static final int LOCATION_BEFORE = 1;
	public static final int LOCATION_AFTER = 2;
	public static final int LOCATION_ON = 3;

	public Object getDestination();

	public int getLocation();
}
