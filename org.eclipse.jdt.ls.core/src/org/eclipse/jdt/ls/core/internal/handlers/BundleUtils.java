/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.osgi.container.ModuleContainer;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Namespace;

/**
 * BundleContext and Bundle utilities
 */
public final class BundleUtils {

	private static final String REFERENCE_PREFIX = "reference:";

	private static final class BundleInfo {

		private String version;

		private String symbolicName;

		private boolean isSingleton;

		private BundleInfo(String bundleVersion, String symbolicName, boolean isSingleton) {
			this.version = bundleVersion;
			this.symbolicName = symbolicName;
			this.isSingleton = isSingleton;
		}

		private String getVersion() {
			return version;
		}

		private String getSymbolicName() {
			return symbolicName;
		}

		private boolean isSingleton() {
			return isSingleton;
		}
	}

	private BundleUtils() {
		//prevent instantianation
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
		Set<Bundle> bundlesToStart = new HashSet<>();
		Set<Bundle> toRefresh = new HashSet<>();
		FrameworkWiring frameworkWiring = context.getBundle(0).adapt(FrameworkWiring.class);
		for (String bundleLocation : bundleLocations) {
			try {
				if (StringUtils.isEmpty(bundleLocation)) {
					JavaLanguageServerPlugin.logError("Empty bundle location");
					continue;
				}

				String location = getBundleLocation(bundleLocation, true);

				BundleInfo bundleInfo = getBundleInfo(bundleLocation);

				if (bundleInfo == null) {
					status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Failed to get bundleInfo for bundle from " + bundleLocation, null));
					continue;
				}

				Bundle[] bundles = getBundles(bundleInfo.getSymbolicName(), frameworkWiring);
				if (bundles != null) {
					Version currentBundleVersion = Version.parseVersion(bundleInfo.getVersion());
					if (bundleInfo.isSingleton()) {
						if (bundles.length > 1) {
							status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Multiple singleton bundles are installed: " + bundleInfo.getSymbolicName()));
							continue;
						} else if (bundles[0].getLocation().equals(location) && bundles[0].getVersion().equals(currentBundleVersion)) {
							continue;
						}

						// Uninstall the singleton bundle if the location or version is not equal
						uninstallBundle(bundlesToStart, toRefresh, bundles[0]);
					} else {
						boolean shouldSkip = false;
						for (Bundle bundle : bundles) {
							if (bundle.getVersion().equals(currentBundleVersion)) {
								if (bundle.getLocation().equals(location)) {
									shouldSkip = true;
								} else {
									// Uninstall non-singleton bundle if it's the same version but different location
									uninstallBundle(bundlesToStart, toRefresh, bundle);
								}
								break;
							}
						}
						if (shouldSkip) {
							continue;
						}
					}
				}

				installBundle(context, bundlesToStart, location);
			} catch (BundleException e) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Install bundle failure " + bundleLocation, e));
			} catch (MalformedURLException ex) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Bundle location format is not correct " + bundleLocation, ex));
			} catch (IOException e) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Cannot extract bundle symbolicName or version " + bundleLocation, e));
			}
		}

		refreshBundles(toRefresh, frameworkWiring);

		status.addAll(startBundles(bundlesToStart));
		if (status.getChildren().length > 0) {
			throw new CoreException(status);
		}
	}

	/**
	 * Install the bundle within the given location.
	 *
	 * @param context
	 *            Bundle context
	 * @param bundlesToStart
	 *            The set containing bundles which need to start
	 * @param location
	 *            The location of the bundle which needs to be installed
	 * @throws BundleException
	 */
	private static void installBundle(BundleContext context, Set<Bundle> bundlesToStart, String location) throws BundleException {
		Bundle newlyInstalledBundle = context.installBundle(location);
		JavaLanguageServerPlugin.logInfo("Installed " + newlyInstalledBundle.getLocation());
		bundlesToStart.add(newlyInstalledBundle);
	}

	/**
	 * Uninstall the specified bundle and update the set for bundle refreshing and
	 * starting.
	 *
	 * @param bundlesToStart
	 *            The set containing bundles which need to start
	 * @param toRefresh
	 *            The set containing bundles which need to refresh after
	 *            unsintallation
	 * @param bundle
	 *            Bundle needs to be uninstalled
	 *
	 * @throws BundleException
	 */
	private static void uninstallBundle(Set<Bundle> bundlesToStart, Set<Bundle> toRefresh, Bundle bundle) throws BundleException {
		bundle.uninstall();
		JavaLanguageServerPlugin.logInfo("Uninstalled " + bundle.getLocation());
		toRefresh.add(bundle);
		bundlesToStart.remove(bundle);
	}

	private static Bundle[] getBundles(String symbolicName, FrameworkWiring fwkWiring) {
		BundleContext context = fwkWiring.getBundle().getBundleContext();
		if (Constants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(symbolicName)) {
			symbolicName = context.getBundle(Constants.SYSTEM_BUNDLE_LOCATION).getSymbolicName();
		}
		StringBuilder filter = new StringBuilder();
		filter.append('(')
			.append(IdentityNamespace.IDENTITY_NAMESPACE)
			.append('=')
			.append(symbolicName)
			.append(')');
		Map<String, String> directives = Collections.singletonMap(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filter.toString());
		Collection<BundleCapability> matchingBundleCapabilities = fwkWiring.findProviders(ModuleContainer.createRequirement(IdentityNamespace.IDENTITY_NAMESPACE, directives, Collections.emptyMap()));
		if (matchingBundleCapabilities.isEmpty()) {
			return null;
		}
		Bundle[] results = matchingBundleCapabilities.stream().map(c -> c.getRevision().getBundle())
				// Remove all the bundles that are uninstalled
				.filter(bundle -> (bundle.getState() & (Bundle.UNINSTALLED)) == 0)
				.sorted((b1, b2) -> b2.getVersion().compareTo(b1.getVersion())) // highest version first
				.toArray(Bundle[]::new);
		return results.length > 0 ? results : null;
	}

	private static void refreshBundles(Set<Bundle> toRefresh, FrameworkWiring frameworkWiring) {
		if (!toRefresh.isEmpty()) {
			JavaLanguageServerPlugin.logInfo("Refresh the bundles");
			final CountDownLatch latch = new CountDownLatch(1);
			frameworkWiring.refreshBundles(toRefresh, new FrameworkListener() {
				@Override
				public void frameworkEvent(FrameworkEvent event) {
					if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
						latch.countDown();
					} else if (event.getType() == FrameworkEvent.ERROR) {
						JavaLanguageServerPlugin.logException("Error happens when refreshing the bundles", event.getThrowable());
						latch.countDown();
					}
				}
			});
			try {
				latch.await();
			} catch (InterruptedException e) {
				JavaLanguageServerPlugin.logException("InterruptedException happened when refreshing", e);
			}
			JavaLanguageServerPlugin.logInfo("Finished Refreshing bundles");
		}
	}

	/**
	 * @param array
	 */
	private static IStatus startBundles(Collection<Bundle> bundles) {
		final BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		MultiStatus status = new MultiStatus(context.getBundle().getSymbolicName(), IStatus.OK, "Starting added bundles", null);
		for (Bundle bundle : bundles) {
			if (bundle.getState() == Bundle.UNINSTALLED) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Could not start: " + bundle.getSymbolicName() + '(' + bundle.getLocation() + ':' + bundle.getBundleId() + ')' + ". It's state is uninstalled."));
				continue;
			}
			if (bundle.getState() == Bundle.STARTING) {
				continue;
			}
			if (bundle.getBundleId() == 0) {
				continue;
			}

			try {
				// set to the default value for osgi.bundles.defaultStartLevel
				bundle.adapt(BundleStartLevel.class).setStartLevel(4);
				bundle.start(Bundle.START_ACTIVATION_POLICY);
				JavaLanguageServerPlugin.logInfo("Started " + bundle.getLocation());
			} catch (BundleException e) {
				status.add(new Status(IStatus.ERROR, context.getBundle().getSymbolicName(), "Bundle startup failed " + bundle.getLocation(), e));
			}
		}
		return status;

	}

	private static String getBundleLocation(String location, boolean useReference) throws MalformedURLException {
		File f = new File(location);
		String bundleLocation = f.toURI().toString();
		if (useReference) {
			bundleLocation = REFERENCE_PREFIX + bundleLocation;
		}
		return bundleLocation;
	}

	private static BundleInfo getBundleInfo(String bundleLocation) throws IOException, BundleException {
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
					boolean isSingleton = false;
					if (StringUtils.isNotBlank(symbolicName)) {
						ManifestElement[] symbolicNameElements = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
						if (symbolicNameElements.length > 0) {
							symbolicName = symbolicNameElements[0].getValue();
							String singleton = symbolicNameElements[0].getDirective(Constants.SINGLETON_DIRECTIVE);
							isSingleton = "true".equals(singleton);
						}
					}
					return new BundleInfo(bundleVersion, symbolicName, isSingleton);
				}
			}
		}
		return null;
	}
}
