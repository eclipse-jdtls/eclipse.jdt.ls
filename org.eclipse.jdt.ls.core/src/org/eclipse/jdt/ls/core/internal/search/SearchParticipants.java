/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Arcadiy Ivanov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.search;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * Provides access to all registered {@link SearchParticipant} instances,
 * including the default Java search participant and any contributed via the
 * {@code org.eclipse.jdt.ls.core.searchParticipant} extension point.
 *
 * <p>Contributed participants are cached and only recomputed when the extension
 * registry signals that extensions have been added or removed.</p>
 */
public final class SearchParticipants implements IRegistryEventListener {

	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.searchParticipant";
	private static final String CLASS = "class";

	private static final SearchParticipants INSTANCE = new SearchParticipants();

	/** Cached contributed participants (excludes the default Java participant). Guarded by {@code this}. */
	private SearchParticipant[] contributedParticipants;

	private SearchParticipants() {
		Platform.getExtensionRegistry().addListener(this, EXTENSION_POINT_ID);
	}

	/**
	 * Returns an array containing the default Java search participant followed by
	 * any search participants contributed via the extension point.
	 *
	 * <p>If no extensions are registered, the returned array contains only the
	 * default participant (identical to the previous hardcoded behavior).</p>
	 *
	 * @return a non-empty array of search participants
	 */
	public static SearchParticipant[] getSearchParticipants() {
		SearchParticipant defaultParticipant = SearchEngine.getDefaultSearchParticipant();
		SearchParticipant[] contributed = INSTANCE.getContributedParticipants();
		if (contributed.length == 0) {
			return new SearchParticipant[] { defaultParticipant };
		}
		SearchParticipant[] result = new SearchParticipant[contributed.length + 1];
		result[0] = defaultParticipant;
		System.arraycopy(contributed, 0, result, 1, contributed.length);
		return result;
	}

	private synchronized SearchParticipant[] getContributedParticipants() {
		if (contributedParticipants == null) {
			contributedParticipants = loadContributedParticipants();
		}
		return contributedParticipants;
	}

	private static SearchParticipant[] loadContributedParticipants() {
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
		if (elements.length == 0) {
			return new SearchParticipant[0];
		}
		List<SearchParticipant> participants = new ArrayList<>(elements.length);
		for (IConfigurationElement element : elements) {
			try {
				Object extension = element.createExecutableExtension(CLASS);
				if (extension instanceof SearchParticipant searchParticipant) {
					participants.add(searchParticipant);
				} else {
					JavaLanguageServerPlugin.logError("Invalid extension to " + EXTENSION_POINT_ID
							+ ". Must extend " + SearchParticipant.class.getName());
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to create search participant", e);
			}
		}
		return participants.toArray(new SearchParticipant[0]);
	}

	@Override
	public synchronized void added(IExtension[] extensions) {
		// Invalidate the cache; will be reloaded on next access
		contributedParticipants = null;
	}

	@Override
	public synchronized void removed(IExtension[] extensions) {
		// Invalidate the cache; will be reloaded on next access
		contributedParticipants = null;
	}

	@Override
	public void added(IExtensionPoint[] extensionPoints) {
	}

	@Override
	public void removed(IExtensionPoint[] extensionPoints) {
	}
}
