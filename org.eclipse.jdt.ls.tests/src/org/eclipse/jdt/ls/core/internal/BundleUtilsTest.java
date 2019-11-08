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

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

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

		String bundleLocation = getBundleLocation(getBundle(), true);
		Bundle installedBundle = context.getBundle(bundleLocation);

		String skippedBundleLocation = getBundleLocation(getAnotherBundle(), true);
		Bundle skippedBundle = context.getBundle(skippedBundleLocation);
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
			installedBundle.uninstall();
		}
	}

	@Test
	public void testLoadMultipleNonSongletonBundles() throws Exception {
		// First, we load a bundle depends on dependency bundle
		String bundleAPath = getBundle("testresources", "nonsingleton_0.0.1.201911081703.jar");
		String bundleBPath = getBundle("testresources/path with whitespace", "nonsingleton_0.0.1.201911081703.jar");
		String bundleCPath = getBundle("testresources", "nonsingleton_0.0.2.201911081702.jar");

		String bundleALocation = getBundleLocation(bundleAPath, true);
		String bundleBLocation = getBundleLocation(bundleBPath, true);
		String bundleCLocation = getBundleLocation(bundleCPath, true);

		BundleUtils.loadBundles(Arrays.asList(bundleAPath, bundleBPath, bundleCPath));

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();

		Bundle bundleA = context.getBundle(bundleALocation);
		Bundle bundleB = context.getBundle(bundleBLocation);
		Bundle bundleC = context.getBundle(bundleCLocation);

		try {
			assertTrue(bundleA.getState() == Bundle.STARTING || bundleA.getState() == Bundle.ACTIVE);
			// non singleton bundle with same symbolic name and version should not be installed
			assertNull(bundleB);
			assertTrue(bundleC.getState() == Bundle.STARTING || bundleC.getState() == Bundle.ACTIVE);
		} finally {
			bundleA.uninstall();
			bundleC.uninstall();
		}
	}

	@Test
	public void testLoadSameSingletonBundleFromDifferentLocation() throws Exception {
		// First, we load a bundle depends on dependency bundle
		String oldBundlePath = getBundle("testresources/extension-with-dependency-0.0.1/", "jdt.ls.extension.with.dependency_0.0.1.jar");
		String oldDependencyPath = getBundle("testresources/extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");
		BundleUtils.loadBundles(Arrays.asList(oldBundlePath, oldDependencyPath));
		String oldBundleLocation = getBundleLocation(oldBundlePath, true);
		String oldDependencyLocation = getBundleLocation(oldDependencyPath, true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle oldBundle = context.getBundle(oldBundleLocation);
		Bundle oldDependency = context.getBundle(oldDependencyLocation);

		assertNotNull(oldBundle);

		assertTrue(oldBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
		assertTrue(oldDependency.getState() == Bundle.ACTIVE);
		oldBundle.loadClass("jdt.ls.extension.with.dependency.Activator");
		assertEquals(oldBundle.getState(), Bundle.ACTIVE);

		// Now we load bundles from another location, this may happen when the LS extensions get updated.
		String newBundlePath = getBundle("testresources/extension-with-dependency-0.0.2/", "jdt.ls.extension.with.dependency_0.0.2.jar");
		String newDependencyPath = getBundle("testresources", "dependency_0.0.2.201911081538.jar");
		BundleUtils.loadBundles(Arrays.asList(newBundlePath, newDependencyPath));

		String newBundleLocation = getBundleLocation(newBundlePath, true);
		String newDependencyLocation = getBundleLocation(newDependencyPath, true);

		Bundle newBundle = context.getBundle(newBundleLocation);
		Bundle newDependency = context.getBundle(newDependencyLocation);

		try {
			assertTrue(oldBundle.getState() == Bundle.UNINSTALLED);
			assertTrue(oldDependency.getState() == Bundle.UNINSTALLED);

			assertTrue(newBundle.getState() == Bundle.STARTING || oldBundle.getState() == Bundle.ACTIVE);
			assertTrue(newDependency.getState() == Bundle.ACTIVE);
			newBundle.loadClass("jdt.ls.extension.with.dependency.Activator");
			assertEquals(newBundle.getState(), Bundle.ACTIVE);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			newBundle.uninstall();
			newDependency.uninstall();
		}
	}

	@Test
	public void testLoadSameBundleWithDifferentVersionMultipleTimes() throws Exception {
		String oldDependencyPath = getBundle("testresources/extension-with-dependency-0.0.1", "dependency_0.0.1.201911081535.jar");
		BundleUtils.loadBundles(Arrays.asList(oldDependencyPath));
		String oldDependencyLocation = getBundleLocation(oldDependencyPath, true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle oldDependencyBundle = context.getBundle(oldDependencyLocation);

		assertNotNull(oldDependencyBundle);
		assertTrue(oldDependencyBundle.getState() == Bundle.ACTIVE);

		// Now we load two different version of bundles
		// this may happen when different extensions which required the same bundle are getting loaded.
		String newDependencyAPath = getBundle("testresources", "dependency_0.0.2.201911081538.jar");
		String newDependencyBPath = getBundle("testresources", "dependency_0.0.1.201911081535.jar");

		String newDependencyALocation = getBundleLocation(newDependencyAPath, true);
		String newDependencyBLocation = getBundleLocation(newDependencyBPath, true);
		BundleUtils.loadBundles(Arrays.asList(newDependencyAPath, newDependencyBPath));

		Bundle newBundleA = context.getBundle(newDependencyALocation);
		Bundle newBundleB = context.getBundle(newDependencyBLocation);
		try {
			assertTrue(oldDependencyBundle.getState() == Bundle.UNINSTALLED);
			assertNotNull(newBundleA);
			assertTrue(newBundleA.getState() == Bundle.ACTIVE);
			// skipped newBundleB since this is a singleton bundle
			assertNull(newBundleB);
		} finally {
			// Uninstall the bundle to clean up the testing bundle context.
			newBundleA.uninstall();
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
}