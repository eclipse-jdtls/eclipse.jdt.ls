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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.IVMInstallChangedListener;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.Bundle;

/**
 * @author Fred Bricon
 */
@RunWith(MockitoJUnitRunner.class)
public class JVMConfiguratorTest extends AbstractInvisibleProjectBasedTest {

	private static final String ENVIRONMENT_NAME = "JavaSE-11";
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
	public void testJVM() throws Exception {
		try {
			Preferences prefs = new Preferences();
			Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
			URL url = FileLocator.toFileURL(bundle.getEntry("/fakejdk2/11a"));
			File file = URIUtil.toFile(URIUtil.toURI(url));
			String path = file.getAbsolutePath();
			String javadoc = "file:///javadoc";
			Set<RuntimeEnvironment> runtimes = new HashSet<>();
			RuntimeEnvironment runtime = new RuntimeEnvironment();
			runtime.setPath(path);
			runtime.setName(ENVIRONMENT_NAME);
			runtime.setJavadoc(javadoc);
			runtime.setDefault(true);
			assertTrue(runtime.isValid());
			runtimes.add(runtime);
			prefs.setRuntimes(runtimes);
			file = runtime.getInstallationFile();
			assertTrue(file != null && file.isDirectory());
			IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
			IStatus status = installType.validateInstallLocation(file);
			assertTrue(status.toString(), status.isOK());
			boolean changed = JVMConfigurator.configureJVMs(prefs);
			assertTrue("A VM hasn't been changed", changed);
			JobHelpers.waitForJobsToComplete();
			IVMInstall vm = JVMConfigurator.findVM(runtime.getInstallationFile(), ENVIRONMENT_NAME);
			assertNotNull(vm);
			assertTrue(vm instanceof IVMInstall2);
			String version = ((IVMInstall2) vm).getJavaVersion();
			assertTrue(version.startsWith(JavaCore.VERSION_11));
			StandardVMType svt = (StandardVMType) vm.getVMInstallType();
			LibraryLocation[] libs = vm.getLibraryLocations();
			assertNotNull(libs);
			for (LibraryLocation lib : libs) {
				assertEquals(runtime.getJavadocURL(), lib.getJavadocLocation());
			}
			IVMInstall newDefaultVM = JavaRuntime.getDefaultVMInstall();
			assertNotEquals(originalVm, newDefaultVM);
			assertEquals(vm, newDefaultVM);
			IExecutionEnvironment environment = JVMConfigurator.getExecutionEnvironment(ENVIRONMENT_NAME);
			assertNotNull(environment);
			assertEquals(vm, environment.getDefaultVM());
		} finally {
			IVMInstall vm = JVMConfigurator.findVM(null, ENVIRONMENT_NAME);
			if (vm != null) {
				vm.getVMInstallType().disposeVMInstall(vm.getId());
			}
		}
		IVMInstall vm = JVMConfigurator.findVM(null, ENVIRONMENT_NAME);
		assertNull(vm);
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
