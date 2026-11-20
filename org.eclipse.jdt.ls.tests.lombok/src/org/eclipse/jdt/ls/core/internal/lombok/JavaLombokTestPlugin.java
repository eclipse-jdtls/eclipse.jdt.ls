/*******************************************************************************
* Copyright (c) 2026 IBM Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial API and implementation
*******************************************************************************/
package org.eclipse.jdt.ls.core.internal.lombok;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator for lombok-specific tests.
 *
 * <p>
 * In order to run these tests properly from within Eclipse, you'll have to add lombok as a javaagent in the test configuration:
 * <ol>
 * <li>Generate the debug configuration by attempting to run the test</li>
 * <li>Open "Debug Configurations" then navigate to the configuration for the test</li>
 * <li><p>Navigate to the "Arguments" tab and add the following to the "VM Arguments" configuration:</p>
 * <p>
 * <code>"-javaagent:/home/davthomp/Documents/Projects/mp/eclipse.jdt.ls/org.eclipse.jdt.ls.tests.lombok/lib/lombok-1.18.48.jar"</code>
 * </p>
 * <p>then, you'll need to make the following changes:
 * <ul>
 * <li><b>/home/davthomp/Documents/Projects/mp/eclipse.jdt.ls</b> Change this to the path where you have jdtls checked out</li>
 * <li><b>lombok-1.18.48.jar</b> Make sure this jar exists, and adjust the version number to the version to match what's there</li>
 * </ul></p>
 * </li>
 * <li>After this, you should be able to Run/Debug the test configuration and the tests should pass</li>
 * </ol>
 * </p>
 *
 * <p>
 * Alternatively, here's a quick and dirty method to run these tests on command line:
 * <ol>
 * <li>
 * Install all the bundles, skipping test execution: <br />
 * <code>./mvnw clean install -DskipTests</code><br />
 * </li>
 *
 * <li>
 * Run this test bundle: <br />
 * <code>./mvnw clean verify -pl org.eclipse.jdt.ls.tests.lombok</code>
 * </li>
 * </p>
 */
public class JavaLombokTestPlugin implements BundleActivator {
	public static final String PLUGIN_ID = "org.eclipse.jdt.ls.tests.lombok";

	@Override
	public void start(BundleContext context) throws Exception {
		JavaCore.initializeAfterLoad(new NullProgressMonitor());
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		description.setAutoBuilding(false);
		workspace.setDescription(description);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
	}
}
