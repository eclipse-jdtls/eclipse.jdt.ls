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

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallChangedListener;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.launching.PropertyChangeEvent;
import org.eclipse.jdt.launching.VMStandin;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
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
		boolean changed = false;
		if (javaHome != null) {
			File jvmHome = new File(javaHome);
			if (jvmHome.isDirectory()) {
				IVMInstall vm = findVM(jvmHome, null);
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
					JDTUtils.setCompatibleVMs(vm.getId());
					changed = true;
				}
				boolean hasDefault = false;
				for (RuntimeEnvironment runtime : preferences.getRuntimes()) {
					if (runtime.isDefault()) {
						hasDefault = true;
						break;
					}
				}
				if (!hasDefault) {
					IVMInstall defaultVM = JavaRuntime.getDefaultVMInstall();
					File location = defaultVM.getInstallLocation();
					if (!location.equals(jvmHome)) {
						JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
						JDTUtils.setCompatibleVMs(vm.getId());
						changed = true;
					}
				}
			}
		}
		boolean jvmChanged = configureJVMs(preferences);
		return changed || jvmChanged;
	}

	public static boolean configureJVMs(Preferences preferences) throws CoreException {
		boolean changed = false;
		Set<RuntimeEnvironment> runtimes = preferences.getRuntimes();
		for (RuntimeEnvironment runtime : runtimes) {
			if (runtime.isValid()) {
				File file = runtime.getInstallationFile();
				if (file != null && file.isDirectory()) {
					URL javadocURL = runtime.getJavadocURL();
					IPath sourcePath = runtime.getSourcePath();
					IVMInstall vm = findVM(file, runtime.getName());
					IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
					VMStandin vmStandin;
					if (vm == null) {
						long unique = System.currentTimeMillis();
						while (installType.findVMInstall(String.valueOf(unique)) != null) {
							unique++;
						}
						String vmId = String.valueOf(unique);
						vmStandin = new VMStandin(installType, vmId);
						changed = true;
					} else {
						vmStandin = new VMStandin(vm);
						changed = changed || !runtime.getName().equals(vm.getName()) || !runtime.getInstallationFile().equals(vm.getInstallLocation());
					}
					IStatus status = installType.validateInstallLocation(file);
					if (!status.isOK()) {
						JavaLanguageServerPlugin.log(status);
						continue;
					}
					vmStandin.setName(runtime.getName());
					vmStandin.setInstallLocation(file);
					if (sourcePath != null || javadocURL != null) {
						LibraryLocation[] libs;
						if (vm != null && vm.getLibraryLocations() != null) {
							libs = vm.getLibraryLocations();
						} else {
							StandardVMType svt = (StandardVMType) installType;
							libs = svt.getDefaultLibraryLocations(file);
						}
						boolean libChanged = false;
						if (libs != null) {
							for (int i = 0; i < libs.length; i++) {
								LibraryLocation lib = libs[i];
								IPath systemSourcePath = sourcePath != null ? sourcePath : lib.getSystemLibrarySourcePath();
								URL javadocLocation = javadocURL != null ? javadocURL : lib.getJavadocLocation();
								LibraryLocation newLib = new LibraryLocation(lib.getSystemLibraryPath(), systemSourcePath, lib.getPackageRootPath(), javadocLocation, lib.getIndexLocation(), lib.getExternalAnnotationsPath());
								libChanged = libChanged || !newLib.equals(lib);
								libs[i] = newLib;
							}
						}
						if (libChanged) {
							LibraryLocation[] newLibs = Arrays.copyOf(libs, libs.length);
							vmStandin.setLibraryLocations(newLibs);
							changed = true;
						}
					}
					vm = vmStandin.convertToRealVM();
					if (runtime.isDefault()) {
						JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
						JavaLanguageServerPlugin.logInfo("Runtime " + runtime.getName() + " set as default");
					}
					if (!setDefaultEnvironmentVM(vm, runtime.getName())) {
						JavaLanguageServerPlugin.logError("Runtime at '" + runtime.getPath() + "' is not compatible with the '" + runtime.getName() + "' environment");
					}
				} else {
					JavaLanguageServerPlugin.logInfo("Invalid runtime: " + runtime);
				}
			}
		}
		if (changed) {
			JavaLanguageServerPlugin.logInfo("JVM Runtimes changed, saving new configuration");
			JavaRuntime.saveVMConfiguration();
		}
		return changed;
	}

	private static boolean setDefaultEnvironmentVM(IVMInstall vm, String name) {
		IExecutionEnvironment environment = getExecutionEnvironment(name);
		if (environment != null) {
			IVMInstall[] compatibleVMs = environment.getCompatibleVMs();
			for (IVMInstall compatibleVM : compatibleVMs) {
				if (compatibleVM.equals(vm)) {
					if (!environment.isStrictlyCompatible(vm)) {
						JavaLanguageServerPlugin.logInfo("Runtime at '" + vm.getInstallLocation().toString() + "' is not strictly compatible with the '" + name + "' environment");
					}
					JavaLanguageServerPlugin.logInfo("Setting " + compatibleVM.getInstallLocation() + " as '" + name + "' environment (id:" + compatibleVM.getId() + ")");
					environment.setDefaultVM(compatibleVM);
					return true;
				}
			}
		}
		return false;
	}

	public static IExecutionEnvironment getExecutionEnvironment(String name) {
		IExecutionEnvironmentsManager manager = JavaRuntime.getExecutionEnvironmentsManager();
		IExecutionEnvironment[] environments = manager.getExecutionEnvironments();
		for (IExecutionEnvironment environment : environments) {
			if (environment.getId().equals(name)) {
				return environment;
			}
		}
		return null;
	}

	public static IVMInstall findVM(File file, String name) {
		IVMInstallType[] types = JavaRuntime.getVMInstallTypes();
		for (IVMInstallType type : types) {
			IVMInstall[] installs = type.getVMInstalls();
			for (IVMInstall install : installs) {
				if (name != null && name.equals(install.getName())) {
					return install;
				}
				if (file != null && file.equals(install.getInstallLocation())) {
					return install;
				}
			}
		}
		return null;
	}

	@Override
	public void defaultVMInstallChanged(IVMInstall previous, IVMInstall current) {
		if (Objects.equals(previous, current)) {
			return;
		}

		//Reset global compliance settings
		Hashtable<String, String> options = JavaCore.getOptions();
		AbstractVMInstall jvm = (AbstractVMInstall) current;
		long jdkLevel = CompilerOptions.versionToJdkLevel(jvm.getJavaVersion());
		String compliance = CompilerOptions.versionFromJdkLevel(jdkLevel);
		JavaCore.setComplianceOptions(compliance, options);
		JavaCore.setOptions(options);

		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
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
		String version = "";
		if (vmInstall instanceof AbstractVMInstall) {
			AbstractVMInstall jvm = (AbstractVMInstall) vmInstall;
			version = jvm.getJavaVersion();
			long jdkLevel = CompilerOptions.versionToJdkLevel(jvm.getJavaVersion());
			String compliance = CompilerOptions.versionFromJdkLevel(jdkLevel);
			Map<String, String> options = javaProject.getOptions(false);
			JavaCore.setComplianceOptions(compliance, options);
		}
		;
		if (JavaCore.compareJavaVersions(version, JavaCore.latestSupportedJavaVersion()) >= 0) {
			//Enable Java preview features for the latest JDK release by default and stfu about it
			javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
			javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		} else {
			javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
		}
	}

}
