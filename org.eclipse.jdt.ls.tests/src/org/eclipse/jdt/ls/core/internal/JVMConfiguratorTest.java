/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallChangedListener;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 */
@RunWith(MockitoJUnitRunner.class)
public class JVMConfiguratorTest extends AbstractProjectsManagerBasedTest {

	private IVMInstall originalVm;

	@Before
	public void setup() {
		originalVm = JavaRuntime.getDefaultVMInstall();
	}

	@Override
	@After
	public void cleanUp() throws CoreException {
		JavaRuntime.setDefaultVMInstall(originalVm, new NullProgressMonitor());
	}

	@Test
	public void testDefaultVM() throws CoreException {
		Preferences prefs = new Preferences();
		String javaHome = new File(TestVMType.getFakeJDKsLocation(), "9").getAbsolutePath();
		prefs.setJavaHome(javaHome);
		boolean changed = JVMConfigurator.configureDefaultVM(prefs);
		IVMInstall newDefaultVM = JavaRuntime.getDefaultVMInstall();
		assertTrue("A VM hasn't been changed", changed);
		assertNotEquals(originalVm, newDefaultVM);
		assertEquals("9", newDefaultVM.getId());
	}

	public void testPreviewFeatureSettings() throws Exception {

		IVMInstallChangedListener jvmConfigurator = new JVMConfigurator();
		try {
			JavaRuntime.addVMInstallChangedListener(jvmConfigurator);
			IJavaProject defaultProject = newDefaultProject();
			IJavaProject randomProject = newEmptyProject();

			assertEquals(JavaCore.DISABLED, defaultProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
			assertEquals(JavaCore.DISABLED, randomProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));

			TestVMType.setTestJREAsDefault("12");

			assertEquals(JavaCore.ENABLED, defaultProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
			assertEquals(JavaCore.ENABLED, randomProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));

			TestVMType.setTestJREAsDefault("1.8");

			assertEquals(JavaCore.DISABLED, defaultProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
			assertEquals(JavaCore.DISABLED, randomProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));

		} finally {
			JavaRuntime.removeVMInstallChangedListener(jvmConfigurator);
		}

	}
}
