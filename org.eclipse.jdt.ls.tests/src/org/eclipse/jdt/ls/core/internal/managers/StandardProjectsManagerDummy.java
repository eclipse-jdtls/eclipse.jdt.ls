/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.util.stream.Stream;

import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * @author siarhei_leanavets1
 *
 */
public class StandardProjectsManagerDummy extends StandardProjectsManager {

	/**
	 * @param preferenceManager
	 */
	public StandardProjectsManagerDummy(PreferenceManager preferenceManager) {
		super(preferenceManager);
	}

	@Override
	public Stream<IBuildSupport> buildSupports() {
		return super.buildSupports();
	}

}
