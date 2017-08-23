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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.handlers.BundleUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class BundleUtilsTest extends AbstractProjectsManagerBasedTest {

	private static final String REFERENCE_PREFIX = "reference:";


	@Test
	public void testLoadUnload() {
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("bundles", new ArrayList<>(Arrays.asList(getBundle())));

		BundleUtils.loadBundles(options);

		String bundleLocation = getBundleLocation(getBundle(), true);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleLocation);
		assertNotNull(installedBundle);
		assertEquals(installedBundle.getState(), Bundle.ACTIVE);
	}

	private String getBundle() {
		return (new File("testresources", "testbundle-0.3.0-SNAPSHOT.jar")).getAbsolutePath();
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
}
