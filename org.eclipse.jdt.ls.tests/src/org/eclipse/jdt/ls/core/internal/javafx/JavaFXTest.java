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

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.ls.core.internal.JVMConfigurator;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.RuntimeEnvironment;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.hamcrest.core.IsNull;
import org.junit.Assume;
import org.junit.Test;

public class JavaFXTest extends AbstractProjectsManagerBasedTest {

	private static final String JAVA_SE_8 = "JavaSE-1.8";

	/**
	 * Test musts run with the -Djdkfx8.home=/path/to/jdk8+fx System property, or it
	 * will be skipped.
	 */
	@Test
	public void testJavaFX() throws Exception {
		String jdkFXHome = System.getProperty("java8fx.home");
		Assume.assumeThat("No java8fx.home path set, skipping test", jdkFXHome, IsNull.notNullValue());

		IVMInstall defaultJRE = JavaRuntime.getDefaultVMInstall();

		String name = "java8fx";
		IProject project = null;
		try {
			// Create JavaFX runtime
			Preferences prefs = createJavaFXRuntimePrefs(jdkFXHome);
			JVMConfigurator.configureJVMs(prefs);
			JobHelpers.waitForJobsToComplete();

			// Import JavaFX project and checks it compiles without errors
			importProjects("eclipse/" + name);
			project = getProject(name);
			assertNoErrors(project);

			// Delete JavaFX runtime, project should fail to compile
			IVMInstall vm = JVMConfigurator.findVM(null, JAVA_SE_8);
			vm.getVMInstallType().disposeVMInstall(vm.getId());

		} finally {
			JavaRuntime.setDefaultVMInstall(defaultJRE, monitor, true);
			IExecutionEnvironment environment = JVMConfigurator.getExecutionEnvironment(JAVA_SE_8);
			if (environment != null) {
				environment.setDefaultVM(defaultJRE);
			}
		}
		JobHelpers.waitForJobsToComplete();
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertNotEquals(0, errors.size());
		String errorsStr = ResourceUtils.toString(errors);
		assertTrue("Unexpected errors:\n " + errorsStr, errorsStr.contains("javafx cannot be resolved"));
	}

	private Preferences createJavaFXRuntimePrefs(String path) {
		Preferences prefs = new Preferences();
		RuntimeEnvironment runtime = new RuntimeEnvironment();
		runtime.setPath(path);
		runtime.setName(JAVA_SE_8);
		runtime.setDefault(true);
		assertTrue(runtime.isValid());
		prefs.setRuntimes(Collections.singleton(runtime));
		return prefs;
	}

}
