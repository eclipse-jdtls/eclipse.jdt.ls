/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.preferences;

import java.util.Hashtable;

import org.eclipse.jdt.core.JavaCore;
/**
 * Preference initializer. Used for adjusting the default
 * preference values of used bundles to match server functionality.
 *
 * @author Gorkem Ercan
 *
 */
public class PreferenceInitializer {

	private PreferenceInitializer() {
		//no public instanciation
	}

	public static void adjustPreferences() {

		// Update JavaCore options
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		JavaCore.setOptions(javaCoreOptions);
	}

}
