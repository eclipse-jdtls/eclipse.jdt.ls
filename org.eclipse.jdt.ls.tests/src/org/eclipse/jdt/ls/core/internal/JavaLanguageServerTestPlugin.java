/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logException;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.jdt.MavenJdtPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * @author snjeza
 *
 */
public class JavaLanguageServerTestPlugin implements BundleActivator {

	public static final String PLUGIN_ID = "org.eclipse.jdt.ls.tests";
	// see org.eclipse.m2e.jdt.MavenJdtPlugin.PREFERENCE_LOOKUP_JVM_IN_TOOLCHAINS
	private static final String PREFERENCE_LOOKUP_JVM_IN_TOOLCHAINS = "lookupJVMInToolchains"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		// https://github.com/eclipse-jdtls/eclipse.jdt.ls/pull/3238
		if ("false".equals(System.getProperty(PREFERENCE_LOOKUP_JVM_IN_TOOLCHAINS, "true"))) {
			IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(MavenJdtPlugin.PLUGIN_ID);
			prefs.put(PREFERENCE_LOOKUP_JVM_IN_TOOLCHAINS, Boolean.FALSE.toString());
		}
		try {
			long start = System.currentTimeMillis();
			String symbolicName = MavenJdtPlugin.PLUGIN_ID;
			JavaLanguageServerPlugin.debugTrace("Starting " + symbolicName);
			Platform.getBundle(symbolicName).start(Bundle.START_TRANSIENT);
			JavaLanguageServerPlugin.logInfo("Started " + symbolicName + " " + (System.currentTimeMillis() - start) + "ms");
		} catch (BundleException e) {
			logException(e.getMessage(), e);
		}
		TestVMType.setTestJREAsDefault("17");
		JavaCore.initializeAfterLoad(new NullProgressMonitor());
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		description.setAutoBuilding(true);
		workspace.setDescription(description);
	}

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
	}

}
