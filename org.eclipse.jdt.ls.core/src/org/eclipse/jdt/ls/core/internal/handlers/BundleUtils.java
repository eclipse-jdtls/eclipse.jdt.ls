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

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * BundleContext and Bundle utilities
 */
public class BundleUtils {

	private static final String REFERENCE_PREFIX = "reference:";

	private static final String BUNDLES_KEY = "bundles";

	/**
	 * Load bundles from the initialization request
	 *
	 * @param initializationOptions
	 *            initialization options from language client.
	 */

	public static void loadBundles(Object initializationOptions) {
		Map<String, Object> optionsMap = null;
		if (initializationOptions instanceof Map<?, ?>) {
			optionsMap = (Map<String, Object>) initializationOptions;
		}
		if (optionsMap == null) {
			return;
		}

		Object bundleObject = optionsMap.get(BUNDLES_KEY);
		if (bundleObject instanceof ArrayList<?>) {
			BundleContext context = JavaLanguageServerPlugin.getBundleContext();
			@SuppressWarnings("unchecked")
			ArrayList<String> bundleList = (ArrayList<String>) bundleObject;
			if (bundleList == null || bundleList.size() == 0) {
				return;
			}
			for (String bundlePath : bundleList) {
				try {
					String bundleLocation = getBundleLocation(bundlePath, true);
					if (StringUtils.isEmpty(bundleLocation)) {
						JavaLanguageServerPlugin.logError("Empty bundle location");
						continue;
					}

					Bundle bundle = context.getBundle(bundleLocation);
					if (bundle != null) {
						bundle.update();
					} else {
						bundle = context.installBundle(bundleLocation);
						startBundle(bundle);
					}
				} catch (BundleException e) {
					JavaLanguageServerPlugin.logException("Install bundle failure ", e);
				}
			}
		}

	}

	private static String getBundleLocation(String location, boolean useReference) {
		File f = new File(location);
		String bundleLocation = null;
		try {
			bundleLocation = f.toURI().toURL().toString();
			if (useReference) {
				bundleLocation = REFERENCE_PREFIX + bundleLocation;
			}
		} catch (MalformedURLException e) {
			JavaLanguageServerPlugin.logException("Get bundle location failure ", e);
		}
		return bundleLocation;
	}

	private static void startBundle(Bundle bundle) {
		if (bundle.getState() == Bundle.UNINSTALLED || bundle.getState() == Bundle.STARTING || bundle.getBundleId() == 0) {
			return;
		}
		try {
			bundle.start();
		} catch (BundleException e) {
			JavaLanguageServerPlugin.logException("Start bundle failure ", e);
		}
	}
}
