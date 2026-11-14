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
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist.resourcebundle;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * Finds and processes resource bundles using the ResourceBundle API.
 * Uses a project-specific ClassLoader to load bundles, which automatically handles:
 * - Classpath order
 * - Locale fallback
 * - ResourceBundle search algorithm
 * - All ResourceBundle formats (properties files, ListResourceBundle, etc.)
 */
public class ResourceBundlePropertiesFinder {

	/**
	 * Finds all resource bundle keys from the project using ResourceBundle.getBundle().
	 * This approach leverages the standard ResourceBundle API which handles:
	 * - Classpath order automatically
	 * - Locale fallback (e.g., fr_FR -> fr -> default)
	 * - All ResourceBundle formats (properties files, ListResourceBundle subclasses)
	 * - Proper encoding handling
	 *
	 * @param javaProject the Java project
	 * @param bundleName the bundle name (e.g., "messages" or "com.example.messages")
	 * @param locale the locale string (e.g., "fr", "fr_FR") or null to use default locale
	 * @param monitor the progress monitor
	 * @return map of keys to values, with locale-specific values prioritized if locale is provided
	 */
	public Map<String, String> findResourceBundleKeys(IJavaProject javaProject, String bundleName, String locale, IProgressMonitor monitor) {
		Map<String, String> keyValueMap = new LinkedHashMap<>();
		if (bundleName == null || bundleName.isEmpty() || javaProject == null) {
			return keyValueMap;
		}

		ClassLoader classLoader = null;
		try {
			// Create a classloader for the project
			classLoader = ProjectClassLoader.createClassLoader(javaProject, monitor);
			if (classLoader == null) {
				return keyValueMap;
			}

			// Parse locale string to Locale object
			Locale targetLocale = parseLocale(locale);

			// Use ResourceBundle.getBundle() which handles all the complexity:
			// - Searches classpath in order
			// - Handles locale fallback automatically
			// - Supports all ResourceBundle formats
			ResourceBundle bundle;
			try {
				bundle = ResourceBundle.getBundle(bundleName, targetLocale, classLoader);
			} catch (MissingResourceException e) {
				// Bundle doesn't exist, return empty map
				return keyValueMap;
			}
			
			// Verify the bundle was loaded correctly by checking if we can access it
			// This helps debug cases where ResourceBundle falls back to a less specific locale
			if (bundle == null) {
				return keyValueMap;
			}

			// Extract all keys and values from the bundle
			// ResourceBundle.getKeys() returns keys from this bundle and all parent bundles
			Enumeration<String> keys = bundle.getKeys();
			while (keys.hasMoreElements()) {
				if (monitor.isCanceled()) {
					break;
				}
				String key = keys.nextElement();
				try {
					// getString() automatically uses the most specific bundle in the fallback chain
					String value = bundle.getString(key);
					if (value != null) {
						keyValueMap.put(key, value);
					}
				} catch (MissingResourceException e) {
					// Skip keys that don't have string values (might be other types)
					continue;
				}
			}

		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error finding resource bundle keys", e);
		} finally {
			// Prevent ClassLoader leak: ResourceBundle.getBundle() caches bundles internally,
			// and cached bundles hold references to the ClassLoader. Clear the cache for this
			// ClassLoader to allow it to be garbage collected.
			if (classLoader != null) {
				ResourceBundle.clearCache(classLoader);
			}
		}

		return keyValueMap;
	}

	/**
	 * Parses a locale string (e.g., "fr", "fr_FR") into a Locale object.
	 * Returns Locale.getDefault() if locale is null or empty.
	 *
	 * @param localeString the locale string (e.g., "fr", "fr_FR")
	 * @return the Locale object, or Locale.getDefault() if localeString is null/empty
	 */
	private Locale parseLocale(String localeString) {
		if (localeString == null || localeString.isEmpty()) {
			return Locale.getDefault();
		}

		// Handle format: "fr" or "fr_FR"
		String[] parts = localeString.split("_");
		if (parts.length == 1) {
			return Locale.of(parts[0]);
		} else if (parts.length >= 2) {
			return Locale.of(parts[0], parts[1]);
		}

		return Locale.getDefault();
	}

}
