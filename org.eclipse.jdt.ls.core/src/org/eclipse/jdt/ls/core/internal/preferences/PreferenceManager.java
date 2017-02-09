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

import java.util.Hashtable;

import org.eclipse.jdt.core.JavaCore;
/**
 * Preference manager
 *
 * @author Gorkem Ercan
 * @author Fred Bricon
 *
 */
public class PreferenceManager {

	private Preferences preferences ;

	public PreferenceManager() {
		preferences = new Preferences();
	}

	/**
	 * Initialize default preference values of used bundles to match
	 * server functionality.
	 */
	public void initialize() {
		// Update JavaCore options
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		JavaCore.setOptions(javaCoreOptions);
	}

	public void update(Preferences preferences) {
		this.preferences = preferences;
		//TODO serialize preferences
	}

	public Preferences getPreferences() {
		return preferences;
	}

}
