/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.managers;

import java.util.Date;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.internal.core.LaunchConfiguration;
import org.eclipse.debug.internal.core.LaunchConfigurationInfo;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

/**
 * JavaApplicationLaunchConfiguration
 */
public class JavaApplicationLaunchConfiguration extends LaunchConfiguration {

	private IProject project;
	private String scope;
	private String classpathProvider;
	private LaunchConfigurationInfo launchInfo;

	protected JavaApplicationLaunchConfiguration(IProject project, String scope, String classpathProvider) throws CoreException {
		super(String.valueOf(new Date().getTime()), null, false);
		this.project = project;
		this.scope = scope;
		this.classpathProvider = classpathProvider;
		this.launchInfo = new JavaLaunchConfigurationInfo(scope);
	}

	@Override
	public boolean getAttribute(String attributeName, boolean defaultValue) throws CoreException {
		if (IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE.equalsIgnoreCase(attributeName)) {
			return !"test".equals(this.scope);
		}

		return super.getAttribute(attributeName, defaultValue);
	}

	@Override
	public String getAttribute(String attributeName, String defaultValue) throws CoreException {
		if (IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME.equalsIgnoreCase(attributeName)) {
			return project.getName();
		} else if (IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER.equalsIgnoreCase(attributeName)) {
			return this.classpathProvider;
		}

		return super.getAttribute(attributeName, defaultValue);
	}

	@Override
	protected LaunchConfigurationInfo getInfo() throws CoreException {
		return this.launchInfo;
	}
}
