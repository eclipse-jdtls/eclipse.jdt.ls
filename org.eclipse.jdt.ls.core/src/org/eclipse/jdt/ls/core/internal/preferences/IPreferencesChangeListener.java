/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
