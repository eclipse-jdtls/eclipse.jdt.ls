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
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * BundleContext and Bundle utilities
 */
public class BundleUtils {

	private static final String REFERENCE_PREFIX = "reference:";

	/**
	 * Load a collection of bundle based on the provided file path locations.
	 *
	 * @param bundleLocations
	 *            The collection of the bundle file path location
	 * @throws CoreException
	 *             throw the <code>CoreException</code> if failed to load any
	 *             bundles.
	 */
	public static void loadBundles(Collection<String> bundleLocations) throws CoreException {
		if (bundleLocations == null || bundleLocations.isEmpty()) {
			return;
		}

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		MultiStatus status = new MultiStatus(context.getBundle().getSymbolicName(), IStatus.OK, "Load bundle list", null);
		for (String bundleLocation : bundleLocations) {
			try {
				if (StringUtils.isEmpty(bundleLocation)) {
					JavaLanguageServerPlugin.logError("Empty bundle location");
					continue;
				}

				String location = getBundleLocation(bundleLocation, true);

				Bundle bundle = context.getBundle(location);
				if (bundle != null) {
					bundle.update();
				} else {
					bundle = context.installBundle(location);
					bundle.start(Bundle.START_ACTIVATION_POLICY);
				}
			} catch (BundleException e) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Install bundle failure " + bundleLocation, e));
			} catch (MalformedURLException ex) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Bundle location format is not correct " + bundleLocation, ex));
			}
		}
		if (status.getChildren().length > 0) {
			throw new CoreException(status);
		}
	}

	private static String getBundleLocation(String location, boolean useReference) throws MalformedURLException {
		File f = new File(location);
		String bundleLocation = f.toURI().toString();
		if (useReference) {
			bundleLocation = REFERENCE_PREFIX + bundleLocation;
		}
		return bundleLocation;
	}
}
