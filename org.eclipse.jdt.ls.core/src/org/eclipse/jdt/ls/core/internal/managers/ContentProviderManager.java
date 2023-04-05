/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.IContentProvider;
import org.eclipse.jdt.ls.core.internal.IDecompiler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class ContentProviderManager {

	private static final String EMPTY_CONTENT = "";
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.contentProvider";
	private static final String CLASS = "class";
	private static final String ID = "id";
	private static final String PRIORITY = "priority";
	private static final String URI_PATTERN = "uriPattern";
	private static final int DEFAULT_PRIORITY = 500;
	private static final Pattern DEFAULT_URI_PATTERN = Pattern.compile(".*\\.class.*");

	private final PreferenceManager preferenceManager;

	private Set<ContentProviderDescriptor> descriptors;

	public ContentProviderManager(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	/**
	 * Get text content for a class file if possible
	 *
	 * @param classFile
	 *            the class file to get content from
	 * @param monitor
	 * @return the text content if successful, or <code>null</code> if unsuccessful,
	 *         or an empty string if canceled
	 */
	public String getSource(IClassFile classFile, IProgressMonitor monitor) {
		if (classFile == null) {
			return null;
		}
		return getContent(classFile, classFile.getHandleIdentifier(), IDecompiler.class, monitor);
	}

	/**
	 * Get text content for a given resource if possible
	 *
	 * @param uri
	 *            the URI of the item to get content from
	 * @param monitor
	 * @return the text content if successful, or <code>null</code> if unsuccessful,
	 *         or an empty string if canceled
	 */
	public String getContent(URI uri, IProgressMonitor monitor) {
		if (uri == null) {
			return null;
		}
		return getContent(uri, uri.toString(), IContentProvider.class, monitor);
	}

	private String getContent(Object source, String cacheKey, Class<? extends IContentProvider> providerType, IProgressMonitor monitor) {
		URI uri = source instanceof URI u ? u : null;
		List<ContentProviderDescriptor> matches = findMatchingProviders(uri);
		if (monitor.isCanceled()) {
			return EMPTY_CONTENT;
		}

		int previousPriority = -1;
		for (ContentProviderDescriptor match : matches) {
			IContentProvider contentProvider = match.getContentProvider();
			if (!providerType.isInstance(contentProvider)) {
				JavaLanguageServerPlugin.logError("Unable to load " + providerType.getSimpleName() + " class for " + match.id);
				continue;
			}

			if (monitor.isCanceled()) {
				return EMPTY_CONTENT;
			}

			if (previousPriority == match.priority) {
				requestPreferredProvider(match.priority, matches);
			}
			try {
				contentProvider.setPreferences(preferenceManager.getPreferences());
				String content = null;
				if (uri != null) {
					content = contentProvider.getContent(uri, monitor);
				} else if (source instanceof IClassFile classFile) {
					content = ((IDecompiler) contentProvider).getSource(classFile, monitor);
				}
				if (monitor.isCanceled()) {
					return EMPTY_CONTENT;
				} else if (content != null) {
					return content;
				}
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException("Error getting content via " + match.id, e);
			}

			previousPriority = match.priority;
		}

		return EMPTY_CONTENT;
	}

	private synchronized Set<ContentProviderDescriptor> getDescriptors(List<String> preferredProviderIds) {
		if (descriptors == null) {
			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			descriptors = Stream.of(elements).map(e -> new ContentProviderDescriptor(e)).collect(Collectors.toSet());
		}
		return descriptors;
	}

	private List<ContentProviderDescriptor> findMatchingProviders(URI uri) {
		List<String> preferredProviderIds = preferenceManager.getPreferences().getPreferredContentProviderIds();
		Set<ContentProviderDescriptor> descriptors = getDescriptors(preferredProviderIds);
		if (descriptors.isEmpty()) {
			JavaLanguageServerPlugin.logError("No content providers found");
			return null;
		}

		String uriString = uri != null ? uri.toString() : null;

		List<ContentProviderDescriptor> matches = descriptors.stream()
				.filter(d -> uriString != null ? d.uriPattern.matcher(uriString).find() : true)
				.peek(d -> d.calculateEffectivePriority(preferredProviderIds))
				.sorted((d1, d2) -> d1.priority - d2.priority)
				.collect(Collectors.toList());

		if (matches.isEmpty()) {
			JavaLanguageServerPlugin.logError("Unable to find content provider for URI " + uri);
			return null;
		}

		return matches;
	}

	private void requestPreferredProvider(int duplicatePriority, List<ContentProviderDescriptor> matches) {
		Object[] unprioritizedIds = matches.stream().filter(d -> d.priority == duplicatePriority).map(d -> d.id).toArray();
		JavaLanguageServerPlugin
				.logError(String.format("You have more than one content provider installed: %s. Please use the \"java.contentProvider.preferred\" setting to choose which one you want to use.", Arrays.toString(unprioritizedIds)));
	}

	private static class ContentProviderDescriptor {

		private final IConfigurationElement configurationElement;
		public final String id;
		private final int basePriority;
		public int priority;
		public final Pattern uriPattern;

		public ContentProviderDescriptor(IConfigurationElement element) {
			configurationElement = element;
			id = configurationElement.getAttribute(ID);
			basePriority = parsePriority();
			priority = basePriority;
			String uriPatternString = configurationElement.getAttribute(URI_PATTERN);
			uriPattern = uriPatternString != null ? Pattern.compile(uriPatternString) : DEFAULT_URI_PATTERN;
		}

		private int parsePriority() {
			try {
				return Integer.parseInt(configurationElement.getAttribute(PRIORITY));
			} catch (NumberFormatException nfe) {
				return DEFAULT_PRIORITY;
			}
		}

		public void calculateEffectivePriority(List<String> preferredProviderIds) {
			priority = basePriority;
			if (preferredProviderIds != null) {
				int index = preferredProviderIds.indexOf(id);
				if (index != -1) {
					priority = index + 1;
				}
			}
		}

		public synchronized IContentProvider getContentProvider() {
			try {
				Object extension = configurationElement.createExecutableExtension(CLASS);
				if (extension instanceof IContentProvider contentProvider) {
					return contentProvider;
				} else {
					String message = "Invalid extension to " + EXTENSION_POINT_ID + ". Must implement " + IContentProvider.class.getName();
					JavaLanguageServerPlugin.logError(message);
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to create content provider ", e);
			}
			return null;
		}
	}
}
