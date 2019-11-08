/*******************************************************************************
 * Copyright (c) 2017, 2019 Microsoft Corporation and others.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.osgi.container.Module;
import org.eclipse.osgi.container.ModuleRevision;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Capability;

/**
 * BundleContext and Bundle utilities
 */
public final class BundleUtils {

	private static final String REFERENCE_PREFIX = "reference:";

	private static final class BundleInfo {

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

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((symbolicName == null) ? 0 : symbolicName.hashCode());
			result = prime * result + ((version == null) ? 0 : version.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BundleInfo other = (BundleInfo) obj;
			if (symbolicName == null) {
				if (other.symbolicName != null)
					return false;
			} else if (!symbolicName.equals(other.symbolicName))
				return false;
			if (version == null) {
				if (other.version != null)
					return false;
			} else if (!version.equals(other.version))
				return false;
			return true;
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
		Collection<Bundle> bundlesToStart = new ArrayList<>();
		Set<Bundle> toRefresh = new HashSet<>();
		Map<String, Bundle> installedBundles = new HashMap<>();
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

				// Platform.getBundle() won't return the bundle with INSTALLED state, so we use this Map to persist the current installed bundles
				Bundle installedBundle = installedBundles.get(bundleInfo.getSymbolicName());
				if (installedBundle != null) {
					Module module = installedBundle.adapt(Module.class);
					if (isSingleton(module.getCurrentRevision())) {
						// We have installed this singleton bundle before, so skip this one.
						continue;
					}
				}

				Bundle bundle = Platform.getBundle(bundleInfo.getSymbolicName());
				if (bundle != null) {
					if (bundle.getLocation().equals(location) && bundle.getVersion().equals(Version.parseVersion(bundleInfo.getVersion()))) {
						// Same bundle exists
						continue;
					}
					bundle.uninstall();
					JavaLanguageServerPlugin.logInfo("Uninstalled " + bundle.getLocation());
					toRefresh.add(bundle);
				}
				bundle = context.installBundle(location);
				JavaLanguageServerPlugin.logInfo("Installed " + bundle.getLocation());
				installedBundles.put(bundle.getSymbolicName(), bundle);
				bundlesToStart.add(bundle);

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

	private static void refreshBundles(Set<Bundle> toRefresh, FrameworkWiring frameworkWiring) {
		if (!toRefresh.isEmpty()) {
			JavaLanguageServerPlugin.logInfo("Refresh the bundles");
			final CountDownLatch latch = new CountDownLatch(1);
			frameworkWiring.refreshBundles(toRefresh, new FrameworkListener() {
				@Override
				public void frameworkEvent(FrameworkEvent event) {
					latch.countDown();
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
					if (StringUtils.isNotBlank(symbolicName) && symbolicName.indexOf(';') >= 0) {
						symbolicName = symbolicName.substring(0, symbolicName.indexOf(';'));
					}
					return new BundleInfo(bundleVersion, symbolicName);
				}
			}
		}
		return null;
	}

	// Copied from {@link org.eclipse.osgi.container.ModuleResolver#isSingleton(ModuleRevision)}
	static boolean isSingleton(ModuleRevision revision) {
		List<Capability> identities = revision.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
		if (identities.isEmpty()) {
			return false;
		}
		return "true".equals(identities.get(0).getDirectives().get(IdentityNamespace.CAPABILITY_SINGLETON_DIRECTIVE)); //$NON-NLS-1$
	}
}
