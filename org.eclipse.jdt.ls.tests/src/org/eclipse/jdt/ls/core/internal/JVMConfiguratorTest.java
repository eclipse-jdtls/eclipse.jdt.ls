/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallChangedListener;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
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
public class JVMConfiguratorTest extends AbstractInvisibleProjectBasedTest {

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

	@Test
	public void testPreviewFeatureSettings() throws Exception {
		IVMInstallChangedListener jvmConfigurator = new JVMConfigurator();
		try {
			JavaRuntime.addVMInstallChangedListener(jvmConfigurator);
			IJavaProject defaultProject = newDefaultProject();
			IProject invisibleProject = copyAndImportFolder("singlefile/java13", "foo/bar/Foo.java");
			IJavaProject randomProject = JavaCore.create(invisibleProject);

			assertComplianceAndPreviewSupport(defaultProject, "1.8", false);
			assertComplianceAndPreviewSupport(randomProject, "1.8", false);

			String latest = JavaCore.latestSupportedJavaVersion();
			TestVMType.setTestJREAsDefault(latest);

			assertComplianceAndPreviewSupport(defaultProject, latest, true);
			assertComplianceAndPreviewSupport(randomProject, latest, true);

			TestVMType.setTestJREAsDefault("12");

			assertComplianceAndPreviewSupport(defaultProject, "12", false);
			assertComplianceAndPreviewSupport(randomProject, "12", false);

			TestVMType.setTestJREAsDefault("1.8");

			assertComplianceAndPreviewSupport(defaultProject, "1.8", false);
			assertComplianceAndPreviewSupport(randomProject, "1.8", false);


		} finally {
			JavaRuntime.removeVMInstallChangedListener(jvmConfigurator);
		}
	}

	private void assertComplianceAndPreviewSupport(IJavaProject javaProject, String compliance, boolean previewEnabled) {
		assertEquals(previewEnabled ? JavaCore.ENABLED : JavaCore.DISABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true));
		assertEquals(compliance, javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true));
	}
}
