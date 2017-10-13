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

import java.io.File;
import java.net.MalformedURLException;
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
import org.osgi.framework.BundleException;

public class BundleUtilsTest extends AbstractProjectsManagerBasedTest {

	private static final String REFERENCE_PREFIX = "reference:";

	private static final String EXTENSIONPOINT_ID = "testbundle.ext";

	@Test
	public void testLoad() throws BundleException, CoreException {
		BundleUtils.loadBundles(Arrays.asList(getBundle()));
		String bundleLocation = getBundleLocation(getBundle(), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		assertNotNull(installedBundle);
		assertEquals(installedBundle.getState(), Bundle.STARTING);

		String extResult = getBundleExtensionResult();
		assertEquals("EXT_TOSTRING", extResult);
		// Uninstall the bundle to clean up the testing bundle context.
		installedBundle.uninstall();
	}

	@Test
	public void testLoadAndUpdate() throws BundleException, CoreException {
		BundleUtils.loadBundles(Arrays.asList(getBundle()));
		String bundleLocation = getBundleLocation(getBundle(), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		assertNotNull(installedBundle);
		assertEquals(installedBundle.getState(), Bundle.STARTING);

		String extResult = getBundleExtensionResult();
		assertEquals("EXT_TOSTRING", extResult);

		BundleUtils.loadBundles(Arrays.asList(getBundle("testresources", "testbundle-0.6.0-SNAPSHOT.jar")));
		bundleLocation = getBundleLocation(getBundle("testresources", "testbundle-0.6.0-SNAPSHOT.jar"), true);

		installedBundle = context.getBundle(bundleLocation);
		assertNotNull(installedBundle);
		assertEquals(installedBundle.getState(), Bundle.STARTING);

		extResult = getBundleExtensionResult();
		assertEquals("EXT_TOSTRING_0.6.0", extResult);

		// Uninstall the bundle to clean up the testing bundle context.
		installedBundle.uninstall();
	}

	@Test
	public void testLoadWithWhitespace() throws BundleException, CoreException {
		BundleUtils.loadBundles(Arrays.asList(getBundle("testresources/path with whitespace", "bundle with whitespace.jar")));
		String bundleLocation = getBundleLocation(getBundle("testresources/path with whitespace", "bundle with whitespace.jar"), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		assertNotNull(installedBundle);
		assertEquals(installedBundle.getState(), Bundle.STARTING);

		String extResult = getBundleExtensionResult();
		assertEquals("EXT_TOSTRING", extResult);
		// Uninstall the bundle to clean up the testing bundle context.
		installedBundle.uninstall();
	}

	@Test(expected = CoreException.class)
	public void testLoadThrowCoreException() throws BundleException, CoreException {
		BundleUtils.loadBundles(Arrays.asList(new String[] { "Fakedlocation" }));
	}

	private String getBundle() {
		return (new File("testresources", "testbundle-0.3.0-SNAPSHOT.jar")).getAbsolutePath();
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
						IStatus status = new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.OK, "Error in JDT Core during launching debug server", ex); //$NON-NLS-1$
						JavaLanguageServerPlugin.log(status);
					}
				});
			}
		}
		return resultValues[0];
	}
}
