/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

/**
 * A listener used to receive changes to preference values
 *
 * @author snjeza
 *
 */
public interface IPreferencesChangeListener {

	/**
	 * Notification that a preferences have changed
	 *
	 */
	public void preferencesChange(Preferences oldPreferences, Preferences newPreferences);
}
