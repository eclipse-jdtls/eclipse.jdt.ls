/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javafx;

import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.ls.core.internal.JVMConfigurator;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

public class JavaFXTest extends AbstractProjectsManagerBasedTest {

	private static final String JAVA_SE_8 = "JavaSE-1.8";
	private static final String JAVA_SE_17 = "JavaSE-17";

	/**
	 * Test requires Java 8 in toolchains.xml
	 */
	@Test
	public void testJavaFX() throws Exception {
		IVMInstall defaultJRE = JavaRuntime.getDefaultVMInstall();
		String name = "java8fx";
		IProject project = null;
		IExecutionEnvironment java8env = JVMConfigurator.getExecutionEnvironment(JAVA_SE_8);
		IVMInstall oldJavaVm = java8env.getDefaultVM();
		try {
			IVMInstall java8vm = null;
			IVMInstall java8DefaultVm = null;
			if (java8env != null) {
				java8DefaultVm = java8env.getDefaultVM();
				IVMInstall[] compatibleVms = java8env.getCompatibleVMs();
				for (IVMInstall vm : compatibleVms) {
					if (vm.getVMInstallType().getName().startsWith("TestVMInstall-")) {
						continue;
					}
					if (java8env.isStrictlyCompatible(vm)) {
						java8vm = vm;
						java8env.setDefaultVM(java8vm);
						break;
					}
				}
			}
			Assume.assumeThat("JavaSE-1.8 VM is not found, skipping test", java8vm, IsNull.notNullValue());
			// Import JavaFX project and checks it compiles without errors
			JavaRuntime.setDefaultVMInstall(java8vm, monitor, true);
			importProjects("eclipse/" + name);
			project = getProject(name);
			assertNoErrors(project);
			JavaRuntime.setDefaultVMInstall(defaultJRE, monitor, true);
			if (java8env != null) {
				java8env.setDefaultVM(java8DefaultVm);
			}
			JobHelpers.waitForJobsToComplete();
			List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
			assertNotEquals(0, errors.size());
			String errorsStr = ResourceUtils.toString(errors);
			assertTrue("Unexpected errors:\n " + errorsStr, errorsStr.contains("javafx cannot be resolved"));
		} finally {
			JavaRuntime.setDefaultVMInstall(defaultJRE, monitor, true);
			IExecutionEnvironment environment = JVMConfigurator.getExecutionEnvironment(JAVA_SE_17);
			if (environment != null) {
				environment.setDefaultVM(defaultJRE);
			}
			java8env.setDefaultVM(oldJavaVm);
		}
	}

	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		TestVMType.setTestJREAsDefault("17");
		// Copied from org.eclipse.jdt.core.tests.model.ClasspathInitializerTests.tearDown()
		// Cleanup caches
		JavaModelManager manager = JavaModelManager.getJavaModelManager();
		manager.containers = new HashMap<>(5);
		manager.variables = new HashMap<>(5);
		JobHelpers.waitForJobsToComplete();
	}

}
