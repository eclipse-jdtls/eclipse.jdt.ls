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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * BundleContext and Bundle utilities
 */
public class BundleUtils {

	private static final String REFERENCE_PREFIX = "reference:";

	private static class BundleInfo {

		private String version;

		private String symbolicName;

		private BundleInfo(String bundleVersion, String symbolicName) {
			this.version = bundleVersion;
			this.symbolicName = symbolicName;
		}

		private String getVersion() {
			return version;
		}

		private String getSymbolicName() {
			return symbolicName;
		}
	}

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

				boolean needInstall = true;
				BundleInfo bundleInfo = getBundleInfo(bundleLocation);
				if (bundleInfo == null) {
					status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Failed to get bundleInfo for bundle from " + bundleLocation, null));
					continue;
				}
				Bundle bundle = Platform.getBundle(bundleInfo.getSymbolicName());
				if (bundle != null) {
					if (bundle.getLocation() != location || bundle.getVersion() != new Version(bundleInfo.getVersion())) {
						bundle.uninstall();
					} else {
						bundle.update();
						needInstall = false;
					}
				}
				if (needInstall) {
					bundle = context.installBundle(location);
					bundle.start(Bundle.START_ACTIVATION_POLICY);
				}
			} catch (BundleException e) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Install bundle failure " + bundleLocation, e));
			} catch (MalformedURLException ex) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Bundle location format is not correct " + bundleLocation, ex));
			} catch (IOException e) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Cannot extract bundle symbolicName or version " + bundleLocation, e));
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

	private static BundleInfo getBundleInfo(String bundleLocation) throws IOException {
		try (JarFile jarFile = new JarFile(bundleLocation)) {
			Manifest manifest = jarFile.getManifest();
			if (manifest != null) {
				Attributes mainAttributes = manifest.getMainAttributes();
				if (mainAttributes != null) {
					String bundleVersion = mainAttributes.getValue(Constants.BUNDLE_VERSION);
					if (StringUtils.isBlank(bundleVersion)) {
						return null;
					}
					String symbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
					if (StringUtils.isNotBlank(symbolicName) && symbolicName.indexOf(";") >= 0) {
						symbolicName = symbolicName.substring(0, symbolicName.indexOf(";"));
					}
					return new BundleInfo(bundleVersion, symbolicName);
				}
			}
		}
		return null;
	}
}
