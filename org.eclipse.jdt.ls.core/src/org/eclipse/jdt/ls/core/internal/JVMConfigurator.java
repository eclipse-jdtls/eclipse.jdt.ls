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

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallChangedListener;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.PropertyChangeEvent;
import org.eclipse.jdt.launching.VMStandin;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

/**
 * Configures and listens to JVM changes.
 *
 * @author Fred Bricon
 *
 */
public class JVMConfigurator implements IVMInstallChangedListener {

	public static boolean configureDefaultVM(Preferences preferences) throws CoreException {
		if (preferences == null) {
			return false;
		}
		String javaHome = preferences.getJavaHome();
		if (javaHome != null) {
			File jvmHome = new File(javaHome);
			if (jvmHome.isDirectory()) {
				IVMInstall defaultVM = JavaRuntime.getDefaultVMInstall();
				File location = defaultVM.getInstallLocation();
				if (!location.equals(jvmHome)) {
					IVMInstall vm = findVM(jvmHome);
					if (vm == null) {
						IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
						long unique = System.currentTimeMillis();
						while (installType.findVMInstall(String.valueOf(unique)) != null) {
							unique++;
						}
						String vmId = String.valueOf(unique);
						VMStandin vmStandin = new VMStandin(installType, vmId);
						String name = StringUtils.defaultIfBlank(jvmHome.getName(), "JRE");
						vmStandin.setName(name);
						vmStandin.setInstallLocation(jvmHome);
						vm = vmStandin.convertToRealVM();
					}
					JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
					JDTUtils.setCompatibleVMs(vm.getId());
					return true;
				}
			}
		}
		return false;
	}

	private static IVMInstall findVM(File jvmHome) {
		IVMInstallType[] types = JavaRuntime.getVMInstallTypes();
		for (IVMInstallType type : types) {
			IVMInstall[] installs = type.getVMInstalls();
			for (IVMInstall install : installs) {
				if (jvmHome.equals(install.getInstallLocation())) {
					return install;
				}
			}
		}
		return null;
	}

	@Override
	public void defaultVMInstallChanged(IVMInstall previous, IVMInstall current) {
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (!ProjectUtils.isVisibleProject(project) && ProjectUtils.isJavaProject(project)) {
				IJavaProject javaProject = JavaCore.create(project);
				configureJVMSettings(javaProject, current);
			}
		}
	}

	@Override
	public void vmChanged(PropertyChangeEvent event) {
	}

	@Override
	public void vmAdded(IVMInstall vm) {
	}

	@Override
	public void vmRemoved(IVMInstall vm) {
	}

	public static void configureJVMSettings(IJavaProject javaProject) {
		configureJVMSettings(javaProject, JavaRuntime.getDefaultVMInstall());
	}

	public static void configureJVMSettings(IJavaProject javaProject, IVMInstall vmInstall) {
		if (javaProject == null) {
			return;
		}
		long javaVersion = 0;
		if (vmInstall instanceof AbstractVMInstall) {
			AbstractVMInstall jvm = (AbstractVMInstall) vmInstall;
			javaVersion = CompilerOptions.versionToJdkLevel(jvm.getJavaVersion());
		}
		if (javaVersion > ClassFileConstants.JDK11) {
			//Enable Java 12+ preview features by default and stfu about it
			javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
			javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		} else {
			javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
		}
	}

}
