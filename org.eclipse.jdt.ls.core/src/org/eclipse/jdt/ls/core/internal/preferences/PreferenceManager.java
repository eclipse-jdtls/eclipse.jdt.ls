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

import org.eclipse.lsp4j.ClientCapabilities;

/**
 * Preference manager
 *
 * @author Gorkem Ercan
 * @author Fred Bricon
 *
 */
public class PreferenceManager {

	private Preferences preferences ;
	private ClientCapabilities clientCapabilities;

	public PreferenceManager() {
		preferences = new Preferences();
	}

	public void update(Preferences preferences) {
		if(preferences == null){
			throw new IllegalArgumentException("Preferences can not be null");
		}
		this.preferences = preferences;
		//TODO serialize preferences
	}

	public Preferences getPreferences() {
		return preferences;
	}

	/**
	 * @return the clientCapabilities
	 */
	public ClientCapabilities getClientCapabilities() {
		return clientCapabilities;
	}

	/**
	 * @param clientCapabilities the clientCapabilities to set
	 */
	public void setClientCapabilities(ClientCapabilities clientCapabilities) {
		this.clientCapabilities = clientCapabilities;
	}

}
