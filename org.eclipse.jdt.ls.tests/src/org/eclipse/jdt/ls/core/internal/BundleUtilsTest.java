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

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
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

	private static final String REFERENCE_PREFIX = "reference:";

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

	@Test(expected = CoreException.class)
	public void testLoadThrowCoreException() throws Exception {
		BundleUtils.loadBundles(Arrays.asList(new String[] { "Fakedlocation" }));
	}

	private void loadBundles(List<String> bundles) throws Exception {
		RegistryChangeListener listener = new RegistryChangeListener(false);
		try {
			Platform.getExtensionRegistry().addRegistryChangeListener(listener);
			BundleUtils.loadBundles(bundles);
			while (!listener.isChanged()) {
				Thread.sleep(100);
			}
		} finally {
			Platform.getExtensionRegistry().removeRegistryChangeListener(listener);
		}
	}

	private String getBundle() {
		return getBundle("testresources", "testbundle-0.3.0-SNAPSHOT.jar");
	}

	private String getAnotherBundle() {
		return getBundle("testresources/path with whitespace", "testbundle-0.3.0-SNAPSHOT.jar");
	}

	private String getBundle(String folder, String bundleName) {
		return (new File(folder, bundleName)).getAbsolutePath();
	}

	private String getBundleLocation(String location, boolean useReference) {
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

	private class RegistryChangeListener implements IRegistryChangeListener {
		private boolean changed;

		private RegistryChangeListener(boolean changed) {
			this.setChanged(changed);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.core.runtime.IRegistryChangeListener#registryChanged(org.eclipse.core.runtime.IRegistryChangeEvent)
		 */
		@Override
		public void registryChanged(IRegistryChangeEvent event) {
			setChanged(true);
		}

		public boolean isChanged() {
			return changed;
		}

		public void setChanged(boolean changed) {
			this.changed = changed;
		}
	}
}
