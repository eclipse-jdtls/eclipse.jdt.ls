/*******************************************************************************
 * Copyright (c) 2017, 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.handlers.BundleUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class BundleUtilsTest extends AbstractProjectsManagerBasedTest {

	private static final String EXTENSIONPOINT_ID = "testbundle.ext";

	@Test
	public void testLoad() throws Exception {
		loadBundles(Arrays.asList(getBundle()));
		String bundleLocation = getBundleLocation(getBundle(), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);

			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			// active the bundle by loading a class. This is needed
			// because test bundles have lazy activation policy
			installedBundle.loadClass("testbundle.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			String extResult = getBundleExtensionResult();
			assertEquals("EXT_TOSTRING", extResult);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			installedBundle.uninstall();
		}
	}

	@Test
	public void testLoadWithoutLoadClass() throws Exception {
		loadBundles(Arrays.asList(getBundle()));
		String bundleLocation = getBundleLocation(getBundle(), true);
		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);
			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			String extResult = getBundleExtensionResult();
			assertEquals("EXT_TOSTRING", extResult);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			installedBundle.uninstall();
		}
	}

	@Test
	public void testLoadAndUpdate() throws Exception {
		loadBundles(Arrays.asList(getBundle()));
		String bundleLocation = getBundleLocation(getBundle(), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);

			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("testbundle.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			String extResult = getBundleExtensionResult();
			assertEquals("EXT_TOSTRING", extResult);
			loadBundles(Arrays.asList(getBundle("testresources", "testbundle-0.6.0-SNAPSHOT.jar")));
			bundleLocation = getBundleLocation(getBundle("testresources", "testbundle-0.6.0-SNAPSHOT.jar"), true);

			installedBundle = context.getBundle(bundleLocation);
			assertNotNull(installedBundle);
			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("testbundle.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			extResult = getBundleExtensionResult();
			assertEquals("EXT_TOSTRING_0.6.0", extResult);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			installedBundle.uninstall();
		}
	}

	@Test
	public void testLoadWithWhitespace() throws Exception {
		loadBundles(Arrays.asList(getBundle("testresources/path with whitespace", "bundle with whitespace.jar")));
		String bundleLocation = getBundleLocation(getBundle("testresources/path with whitespace", "bundle with whitespace.jar"), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);

			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("testbundle.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			String extResult = getBundleExtensionResult();
			assertEquals("EXT_TOSTRING", extResult);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			installedBundle.uninstall();
		}
	}

	@Test
	public void testLoadSameBundleMultipleTimes() throws Exception {
		loadBundles(Arrays.asList(getBundle(), getAnotherBundle()));

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();

		String skippedBundleLocation = getBundleLocation(getBundle(), true);
		Bundle skippedBundle = context.getBundle(skippedBundleLocation);

		String bundleLocation = getBundleLocation(getAnotherBundle(), true);
		Bundle installedBundle = context.getBundle(bundleLocation);
		try {
			assertNotNull(installedBundle);
			assertNull(skippedBundle);

			assertTrue(installedBundle.getState() == Bundle.STARTING || installedBundle.getState() == Bundle.ACTIVE);
			installedBundle.loadClass("testbundle.Activator");
			assertEquals(installedBundle.getState(), Bundle.ACTIVE);

			String extResult = getBundleExtensionResult();
			assertEquals("EXT_TOSTRING", extResult);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			uninstallBundles(Arrays.asList(installedBundle, skippedBundle));
		}
	}

	@Test
	public void testLoadMultipleNonSingletonBundles() throws Exception {
		Bundle bundleA = null;
		Bundle bundleB = null;
		Bundle bundleC = null;

		try {
			// First, we load a bundle depends on dependency bundle
			String bundleAPath = getBundle("testresources", "nonsingleton_0.0.1.201911081703.jar");
			String bundleBPath = getBundle("testresources/path with whitespace", "nonsingleton_0.0.1.201911081703.jar");
			String bundleCPath = getBundle("testresources", "nonsingleton_0.0.2.201911081702.jar");

			String bundleALocation = getBundleLocation(bundleAPath, true);
			String bundleBLocation = getBundleLocation(bundleBPath, true);
			String bundleCLocation = getBundleLocation(bundleCPath, true);

			BundleUtils.loadBundles(Arrays.asList(bundleAPath, bundleBPath, bundleCPath));

			BundleContext context = JavaLanguageServerPlugin.getBundleContext();

			bundleA = context.getBundle(bundleALocation);
			bundleB = context.getBundle(bundleBLocation);
			bundleC = context.getBundle(bundleCLocation);

			assertNull(bundleA);
			// For the same version bundle with different location, later comes wins.
			assertTrue(bundleB.getState() == Bundle.STARTING || bundleB.getState() == Bundle.ACTIVE);
			assertTrue(bundleC.getState() == Bundle.STARTING || bundleC.getState() == Bundle.ACTIVE);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			uninstallBundles(Arrays.asList(bundleA, bundleB, bundleC));
		}
	}

	@Test
	public void testLoadSameSingletonBundleFromDifferentLocation() throws Exception {
		Bundle oldBundle = null;
		Bundle oldDependency = null;
		Bundle newBundle = null;
		Bundle newDependency = null;

		try {
			// First, we load a bundle depends on dependency bundle
			String oldBundlePath = getBundle("testresources/extension-with-dependency-0.0.1", "jdt.ls.extension.with.dependency_0.0.1.jar");
			String oldDependencyPath = getBundle("testresources/extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");
			BundleUtils.loadBundles(Arrays.asList(oldBundlePath, oldDependencyPath));
			String oldBundleLocation = getBundleLocation(oldBundlePath, true);
			String oldDependencyLocation = getBundleLocation(oldDependencyPath, true);

			BundleContext context = JavaLanguageServerPlugin.getBundleContext();
			oldBundle = context.getBundle(oldBundleLocation);
			oldDependency = context.getBundle(oldDependencyLocation);

			assertNotNull(oldBundle);

			assertTrue(oldBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
			assertTrue(oldDependency.getState() == Bundle.ACTIVE);
			oldBundle.loadClass("jdt.ls.extension.with.dependency.Activator");
			assertEquals(oldBundle.getState(), Bundle.ACTIVE);

			// Now we load bundles from another location, this may happen when the LS extensions get updated.
			String newBundlePath = getBundle("testresources/extension-with-dependency-0.0.2", "jdt.ls.extension.with.dependency_0.0.2.jar");
			String newDependencyPath = getBundle("testresources/extension-with-dependency-0.0.2", "dependency_0.0.2.201911081538.jar");
			BundleUtils.loadBundles(Arrays.asList(newBundlePath, newDependencyPath));

			String newBundleLocation = getBundleLocation(newBundlePath, true);
			String newDependencyLocation = getBundleLocation(newDependencyPath, true);

			newBundle = context.getBundle(newBundleLocation);
			newDependency = context.getBundle(newDependencyLocation);

			assertTrue(oldBundle.getState() == Bundle.UNINSTALLED);
			assertTrue(oldDependency.getState() == Bundle.UNINSTALLED);

			assertTrue(newBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
			assertTrue(newDependency.getState() == Bundle.ACTIVE);
			newBundle.loadClass("jdt.ls.extension.with.dependency.Activator");
			assertEquals(newBundle.getState(), Bundle.ACTIVE);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			uninstallBundles(Arrays.asList(oldBundle, oldDependency, newBundle, newDependency));
		}
	}

	@Test
	public void testUpdateExtensionsDependingOnTheSameBundle() throws Exception {
		Bundle oldBundle = null;
		Bundle oldDependency = null;
		Bundle anotherBundle = null;
		Bundle newBundle = null;
		Bundle newDependency = null;

		try {
			// First, we load a bundle depends on dependency bundle
			String oldBundlePath = getBundle("testresources/extension-with-dependency-0.0.1", "jdt.ls.extension.with.dependency_0.0.1.jar");
			String oldDependencyPath = getBundle("testresources/extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");
			String anotherBundlePath = getBundle("testresources/another-extension-with-dependency-0.0.1", "jdt.ls.another.extension.with.dependency_0.0.1.jar");

			BundleUtils.loadBundles(Arrays.asList(oldBundlePath, oldDependencyPath, anotherBundlePath));
			String oldBundleLocation = getBundleLocation(oldBundlePath, true);
			String oldDependencyLocation = getBundleLocation(oldDependencyPath, true);
			String anotherBundleLocation = getBundleLocation(anotherBundlePath, true);

			BundleContext context = JavaLanguageServerPlugin.getBundleContext();
			oldBundle = context.getBundle(oldBundleLocation);
			oldDependency = context.getBundle(oldDependencyLocation);
			anotherBundle = context.getBundle(anotherBundleLocation);

			assertNotNull(oldBundle);

			assertTrue(oldBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
			assertTrue(oldDependency.getState() == Bundle.ACTIVE);
			assertTrue(anotherBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
			oldBundle.loadClass("jdt.ls.extension.with.dependency.Activator");
			assertEquals(oldBundle.getState(), Bundle.ACTIVE);
			anotherBundle.loadClass("jdt.ls.another.extension.with.dependency.Activator");
			assertEquals(anotherBundle.getState(), Bundle.ACTIVE);

			// Now we load bundles from another location, this may happen when the LS extensions get updated.
			String newBundlePath = getBundle("testresources/extension-with-dependency-0.0.2", "jdt.ls.extension.with.dependency_0.0.2.jar");
			String newDependencyPath = getBundle("testresources/extension-with-dependency-0.0.2", "dependency_0.0.2.201911081538.jar");
			BundleUtils.loadBundles(Arrays.asList(newBundlePath, newDependencyPath));

			String newBundleLocation = getBundleLocation(newBundlePath, true);
			String newDependencyLocation = getBundleLocation(newDependencyPath, true);

			newBundle = context.getBundle(newBundleLocation);
			newDependency = context.getBundle(newDependencyLocation);

			assertTrue(oldBundle.getState() == Bundle.UNINSTALLED);
			assertTrue(oldDependency.getState() == Bundle.UNINSTALLED);

			assertTrue(newBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
			assertTrue(newDependency.getState() == Bundle.ACTIVE);
			newBundle.loadClass("jdt.ls.extension.with.dependency.Activator");
			assertEquals(newBundle.getState(), Bundle.ACTIVE);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			uninstallBundles(Arrays.asList(oldBundle, oldDependency, anotherBundle, newBundle, newDependency));
		}
	}

	@Test
	public void testLoadSameBundleWithDifferentVersionMultipleTimes() throws Exception {
		Bundle oldDependencyBundle = null;
		Bundle newBundleA = null;
		Bundle newBundleB = null;
		try {
			String oldDependencyPath = getBundle("testresources/extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");
			BundleUtils.loadBundles(Arrays.asList(oldDependencyPath));
			String oldDependencyLocation = getBundleLocation(oldDependencyPath, true);

			BundleContext context = JavaLanguageServerPlugin.getBundleContext();
			oldDependencyBundle = context.getBundle(oldDependencyLocation);

			assertNotNull(oldDependencyBundle);
			assertTrue(oldDependencyBundle.getState() == Bundle.ACTIVE);

			// Now we load two different version of bundles
			// this may happen when different extensions which required the same bundle are getting loaded.
			String newDependencyAPath = getBundle("testresources", "dependency_0.0.2.201911081538.jar");
			String newDependencyBPath = getBundle("testresources/another-extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");

			String newDependencyALocation = getBundleLocation(newDependencyAPath, true);
			String newDependencyBLocation = getBundleLocation(newDependencyBPath, true);
			BundleUtils.loadBundles(Arrays.asList(newDependencyAPath, newDependencyBPath));

			newBundleA = context.getBundle(newDependencyALocation);
			newBundleB = context.getBundle(newDependencyBLocation);

			assertTrue(oldDependencyBundle.getState() == Bundle.UNINSTALLED);
			assertNull(newBundleA);
			assertNotNull(newBundleB);
			assertTrue(newBundleB.getState() == Bundle.ACTIVE);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			uninstallBundles(Arrays.asList(oldDependencyBundle, newBundleA, newBundleB));
		}
	}

	@Test
	public void testLoadMultipleExtensionsRequiringSameBundleButDifferentUrl() throws Exception {
		Bundle extensionBundleA = null;
		Bundle extensionBundleB = null;
		Bundle dependencyA = null;
		Bundle dependencyB = null;

		try {
			String extensionBundleAPath = getBundle("testresources/extension-with-dependency-0.0.1", "jdt.ls.extension.with.dependency_0.0.1.jar");
			String dependencyAPath = getBundle("testresources/extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");
			String extensionBundleBPath = getBundle("testresources/another-extension-with-dependency-0.0.1", "jdt.ls.another.extension.with.dependency_0.0.1.jar");
			String dependencyBPath = getBundle("testresources/another-extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");

			BundleUtils.loadBundles(Arrays.asList(extensionBundleAPath, dependencyAPath, extensionBundleBPath, dependencyBPath));
			String extensionBundleALocation = getBundleLocation(extensionBundleAPath, true);
			String dependencyALocation = getBundleLocation(dependencyAPath, true);
			String extensionBundleBLocation = getBundleLocation(extensionBundleBPath, true);
			String dependencyBLocation = getBundleLocation(dependencyBPath, true);

			BundleContext context = JavaLanguageServerPlugin.getBundleContext();
			extensionBundleA = context.getBundle(extensionBundleALocation);
			dependencyA = context.getBundle(dependencyALocation);
			extensionBundleB = context.getBundle(extensionBundleBLocation);
			dependencyB = context.getBundle(dependencyBLocation);

			assertTrue(extensionBundleA.getState() == Bundle.STARTING || extensionBundleA.getState() == Bundle.ACTIVE);
			assertTrue(extensionBundleB.getState() == Bundle.STARTING || extensionBundleB.getState() == Bundle.ACTIVE);
			extensionBundleA.loadClass("jdt.ls.extension.with.dependency.Activator");
			assertEquals(extensionBundleA.getState(), Bundle.ACTIVE);
			extensionBundleB.loadClass("jdt.ls.another.extension.with.dependency.Activator");
			assertEquals(extensionBundleB.getState(), Bundle.ACTIVE);

			// Bundles with same version but different URL, only the later comes one will be loaded
			assertNull(dependencyA);
			assertTrue(dependencyB.getState() == Bundle.ACTIVE);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			uninstallBundles(Arrays.asList(extensionBundleA, extensionBundleB, dependencyA, dependencyB));
		}
	}

	@Test(expected = CoreException.class)
	public void testLoadThrowCoreException() throws Exception {
		BundleUtils.loadBundles(Arrays.asList(new String[] { "Fakedlocation" }));
	}

	private String getBundle() {
		return getBundle("testresources", "testbundle-0.3.0-SNAPSHOT.jar");
	}

	private String getAnotherBundle() {
		return getBundle("testresources/path with whitespace", "testbundle-0.3.0-SNAPSHOT.jar");
	}

	private String getBundleExtensionResult() {
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSIONPOINT_ID);
		assertEquals("Expect one extension point", 1, elements.length);
		final String[] resultValues = new String[] { "" };
		for (IConfigurationElement e : elements) {
			if ("java".equals(e.getAttribute("type"))) {
				SafeRunner.run(new ISafeRunnable() {
					@Override
					public void run() throws Exception {
						final Object extInstance = e.createExecutableExtension("class");
						resultValues[0] = extInstance.toString();
					}

					@Override
					public void handleException(Throwable ex) {
						IStatus status = new Status(IStatus.ERROR, IConstants.PLUGIN_ID, IStatus.OK, "Error in JDT Core during launching debug server", ex); //$NON-NLS-1$
						JavaLanguageServerPlugin.log(status);
					}
				});
			}
		}
		return resultValues[0];
	}

	private void uninstallBundles(Collection<Bundle> bundles) {
		for (Bundle bundle : bundles) {
			if (bundle != null && bundle.getState() != Bundle.UNINSTALLED) {
				try {
					bundle.uninstall();
				} catch (BundleException e) {
					// ignore
				}
			}
		}
	}
}