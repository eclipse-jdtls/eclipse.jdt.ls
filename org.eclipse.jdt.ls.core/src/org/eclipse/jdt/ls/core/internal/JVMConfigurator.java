/*******************************************************************************
 * Copyright (c) 2019-2022 Red Hat Inc. and others.
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
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.Severity;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.MessageType;

/**
 * Configures and listens to JVM changes.
 *
 * @author Fred Bricon
 *
 */
public class JVMConfigurator implements IVMInstallChangedListener {

	public static final String MAC_OSX_VM_TYPE = "org.eclipse.jdt.internal.launching.macosx.MacOSXType"; //$NON-NLS-1$

	public static boolean configureDefaultVM(String javaHome) throws CoreException {
		if (StringUtils.isBlank(javaHome)) {
			return false;
		}
		File jvmHome = new File(javaHome);
		if (jvmHome.isDirectory()) {
			IVMInstall defaultVM = JavaRuntime.getDefaultVMInstall();
			if (defaultVM != null && jvmHome.equals(defaultVM.getInstallLocation())) {
				return false;
			}
		} else {
			JavaLanguageServerPlugin.logInfo("java.home " + jvmHome + " is not a directory");
			return false;
		}

		IVMInstall vm = findVM(jvmHome, null);
		if (vm == null) {
			IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
			if (installType == null || installType.getVMInstalls().length == 0) {
				// https://github.com/eclipse/eclipse.jdt.ls/issues/1646
				IVMInstallType macInstallType = JavaRuntime.getVMInstallType(MAC_OSX_VM_TYPE);
				if (macInstallType != null) {
					installType = macInstallType;
				}
			}
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
		JavaLanguageServerPlugin.logInfo("Setting java.home " + jvmHome + " as default global VM");
		JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
		JDTUtils.setCompatibleVMs(vm.getId());

		return true;
	}

	public static boolean configureJVMs(Preferences preferences) throws CoreException {
		return configureJVMs(preferences, null);
	}

	public static boolean configureJVMs(Preferences preferences, JavaClientConnection connection) throws CoreException {
		boolean changed = false;
		boolean defaultVMSet = false;
		Set<RuntimeEnvironment> runtimes = preferences.getRuntimes();
		for (RuntimeEnvironment runtime : runtimes) {
			if (runtime.isValid()) {
				File file = runtime.getInstallationFile();
				if (file != null && file.isDirectory()) {
					URL javadocURL = runtime.getJavadocURL();
					IPath sourcePath = runtime.getSourcePath();
					IVMInstall vm = findVM(file, runtime.getName());
					IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
					if (installType == null || installType.getVMInstalls().length == 0) {
						// https://github.com/eclipse/eclipse.jdt.ls/issues/1646
						IVMInstallType macInstallType = JavaRuntime.getVMInstallType(MAC_OSX_VM_TYPE);
						if (macInstallType != null) {
							installType = macInstallType;
						}
					}
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
						if (Objects.equals(file.getName(), "bin")) {
							sendNotification(connection, "Invalid runtime for " + runtime.getName() + ": 'bin' should be removed from the path (" + runtime.getPath() + ").");
						} else {
							sendNotification(connection, "Invalid runtime for " + runtime.getName() + ": The path (" + runtime.getPath() + ") does not point to a JDK.");
						}
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
						defaultVMSet = true;
						if (!Objects.equals(vm, JavaRuntime.getDefaultVMInstall())) {
							JavaLanguageServerPlugin.logInfo("Setting runtime " + runtime.getName() + "-" + runtime.getInstallationFile() + " as default global VM");
							JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
							changed = true;
						}
					}
					if (!setDefaultEnvironmentVM(vm, runtime.getName())) {
						sendNotification(connection, "Invalid runtime for " + runtime.getName() + ": Runtime at '" + runtime.getPath() + "' is not compatible with the '" + runtime.getName() + "' environment.");
						JavaLanguageServerPlugin.logError("Runtime at '" + runtime.getPath() + "' is not compatible with the '" + runtime.getName() + "' environment");
					}
				} else {
					sendNotification(connection, "Invalid runtime for " + runtime.getName() + ": The path points to a missing or inaccessible folder (" + runtime.getPath() + ").");
					JavaLanguageServerPlugin.logInfo("Invalid runtime: " + runtime);
				}
			}
		}

		if (!defaultVMSet) {
			changed = configureDefaultVM(preferences.getJavaHome()) || changed;
		}

		if (changed) {
			JavaLanguageServerPlugin.logInfo("JVM Runtimes changed, saving new configuration");
			JavaRuntime.saveVMConfiguration();
		}
		return changed;
	}

	private static void sendNotification(JavaClientConnection connection, String message) {
		if (connection == null) {
			return;
		}

		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && preferencesManager.getClientPreferences().isActionableRuntimeNotificationSupport()) {
			ActionableNotification runtimeNotification = new ActionableNotification()
							.withSeverity(Severity.error.toMessageType())
							.withMessage(message)
							.withCommands(Arrays.asList(
								new Command("Open Settings", "java.runtimeValidation.open", null)
							));
			connection.sendActionableNotification(runtimeNotification);
			return;
		}

		connection.showNotificationMessage(MessageType.Error, message);
	}

	private static boolean setDefaultEnvironmentVM(IVMInstall vm, String name) {
		IExecutionEnvironment environment = getExecutionEnvironment(name);
		if (environment != null) {
			if (Objects.equals(vm, environment.getDefaultVM())) {
				return true;
			}
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
		String prev = (previous == null) ? null : previous.getId() + "-" + previous.getInstallLocation();
		String curr = (current == null) ? null : current.getId() + "-" + current.getInstallLocation();

		JavaLanguageServerPlugin.logInfo("Default VM Install changed from  " + prev + " to " + curr);

		//Reset global compliance settings
		AbstractVMInstall jvm = (AbstractVMInstall) current;
		long jdkLevel = CompilerOptions.versionToJdkLevel(jvm.getJavaVersion());
		String compliance = CompilerOptions.versionFromJdkLevel(jdkLevel);
		Hashtable<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(compliance, options);
		JavaCore.setOptions(options);

		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
			if (!ProjectUtils.isVisibleProject(project) && ProjectUtils.isJavaProject(project)) {
				IJavaProject javaProject = JavaCore.create(project);
				configureJVMSettings(javaProject, current);
			}
			ProjectsManager projectsManager = JavaLanguageServerPlugin.getProjectsManager();
			if (projectsManager != null && projectsManager.useDefaultVM(project, current)) {
				JavaLanguageServerPlugin.logInfo("defaultVMInstallChanged -> force update of " + project.getName());
				projectsManager.updateProject(project, true);
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
		if (vmInstall instanceof AbstractVMInstall jvm) {
			version = jvm.getJavaVersion();
			long jdkLevel = CompilerOptions.versionToJdkLevel(jvm.getJavaVersion());
			String compliance = CompilerOptions.versionFromJdkLevel(jdkLevel);
			Map<String, String> options = javaProject.getOptions(false);
			JavaCore.setComplianceOptions(compliance, options);
		}
		if (!ProjectUtils.isSettingsFolderLinked(javaProject.getProject())) {
			if (JavaCore.isSupportedJavaVersion(version) && JavaCore.compareJavaVersions(version, JavaCore.latestSupportedJavaVersion()) >= 0) {
				//Enable Java preview features for the latest JDK release by default and stfu about it
				javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
				javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
			} else {
				javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
			}
		}
	}

}
