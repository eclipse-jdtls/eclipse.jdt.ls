/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class InlayHintFilterManager {

	private boolean initialized;
	private final List<InlayHintFilter> exclusions;

	private static class InstanceHolder {
		public static InlayHintFilterManager instance = new InlayHintFilterManager();
	}

	private InlayHintFilterManager() {
		this.exclusions = new ArrayList<>();
	}

	public static InlayHintFilterManager instance() { 
		return InstanceHolder.instance;
	}

	/**
	 * Check if the given method matches any inlay hint exclusion pattern.
	 * @param method
	 */
	public boolean match(IMethod method) {
		initializeIfNeeded();
		for (InlayHintFilter methodFilter : exclusions) {
			if (methodFilter.match(method)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reset the exclusion patterns. The exclusion patterns will be re-initialized
	 * in the next time a match request comes.
	 */
	public void reset() {
		this.exclusions.clear();
		this.initialized = false;
	}

	private synchronized void initialize() {
		if (this.initialized) {
			return;
		}
		initializeFromPreference();
		this.initialized = true;
	}

	private void initializeFromPreference() {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getInstance().getPreferencesManager();
		if (preferencesManager == null) {
			return;
		}

		List<String> exclusionList = preferencesManager.getPreferences().getInlayHintsExclusionList();
		if (exclusionList != null) {
			this.exclusions.clear();
			exclusionList.forEach(this::addFilter);
		}
	}

	private void addFilter(String methodPattern) {
		InlayHintFilter filter = new InlayHintFilter(methodPattern);
		if (filter.isValid()) {
			exclusions.add(filter);
		}
	}

	private void initializeIfNeeded() {
		if (initialized) {
			return;
		}
		initialize();
	}
}
