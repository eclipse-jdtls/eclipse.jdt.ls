/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * The container of CodeLens Providers. Extensions could implement the interface
 * to plugin a list of CodeLens providers.
 *
 */
public interface CodeLensProviderContainer {

	/**
	 * Get CodeLens provider through providerId.
	 * 
	 * @param providerId
	 *            provider id.
	 * @param pm
	 *            preference manager.
	 * @return A CodeLensProvider with the providerId.
	 */
	CodeLensProvider getCodeLensProvider(String providerId, PreferenceManager pm);
}
