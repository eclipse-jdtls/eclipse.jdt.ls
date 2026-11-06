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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
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
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;

/**
 * Configures and listens to JVM changes.
 *
 * @author Fred Bricon
 *
 */
public class JVMConfigurator implements IVMInstallChangedListener {

	public static final String MAC_OSX_VM_TYPE = "org.eclipse.jdt.internal.launching.macosx.MacOSXType"; //$NON-NLS-1$

	private static final QualifiedName PREVIEW_FEATURE_NOTIFICATION = new QualifiedName(IConstants.PLUGIN_ID, "previewFeatureNotification");
	private static final String CONFIGURE_JAVA_RUNTIMES = "Configure Java Runtimes";
	private static final String DISABLE_PREVIEW_FEATURES = "Disable Preview Features";

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

	/**
	 * Offer to reconcile incompatible preview settings on an unmanaged project.
	 * Existing settings do not record their origin, so even an ignored preview
	 * warning is not permission to change the language level or disable preview.
	 *
	 * @return {@code true} if a migration choice was requested.
	 */
	public static boolean reconcilePreviewFeatureSettings(IJavaProject javaProject) throws CoreException {
		return reconcilePreviewFeatureSettings(javaProject, JavaLanguageServerPlugin.getInstance().getClientConnection());
	}

	public static boolean reconcilePreviewFeatureSettings(IJavaProject javaProject, JavaClientConnection connection) throws CoreException {
		if (!canReconcilePreviewFeatures(javaProject)) {
			return false;
		}
		Map<String, String> effectiveOptions = Map.copyOf(javaProject.getOptions(true));
		if (!JavaCore.ENABLED.equals(effectiveOptions.get(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES))) {
			return false;
		}
		String latest = JavaCore.latestSupportedJavaVersion();
		PreviewRuntime currentRuntime = new PreviewRuntime(JavaRuntime.getVMInstall(javaProject));
		if (latest.equals(effectiveOptions.get(JavaCore.COMPILER_SOURCE))
				&& latest.equals(effectiveOptions.get(JavaCore.COMPILER_COMPLIANCE))
				&& latest.equals(effectiveOptions.get(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM))
				&& latest.equals(currentRuntime.version())) {
			return false;
		}
		if (connection == null) {
			JavaLanguageServerPlugin.logInfo("Cannot offer preview migration for " + javaProject.getElementName() + ": no client connection.");
			return false;
		}

		Map<String, PreviewRuntime> migrations = new LinkedHashMap<>();
		for (IVMInstallType type : JavaRuntime.getVMInstallTypes()) {
			for (IVMInstall vm : type.getVMInstalls()) {
				PreviewRuntime runtime = new PreviewRuntime(vm);
				if (latest.equals(runtime.version()) && runtime.isAvailable()) {
					String title = "Use Java " + latest + " and Keep Preview (" + vm.getName() + " - " + runtime.location() + ")";
					migrations.put(title, runtime);
				}
			}
		}
		String fingerprint = Arrays.asList(latest,
				effectiveOptions.get(JavaCore.COMPILER_SOURCE),
				effectiveOptions.get(JavaCore.COMPILER_COMPLIANCE),
				effectiveOptions.get(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM),
				effectiveOptions.get(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES),
				effectiveOptions.get(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES),
				currentRuntime.identity(), migrations.keySet()).toString();
		IProject project = javaProject.getProject();
		if (fingerprint.equals(project.getPersistentProperty(PREVIEW_FEATURE_NOTIFICATION))) {
			return false;
		}

		Map<String, String> projectOptions = Map.copyOf(javaProject.getOptions(false));
		List<IClasspathEntry> jreEntries = getJreEntries(javaProject);
		long compilerSettingsStamp = project.getFolder(ProjectUtils.SETTINGS).getFile(JavaCore.PLUGIN_ID + ".prefs").getModificationStamp();
		long classpathStamp = project.getFile(".classpath").getModificationStamp();
		List<MessageActionItem> actions = new ArrayList<>();
		migrations.keySet().forEach(title -> actions.add(new MessageActionItem(title)));
		actions.add(new MessageActionItem(CONFIGURE_JAVA_RUNTIMES));
		actions.add(new MessageActionItem(DISABLE_PREVIEW_FEATURES));
		String projectName = ProjectUtils.getProjectRealFolder(project).toOSString();
		String message = "Preview features in " + projectName + " use Java source " + effectiveOptions.get(JavaCore.COMPILER_SOURCE)
				+ " and project JDK " + Objects.toString(currentRuntime.version(), "unknown") + ". This compiler requires Java " + latest + " for preview.";
		if (migrations.isEmpty()) {
			message += " No configured Java " + latest + " JDK is available. Configure one in java.configuration.runtimes to migrate.";
		}
		message += " Keep preview by updating the project JDK and source/compliance/target together, or explicitly disable it without changing the JDK or language level."
				+ " Earlier preview syntax and APIs may require source changes.";
		ShowMessageRequestParams request = new ShowMessageRequestParams();
		request.setType(MessageType.Error);
		request.setMessage(message);
		request.setActions(actions);

		// Remember this exact mismatch across source opens and reimports, including a dismissed request.
		project.setPersistentProperty(PREVIEW_FEATURE_NOTIFICATION, fingerprint);
		CompletableFuture<MessageActionItem> response = connection.client.showMessageRequest(request);
		if (response == null) {
			JavaLanguageServerPlugin.logError("The client did not return a preview migration response for " + projectName);
			connection.showNotificationMessage(MessageType.Error, message);
			return false;
		}
		response.thenAccept(action -> {
			if (action == null) {
				return;
			}
			String title = action.getTitle();
			PreviewRuntime selectedRuntime = migrations.get(title);
			if (selectedRuntime == null && !CONFIGURE_JAVA_RUNTIMES.equals(title) && !DISABLE_PREVIEW_FEATURES.equals(title)) {
				JavaLanguageServerPlugin.logError("Unknown preview migration action: " + title);
				return;
			}
			WorkspaceJob migration = new WorkspaceJob("Update preview feature settings for " + projectName) {
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) {
					try {
						if (project.isAccessible()) {
							project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
						}
						if (!canReconcilePreviewFeatures(javaProject)
								|| !latest.equals(JavaCore.latestSupportedJavaVersion())
								|| !fingerprint.equals(project.getPersistentProperty(PREVIEW_FEATURE_NOTIFICATION))
								|| compilerSettingsStamp != project.getFolder(ProjectUtils.SETTINGS).getFile(JavaCore.PLUGIN_ID + ".prefs").getModificationStamp()
								|| classpathStamp != project.getFile(".classpath").getModificationStamp()
								|| !projectOptions.equals(javaProject.getOptions(false))
								|| !effectiveOptions.equals(javaProject.getOptions(true))
								|| !jreEntries.equals(getJreEntries(javaProject))
								|| !currentRuntime.matches(JavaRuntime.getVMInstall(javaProject))) {
							if (project.isAccessible() && fingerprint.equals(project.getPersistentProperty(PREVIEW_FEATURE_NOTIFICATION))) {
								project.setPersistentProperty(PREVIEW_FEATURE_NOTIFICATION, null);
							}
							connection.showNotificationMessage(MessageType.Warning, "Preview migration for " + projectName
									+ " was not applied because the project, settings or JDK changed while the choice was pending.");
							return Status.CANCEL_STATUS;
						}
						if (CONFIGURE_JAVA_RUNTIMES.equals(title)) {
							connection.sendNotification("java.runtimeValidation.open");
							return Status.OK_STATUS;
						}
						if (selectedRuntime != null && (!selectedRuntime.isAvailable() || !latest.equals(getJavaVersion(selectedRuntime.vm())))) {
							sendNotification(connection, "The selected Java " + latest + " JDK is no longer available. Configure java.configuration.runtimes and try again.");
							return Status.CANCEL_STATUS;
						}
						if (monitor.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						applyPreviewFeatureSettings(javaProject, selectedRuntime, latest, monitor);
						project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
						ProjectUtils.refreshDiagnostics(monitor);
						return Status.OK_STATUS;
					} catch (CoreException e) {
						String error = "Failed to update preview feature settings for " + projectName;
						JavaLanguageServerPlugin.logException(error, e);
						connection.showNotificationMessage(MessageType.Error, error + ": " + e.getMessage());
						return e.getStatus();
					}
				}
			};
			migration.setRule(ResourcesPlugin.getWorkspace().getRoot());
			migration.setPriority(Job.BUILD);
			migration.schedule();
		}).exceptionally(error -> {
			JavaLanguageServerPlugin.logException("Preview migration request failed for " + projectName, error);
			connection.showNotificationMessage(MessageType.Error, "Could not request preview migration for " + projectName + ": " + error.getMessage());
			return null;
		});
		return true;
	}

	private static boolean canReconcilePreviewFeatures(IJavaProject javaProject) {
		return javaProject != null && javaProject.exists() && javaProject.getProject().isAccessible()
				&& ProjectUtils.isUnmanagedFolder(javaProject.getProject())
				&& !ProjectUtils.isMavenProject(javaProject.getProject())
				&& !ProjectUtils.isGradleProject(javaProject.getProject())
				&& !ProjectUtils.isSettingsFolderLinked(javaProject.getProject());
	}

	private static List<IClasspathEntry> getJreEntries(IJavaProject javaProject) throws CoreException {
		return Arrays.stream(javaProject.getRawClasspath()).filter(JVMConfigurator::isJreEntry).toList();
	}

	private static boolean isJreEntry(IClasspathEntry entry) {
		return entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER && JavaRuntime.JRE_CONTAINER.equals(entry.getPath().segment(0));
	}

	private static void applyPreviewFeatureSettings(IJavaProject javaProject, PreviewRuntime runtime, String latest, IProgressMonitor monitor) throws CoreException {
		IClasspathEntry[] originalClasspath = javaProject.getRawClasspath();
		Map<String, String> originalOptions = javaProject.getOptions(false);
		Map<String, String> options = new HashMap<>(originalOptions);
		try {
			if (runtime == null) {
				options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
			} else {
				if (!runtime.matches(JavaRuntime.getVMInstall(javaProject))) {
					List<IClasspathEntry> classpath = new ArrayList<>();
					boolean hasJreEntry = false;
					for (IClasspathEntry entry : originalClasspath) {
						if (isJreEntry(entry)) {
							classpath.add(JavaCore.newContainerEntry(JavaRuntime.newJREContainerPath(runtime.vm()),
									entry.getAccessRules(), entry.getExtraAttributes(), entry.isExported()));
							hasJreEntry = true;
						} else {
							classpath.add(entry);
						}
					}
					if (!hasJreEntry) {
						classpath.add(JavaCore.newContainerEntry(JavaRuntime.newJREContainerPath(runtime.vm())));
					}
					javaProject.setRawClasspath(classpath.toArray(IClasspathEntry[]::new), monitor);
				}
				options.put(JavaCore.COMPILER_SOURCE, latest);
				options.put(JavaCore.COMPILER_COMPLIANCE, latest);
				options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, latest);
			}
			javaProject.setOptions(options);
			if (!options.equals(javaProject.getOptions(false))
					|| runtime != null && !runtime.matches(JavaRuntime.getVMInstall(javaProject))) {
				throw new CoreException(StatusFactory.newErrorStatus("The selected preview configuration could not be persisted."));
			}
		} catch (CoreException e) {
			javaProject.setOptions(originalOptions);
			try {
				javaProject.setRawClasspath(originalClasspath, new NullProgressMonitor());
			} catch (CoreException rollbackError) {
				e.addSuppressed(rollbackError);
			}
			throw e;
		}
	}

	private static String getJavaVersion(IVMInstall vm) {
		if (vm instanceof IVMInstall2 vmInstall) {
			String version = vmInstall.getJavaVersion();
			if (!StringUtils.isBlank(version)) {
				// CompilerOptions.versionToJdkLevel clamps newer VMs to the compiler's latest release.
				if (version.startsWith("1.")) {
					version = version.substring(2);
				}
				return StringUtils.substringBefore(StringUtils.substringBefore(version, "."), "-");
			}
		}
		return null;
	}

	private record PreviewRuntime(IVMInstall vm, String version, File location) {
		private PreviewRuntime(IVMInstall vm) {
			this(vm, getJavaVersion(vm), vm == null ? null : vm.getInstallLocation());
		}

		private boolean matches(IVMInstall current) {
			return Objects.equals(vm, current) && Objects.equals(version, getJavaVersion(current))
					&& Objects.equals(location, current == null ? null : current.getInstallLocation());
		}

		private boolean isAvailable() {
			return vm != null && !StringUtils.isBlank(vm.getName()) && location != null && location.isDirectory()
					&& matches(vm.getVMInstallType().findVMInstall(vm.getId()))
					&& vm.getVMInstallType().validateInstallLocation(location).isOK();
		}

		private String identity() {
			return vm == null ? "none" : vm.getVMInstallType().getId() + "/" + vm.getId() + "/" + version + "/" + location;
		}
	}

}
