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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.BundleRequestParams;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class BundleHandlerTest extends AbstractCompilationUnitBasedTest {

	@Override
	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
	}

	@Test
	public void testLoadUnload() {
		String bundleLocation = getBundle();
		BundleRequestParams params = new BundleRequestParams(new String[] { "install", bundleLocation });

		BundleHandler handler = new BundleHandler();
		long bundleId = (long) handler.handleBundle(params);

		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		Bundle installedBundle = context.getBundle(bundleId);
		assertNotNull(installedBundle);
		assertEquals(installedBundle.getState(), Bundle.ACTIVE);

		bundleLocation = installedBundle.getLocation();

		params = new BundleRequestParams(new String[] { "uninstall", bundleLocation });
		handler.handleBundle(params);
		Bundle uninstalledBundle = context.getBundle(bundleLocation);
		assertNull(uninstalledBundle);
	}

	private String getBundle() {
		return (new File("testresources", "org.eclipse.jdt.ls.debug-0.3.0-SNAPSHOT.jar")).getAbsolutePath();
	}
}
