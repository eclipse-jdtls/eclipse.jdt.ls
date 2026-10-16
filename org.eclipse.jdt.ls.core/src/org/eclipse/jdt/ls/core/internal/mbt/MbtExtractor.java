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
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.mbt;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

/**
 * Generates {@code mbt.json} using M2E's embedded Maven, reproducing
 * exactly the same process as the standalone classpath-extractor.
 *
 * <p>Registers an {@link AbstractMavenLifecycleParticipant} in M2E's
 * Plexus container to strip unnecessary plugin executions (compiler,
 * surefire, checkstyle, etc.) before the lifecycle runs — exactly as
 * {@code ClasspathExtractorParticipant.afterProjectsRead()} does.</p>
 *
 * <p>Then executes Maven on the full reactor in a single session,
 * sharing the resolution cache across all modules — same performance
 * as running {@code mvn} from the command line.</p>
 */
public final class MbtExtractor {

	private static final Gson GSON = new Gson();

	private static final String POM_XML = "pom.xml";
	private static final String POM_TYPE = "pom";
	private static final String JAR_TYPE = "jar";
	private static final String SCOPE_TEST = "test";
	private static final String SCOPE_PROVIDED = "provided";
	private static final String SCOPE_SYSTEM = "system";
	private static final String SCOPE_IMPORT = "import";
	private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";
	private static final String SOURCES_CLASSIFIER = "sources";
	private static final String TEST_SCOPE_SUFFIX = ":test";
	private static final int MAX_HIERARCHY_DEPTH = 10;
	private static final int MAX_PROPERTY_ITERATIONS = 5;

	private static final Map<String, Map<String, String>> hierarchyPropsCache = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> hierarchyManagedCache = new ConcurrentHashMap<>();

	private static final String COMPILER_PLUGIN_KEY = "org.apache.maven.plugins:maven-compiler-plugin";

	// Plugins whose executions are removed (same as ClasspathExtractorParticipant)
	private static final Set<String> SKIPPED_PLUGINS = Set.of(
			"org.apache.maven.plugins:maven-surefire-plugin",
			"org.apache.maven.plugins:maven-enforcer-plugin",
			"org.apache.maven.plugins:maven-checkstyle-plugin",
			"com.github.ekryd.sortpom:sortpom-maven-plugin",
			"org.jacoco:jacoco-maven-plugin",
			"org.codehaus.mojo:license-maven-plugin",
			"org.apache.maven.plugins:maven-compiler-plugin");

	// Phases whose executions are filtered out (same as ClasspathExtractorParticipant)
	private static final Set<String> SKIPPED_PHASES = Set.of(
			"initialize", "validate",
			"generate-resources", "process-resources",
			"generate-test-resources", "process-test-resources",
			"test-compile", "compile");

	private MbtExtractor() {
	}

	private static void sendProgress(String message) {
		JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, message);
	}

	/**
	 * Generates {@code mbt.json} for the given workspace and returns the
	 * parsed {@link MbtInfo} directly, avoiding the write-then-read round-trip.
	 *
	 * @return the generated {@link MbtInfo}, or {@code null} if no projects were found
	 */
	public static MbtInfo generate(Path workspacePath, IProgressMonitor monitor) throws IOException {
		return generate(workspacePath, true, monitor);
	}

	/**
	 * Generates the dependency graph for the given workspace.
	 *
	 * @param persistToFile if {@code true}, writes mbt.json to disk; if {@code false}, keeps in memory only
	 * @return the generated {@link MbtInfo}, or {@code null} if no projects were found
	 */
	public static MbtInfo generate(Path workspacePath, boolean persistToFile, IProgressMonitor monitor) throws IOException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

		// Step 1: Find root pom.xml
		subMonitor.subTask("Scanning for root " + POM_XML + "...");
		File rootPom = findRootPom(workspacePath);
		if (rootPom == null) {
			JavaLanguageServerPlugin.logInfo("No " + POM_XML + " found in " + workspacePath);
			return null;
		}
		JavaLanguageServerPlugin.logInfo("Root POM: " + rootPom);
		subMonitor.worked(2);

		IMaven m2e = MavenPlugin.getMaven();

		// Step 2: Register lifecycle participant that strips plugins
		// (same as ClasspathExtractorParticipant — removes compiler, surefire, etc.)
		MbtLifecycleParticipant participant = new MbtLifecycleParticipant();
		try {
			PlexusContainer container = m2e.lookup(PlexusContainer.class);
			container.addComponent(participant, AbstractMavenLifecycleParticipant.class, "mbt-optimizer");
		} catch (Exception e) {
			JavaLanguageServerPlugin.logInfo("Could not register lifecycle participant: " + e.getMessage());
		}

		try {
			// Step 3: Create execution context and configure its request
			subMonitor.subTask("Preparing Maven reactor...");
			IMavenExecutionContext context = m2e.createExecutionContext();
			MavenExecutionRequest request = context.getExecutionRequest();
			request.setPom(rootPom);
			request.setBaseDirectory(rootPom.getParentFile());
			// dependency:resolve has requiresDependencyResolution = TEST, so Maven resolves
			// all dependencies (including test-scope) before the mojo runs — no lifecycle
			// phase needed.
			request.setGoals(List.of("dependency:resolve"));
			request.setReactorFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_NEVER);

			// Progress listener: per-module, per-mojo
			SubMonitor reactorMonitor = subMonitor.split(80);
			request.setExecutionListener(new ReactorProgressListener(reactorMonitor));

			// Step 4: Execute Maven on FULL reactor (single session, shared cache)
			JavaLanguageServerPlugin.logInfo("Executing Maven reactor: dependency:resolve");
			MavenExecutionResult result = context.execute(request);

			if (result.hasExceptions()) {
				for (Throwable t : result.getExceptions()) {
					JavaLanguageServerPlugin.logInfo("Maven reactor warning: " + t.getMessage());
				}
			}

			// Step 5: Extract classpath info from resolved projects
			List<MavenProject> projects = result.getTopologicallySortedProjects();
			if (projects == null || projects.isEmpty()) {
				JavaLanguageServerPlugin.logInfo("No projects in reactor result");
				return null;
			}

			JavaLanguageServerPlugin.logInfo("Reactor complete: " + projects.size() + " projects");

			Map<String, MbtTargetOutput> targets = new TreeMap<>();
			Map<String, MbtDependencyModuleOutput> dependencyModules = new TreeMap<>();
			hierarchyPropsCache.clear();
			hierarchyManagedCache.clear();

			SubMonitor extractMonitor = subMonitor.split(10);
			extractMonitor.setWorkRemaining(projects.size());
			int extractCount = 0;
			int extractTotal = projects.size();

			for (MavenProject project : projects) {
				extractCount++;
				if (POM_TYPE.equals(project.getPackaging())) {
					extractMonitor.worked(1);
					continue;
				}

				String projectId = gav(project.getGroupId(), project.getArtifactId(), project.getVersion());
				sendProgress("Resolving mbt.json dependencies [" + extractCount + "/" + extractTotal + "]: " + project.getArtifactId());

				// Get compiler options collected by the lifecycle participant (before plugins were stripped)
				List<String> compileOptions = participant.getCompileOptions(projectId);
				List<String> testCompileOptions = participant.getTestCompileOptions(projectId);

				extractFromResolvedProject(projectId, project, workspacePath, targets, dependencyModules,
						compileOptions, testCompileOptions, m2e, extractMonitor);
				extractMonitor.worked(1);
			}

			if (targets.isEmpty()) {
				JavaLanguageServerPlugin.logInfo("No targets extracted");
				return null;
			}

			// Step 6: Finalize, write mbt.json, and return parsed MbtInfo
			promoteDependencyModulesThatAreTargets(targets, dependencyModules);

			String genMsg = "Generating mbt.json (" + targets.size() + " targets, " + dependencyModules.size() + " dependencies)...";
			subMonitor.subTask(genMsg);
			sendProgress(genMsg);
			MbtInfo mbtInfoResult = writeMbtJson(workspacePath, targets, dependencyModules, persistToFile);
			JavaLanguageServerPlugin.logInfo("mbt.json generated: " + targets.size() + " targets, " + dependencyModules.size() + " dependencies");
			subMonitor.worked(5);
			return mbtInfoResult;

		} catch (CoreException e) {
			throw new IOException("Maven reactor execution failed", e);
		} finally {
			participant.deactivate();
		}
	}

	public static void clearHierarchyCaches() {
		hierarchyPropsCache.clear();
		hierarchyManagedCache.clear();
	}

	/**
	 * Generates MBT info for a single module, resolving its dependencies
	 * via Maven without executing the full reactor. Used for per-module
	 * on-demand generation when mbt.json does not yet exist.
	 *
	 * @param moduleDir     the directory containing the module's pom.xml
	 * @param workspacePath the workspace root for relative path resolution
	 * @param monitor       progress monitor
	 * @return the generated {@link MbtInfo} for this module, or {@code null} if resolution fails
	 */
	public static MbtInfo generateForModule(Path moduleDir, Path workspacePath, IProgressMonitor monitor) throws IOException {
		File modulePom = moduleDir.resolve(POM_XML).toFile();
		if (!modulePom.isFile()) {
			return null;
		}

		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		IMaven m2e = MavenPlugin.getMaven();

		try {
			String moduleName = moduleDir.getFileName().toString();
			subMonitor.subTask("Resolving module: " + moduleName);
			sendProgress("Resolving module: " + moduleName);

			IMavenExecutionContext context = m2e.createExecutionContext();
			MavenExecutionRequest request = context.getExecutionRequest();
			request.setPom(modulePom);
			request.setBaseDirectory(modulePom.getParentFile());
			request.setGoals(List.of("dependency:resolve"));
			request.setReactorFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_NEVER);
			request.setRecursive(false);

			subMonitor.worked(10);

			JavaLanguageServerPlugin.logInfo("Per-module Maven resolution: " + modulePom);
			MavenExecutionResult result = context.execute(request);
			subMonitor.worked(60);

			if (result.hasExceptions()) {
				for (Throwable t : result.getExceptions()) {
					JavaLanguageServerPlugin.logInfo("Per-module Maven warning: " + t.getMessage());
				}
			}

			List<MavenProject> projects = result.getTopologicallySortedProjects();
			if (projects == null || projects.isEmpty()) {
				JavaLanguageServerPlugin.logInfo("No projects resolved for module: " + moduleDir);
				return null;
			}

			Map<String, MbtTargetOutput> targets = new TreeMap<>();
			Map<String, MbtDependencyModuleOutput> dependencyModules = new TreeMap<>();

			for (MavenProject project : projects) {
				if (POM_TYPE.equals(project.getPackaging())) {
					continue;
				}
				String projectId = gav(project.getGroupId(), project.getArtifactId(), project.getVersion());

				List<String> compileOptions = List.of();
				List<String> testCompileOptions = List.of();
				Plugin compilerPlugin = project.getPlugin(COMPILER_PLUGIN_KEY);
				if (compilerPlugin != null) {
					compileOptions = extractCompileJavacOptionsFromPlugin(compilerPlugin);
					testCompileOptions = extractTestCompileJavacOptionsFromPlugin(compilerPlugin);
				}

				extractFromResolvedProject(projectId, project, workspacePath, targets, dependencyModules,
						compileOptions, testCompileOptions, m2e, subMonitor);
				subMonitor.worked(20);
			}

			if (targets.isEmpty()) {
				JavaLanguageServerPlugin.logInfo("No targets extracted for module: " + moduleDir);
				return null;
			}

			promoteDependencyModulesThatAreTargets(targets, dependencyModules);

			JavaLanguageServerPlugin.logInfo("Per-module MBT resolved: " + targets.size()
					+ " target(s), " + dependencyModules.size() + " dependencies");
			return writeMbtJson(workspacePath, targets, dependencyModules, false);

		} catch (CoreException e) {
			throw new IOException("Maven module resolution failed for " + moduleDir, e);
		}
	}

	// ---- Lifecycle participant (same as ClasspathExtractorParticipant) ----

	private static class MbtLifecycleParticipant extends AbstractMavenLifecycleParticipant {

		private final Map<String, List<String>> compileOptionsByProject = new TreeMap<>();
		private final Map<String, List<String>> testCompileOptionsByProject = new TreeMap<>();
		private volatile boolean active = true;

		@Override
		public void afterProjectsRead(MavenSession session) {
			if (!active) {
				return;
			}
			int stripped = 0;
			for (MavenProject project : session.getProjects()) {
				String projectId = gav(project.getGroupId(), project.getArtifactId(), project.getVersion());
				Build build = project.getBuild();
				if (build == null || build.getPlugins() == null) {
					continue;
				}
				for (Plugin plugin : build.getPlugins()) {
					String pluginKey = ga(plugin.getGroupId(), plugin.getArtifactId());

					// Extract compiler options BEFORE stripping (same as ClasspathExtractorParticipant)
					if (COMPILER_PLUGIN_KEY.equals(pluginKey)) {
						compileOptionsByProject.put(projectId, extractCompileJavacOptionsFromPlugin(plugin));
						testCompileOptionsByProject.put(projectId, extractTestCompileJavacOptionsFromPlugin(plugin));
					}

					// Strip plugin executions for speed
					if (SKIPPED_PLUGINS.contains(pluginKey)) {
						stripped += plugin.getExecutions().size();
						plugin.setExecutions(Collections.emptyList());
					} else {
						List<PluginExecution> filtered = plugin.getExecutions().stream()
								.filter(ex -> ex.getPhase() == null || !SKIPPED_PHASES.contains(ex.getPhase()))
								.collect(Collectors.toList());
						stripped += plugin.getExecutions().size() - filtered.size();
						plugin.setExecutions(filtered);
					}
				}
			}
			JavaLanguageServerPlugin.logInfo("Lifecycle participant: stripped " + stripped + " plugin executions from "
					+ session.getProjects().size() + " projects");
		}

		List<String> getCompileOptions(String projectId) {
			return compileOptionsByProject.getOrDefault(projectId, List.of());
		}

		List<String> getTestCompileOptions(String projectId) {
			return testCompileOptionsByProject.getOrDefault(projectId, List.of());
		}

		void deactivate() {
			active = false;
			compileOptionsByProject.clear();
			testCompileOptionsByProject.clear();
		}
	}

	// ---- Reactor progress listener ----

	private static class ReactorProgressListener extends AbstractExecutionListener {

		private final SubMonitor monitor;
		private int moduleCount;
		private int currentModule;

		ReactorProgressListener(SubMonitor monitor) {
			this.monitor = monitor;
		}

		@Override
		public void sessionStarted(ExecutionEvent event) {
			List<MavenProject> projects = event.getSession().getProjects();
			moduleCount = projects.size();
			monitor.setWorkRemaining(moduleCount);
			JavaLanguageServerPlugin.logInfo("Maven reactor: " + moduleCount + " modules");
			sendProgress("Building mbt.json: " + moduleCount + " modules");
		}

		@Override
		public void projectStarted(ExecutionEvent event) {
			currentModule++;
			MavenProject p = event.getProject();
			String msg = "Building mbt.json [" + currentModule + "/" + moduleCount + "]: " + p.getArtifactId();
			monitor.subTask(msg);
			sendProgress(msg);
		}

		@Override
		public void mojoStarted(ExecutionEvent event) {
			MojoExecution mojo = event.getMojoExecution();
			String phase = mojo.getLifecyclePhase();
			String detail = phase != null ? phase + " (" + mojo.getGoal() + ")" : mojo.getGoal();
			monitor.subTask(event.getProject().getArtifactId() + ": " + detail);
		}

		@Override
		public void projectSucceeded(ExecutionEvent event) {
			monitor.worked(1);
		}

		@Override
		public void projectFailed(ExecutionEvent event) {
			monitor.worked(1);
		}

	}

	// ---- Extract classpath info (same as ClasspathExtractorMojo.execute) ----

	private static void extractFromResolvedProject(String id, MavenProject project, Path workspacePath,
			Map<String, MbtTargetOutput> targets, Map<String, MbtDependencyModuleOutput> dependencyModules,
			List<String> compileJavacOptions, List<String> testCompileJavacOptions,
			IMaven maven, SubMonitor monitor) {

		Set<Artifact> artifacts = project.getArtifacts();

		// Separate compile vs test dependencies (same as ClasspathExtractorMojo)
		List<String> compileDeps = new ArrayList<>();
		List<String> testDeps = new ArrayList<>();

		if (artifacts != null && !artifacts.isEmpty()) {
			for (Artifact dep : artifacts) {
				if (POM_TYPE.equals(dep.getType())) {
					continue;
				}
				String depId = gav(dep.getGroupId(), dep.getArtifactId(), dep.getBaseVersion());
				if (SCOPE_TEST.equals(dep.getScope())) {
					testDeps.add(depId);
				} else {
					compileDeps.add(depId);
				}
				addDependencyModule(depId, dep, dependencyModules, maven, monitor);
			}
			// Always complement Maven's resolution by reading external JARs' POMs from the
			// local repo. Maven's embedded execution may not fully resolve all transitives,
			// especially through BOM chains.
			// Example: quarkus-netty-deployment → vertx-core → netty-resolver-dns
			//          vertx-core is resolved by Maven, but netty-resolver-dns (its transitive
			//          via vertx-dependencies BOM → netty-bom) may be missed.
			resolveTransitiveDepsFromRepo(new ArrayList<>(compileDeps), compileDeps,
					dependencyModules, maven, monitor, new HashSet<>(compileDeps));
		} else {
			JavaLanguageServerPlugin.logInfo("Using POM model fallback for dependencies of " + id);
			collectDependenciesFromModel(project, compileDeps, testDeps, dependencyModules, maven, monitor);
		}

		// Main target
		MbtTargetOutput main = new MbtTargetOutput();
		main.sources = toWorkspaceRelativePaths(project.getCompileSourceRoots(), workspacePath);
		main.classes = List.of(toWorkspaceRelativePath(project.getBuild().getOutputDirectory(), workspacePath));
		main.javaHome = getJavaHome(project);
		main.compilerOptions = compileJavacOptions.isEmpty() ? null : compileJavacOptions;
		main.dependencyModules = new ArrayList<>(compileDeps);
		targets.put(id, main);

		// Test target: compile deps + main target + test deps
		MbtTargetOutput test = new MbtTargetOutput();
		test.sources = toWorkspaceRelativePaths(project.getTestCompileSourceRoots(), workspacePath);
		test.classes = List.of(toWorkspaceRelativePath(project.getBuild().getTestOutputDirectory(), workspacePath));
		test.javaHome = main.javaHome;
		test.compilerOptions = testCompileJavacOptions.isEmpty() ? null : testCompileJavacOptions;
		List<String> allTestDeps = new ArrayList<>(compileDeps);
		allTestDeps.add(id);
		allTestDeps.addAll(testDeps);
		test.dependencyModules = allTestDeps;
		targets.put(id + TEST_SCOPE_SUFFIX, test);
	}

	private static void collectDependenciesFromModel(MavenProject project,
			List<String> compileDeps, List<String> testDeps,
			Map<String, MbtDependencyModuleOutput> dependencyModules,
			IMaven maven, SubMonitor monitor) {
		// Build lookup for BOM-managed versions (groupId:artifactId → version)
		Map<String, String> managedVersions = new TreeMap<>();
		if (project.getDependencyManagement() != null) {
			for (Dependency managed : project.getDependencyManagement().getDependencies()) {
				if (managed.getVersion() != null && !managed.getVersion().isBlank()) {
					managedVersions.put(ga(managed.getGroupId(), managed.getArtifactId()), managed.getVersion());
				}
			}
		}

		for (Dependency dep : project.getDependencies()) {
			if (POM_TYPE.equals(dep.getType())) {
				continue;
			}
			String version = dep.getVersion();
			if (version == null || version.isBlank()) {
				version = managedVersions.get(ga(dep.getGroupId(), dep.getArtifactId()));
			}
			if (version == null || version.isBlank()) {
				continue;
			}
			String depId = gav(dep.getGroupId(), dep.getArtifactId(), version);
			if (SCOPE_TEST.equals(dep.getScope())) {
				testDeps.add(depId);
			} else {
				compileDeps.add(depId);
			}
			if (!dependencyModules.containsKey(depId)) {
				try {
					Artifact resolved = maven.resolve(dep.getGroupId(), dep.getArtifactId(),
							version, JAR_TYPE, dep.getClassifier(), null, monitor);
					if (resolved != null && resolved.getFile() != null && resolved.getFile().isFile()) {
						addDependencyModule(depId, resolved, dependencyModules, maven, monitor);
					}
				} catch (CoreException e) {
					// Not resolvable — reactor module handled by promoteDependencyModulesThatAreTargets
				}
			}
		}

		// Resolve transitive dependencies of external JARs from the local Maven repo
		resolveTransitiveDepsFromRepo(new ArrayList<>(compileDeps), compileDeps,
				dependencyModules, maven, monitor, new HashSet<>(compileDeps));
	}

	/**
	 * Recursively resolves transitive dependencies of external JARs by reading
	 * their POMs from the local Maven repository. Skips SNAPSHOT reactor modules.
	 */
	private static void resolveTransitiveDepsFromRepo(List<String> toProcess,
			List<String> compileDeps, Map<String, MbtDependencyModuleOutput> dependencyModules,
			IMaven maven, SubMonitor monitor, HashSet<String> visited) {
		for (String depId : toProcess) {
			String[] parts = depId.split(":");
			if (parts.length < 3) {
				continue;
			}
			// Skip SNAPSHOT reactor modules — their POMs won't have useful transitive info
			if (parts[2].endsWith(SNAPSHOT_SUFFIX)) {
				continue;
			}
			try {
				Artifact pomArtifact = maven.resolve(parts[0], parts[1], parts[2], POM_TYPE, null, null, monitor);
				if (pomArtifact == null || pomArtifact.getFile() == null || !pomArtifact.getFile().isFile()) {
					continue;
				}
				List<String> newDeps = readAndResolveTransitiveDeps(
						pomArtifact.getFile(), parts[2], compileDeps, dependencyModules,
						maven, monitor, visited);
				if (!newDeps.isEmpty()) {
					resolveTransitiveDepsFromRepo(newDeps, compileDeps, dependencyModules, maven, monitor, visited);
				}
			} catch (CoreException e) {
				// POM not available in local repo
			}
		}
	}

	/**
	 * Reads a POM file and resolves its compile-scope dependencies as transitive deps,
	 * using the full parent/BOM hierarchy for version interpolation.
	 *
	 * @return newly discovered dependency IDs that need further transitive resolution
	 */
	private static List<String> readAndResolveTransitiveDeps(File pomFile, String artifactVersion,
			List<String> compileDeps, Map<String, MbtDependencyModuleOutput> dependencyModules,
			IMaven maven, SubMonitor monitor, HashSet<String> visited) {
		List<String> newDeps = new ArrayList<>();
		try {
			Model model = new MavenXpp3Reader()
					.read(Files.newBufferedReader(pomFile.toPath(), java.nio.charset.StandardCharsets.UTF_8));

			// Inject Maven implicit properties (project.groupId, project.version, etc.)
			// BEFORE collectModelHierarchy, because BOM imports may reference them.
			// Example: vertx-core-4.5.26.pom imports BOM vertx-dependencies:${project.version}
			//          Without project.version=4.5.26 in properties, the BOM can't be resolved,
			//          and its managed versions (including netty-bom → netty-resolver-dns) are lost.
			Map<String, String> properties = new TreeMap<>();
			Map<String, String> managed = new TreeMap<>();
			String modelGroupId = model.getGroupId();
			if (modelGroupId == null && model.getParent() != null) {
				modelGroupId = model.getParent().getGroupId();
			}
			String modelVersion = model.getVersion();
			if (modelVersion == null && model.getParent() != null) {
				modelVersion = model.getParent().getVersion();
			}
			if (modelGroupId != null) {
				properties.put("project.groupId", modelGroupId);
				properties.put("pom.groupId", modelGroupId);
			}
			if (modelVersion != null) {
				properties.put("project.version", modelVersion);
				properties.put("pom.version", modelVersion);
			}
			if (model.getArtifactId() != null) {
				properties.put("project.artifactId", model.getArtifactId());
				properties.put("pom.artifactId", model.getArtifactId());
			}
			collectModelHierarchy(model, properties, managed, maven, monitor, MAX_HIERARCHY_DEPTH);

			for (Dependency dep : model.getDependencies()) {
				String scope = dep.getScope();
				if (SCOPE_TEST.equals(scope) || SCOPE_PROVIDED.equals(scope) || SCOPE_SYSTEM.equals(scope)) {
					continue;
				}
				if (POM_TYPE.equals(dep.getType())) {
					continue;
				}
				// Interpolate groupId and artifactId — many POMs use ${project.groupId}
				// Example: netty-handler-4.1.132.pom declares:
				//   <groupId>${project.groupId}</groupId>        → io.netty
				//   <artifactId>netty-resolver</artifactId>
				//   <version>${project.version}</version>        → 4.1.132.Final
				String groupId = dep.getGroupId();
				if (groupId != null && groupId.contains("${")) {
					groupId = interpolateProperties(groupId, properties);
				}
				String artifactId = dep.getArtifactId();
				if (artifactId != null && artifactId.contains("${")) {
					artifactId = interpolateProperties(artifactId, properties);
				}
				if (groupId == null || groupId.contains("${") || artifactId == null || artifactId.contains("${")) {
					continue;
				}
				String version = dep.getVersion();
				if (version != null && version.contains("${")) {
					version = interpolateProperties(version, properties);
				}
				if (version == null || version.isBlank() || version.contains("${")) {
					version = managed.get(ga(groupId, artifactId));
				}
				if (version == null || version.isBlank() || version.contains("${")) {
					version = artifactVersion;
				}
				String transDepId = gav(groupId, artifactId, version);
				if (!visited.add(transDepId)) {
					continue;
				}
				if (dependencyModules.containsKey(transDepId)) {
					// Already resolved by another project — still add to this project's deps
					compileDeps.add(transDepId);
					newDeps.add(transDepId);
				} else {
					try {
						Artifact resolved = maven.resolve(groupId, artifactId,
								version, JAR_TYPE, dep.getClassifier(), null, monitor);
						if (resolved != null && resolved.getFile() != null && resolved.getFile().isFile()) {
							compileDeps.add(transDepId);
							newDeps.add(transDepId);
							addDependencyModule(transDepId, resolved, dependencyModules, maven, monitor);
						}
					} catch (CoreException e) {
						// Not resolvable
					}
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logInfo("Could not read POM for transitive deps: " + pomFile.getName());
		}
		return newDeps;
	}

	/**
	 * Recursively collects properties and managed dependency versions from a POM,
	 * its parent chain, and BOM imports ({@code <type>pom</type><scope>import</scope>}).
	 * Results are cached per GAV to avoid redundant POM parsing.
	 *
	 * @param model    the Maven model to process
	 * @param properties accumulated properties (first-wins via {@code putIfAbsent})
	 * @param managed  accumulated managed versions keyed by {@code groupId:artifactId}
	 * @param maven    M2E Maven facade for resolving POM artifacts
	 * @param monitor  progress monitor
	 * @param maxDepth recursion guard to prevent infinite loops in cyclic parent chains
	 */
	private static void collectModelHierarchy(Model model,
			Map<String, String> properties, Map<String, String> managed,
			IMaven maven, SubMonitor monitor, int maxDepth) {
		if (maxDepth <= 0) {
			return;
		}
		String gav = pomGav(model);

		// Check cache — skip full hierarchy traversal if already computed
		Map<String, String> cachedProps = hierarchyPropsCache.get(gav);
		Map<String, String> cachedManaged = hierarchyManagedCache.get(gav);
		if (cachedProps != null && cachedManaged != null) {
			for (Map.Entry<String, String> e : cachedProps.entrySet()) {
				properties.putIfAbsent(e.getKey(), e.getValue());
			}
			for (Map.Entry<String, String> e : cachedManaged.entrySet()) {
				managed.putIfAbsent(e.getKey(), e.getValue());
			}
			return;
		}

		// Compute from scratch — use local maps so we can cache the result
		Map<String, String> localProps = new TreeMap<>();
		Map<String, String> localManaged = new TreeMap<>();

		// Collect properties
		if (model.getProperties() != null) {
			for (Map.Entry<Object, Object> entry : model.getProperties().entrySet()) {
				localProps.putIfAbsent(entry.getKey().toString(), entry.getValue().toString());
			}
		}
		// Inject Maven implicit properties so that ${project.version} etc. resolve
		// in BOM imports and dependency versions within this model.
		// Example: vertx-dependencies-4.5.26.pom has <netty.version>4.1.132.Final</netty.version>
		//          and imports netty-bom:${netty.version}. Without project.version here,
		//          the recursion into vertx-dependencies would fail to resolve its own BOMs.
		String implGroupId = model.getGroupId();
		if (implGroupId == null && model.getParent() != null) {
			implGroupId = model.getParent().getGroupId();
		}
		String implVersion = model.getVersion();
		if (implVersion == null && model.getParent() != null) {
			implVersion = model.getParent().getVersion();
		}
		if (implGroupId != null) {
			localProps.putIfAbsent("project.groupId", implGroupId);
			localProps.putIfAbsent("pom.groupId", implGroupId);
		}
		if (implVersion != null) {
			localProps.putIfAbsent("project.version", implVersion);
			localProps.putIfAbsent("pom.version", implVersion);
		}
		if (model.getArtifactId() != null) {
			localProps.putIfAbsent("project.artifactId", model.getArtifactId());
			localProps.putIfAbsent("pom.artifactId", model.getArtifactId());
		}
		// Collect managed versions and process BOM imports
		if (model.getDependencyManagement() != null) {
			List<Dependency> bomImports = new ArrayList<>();
			for (Dependency m : model.getDependencyManagement().getDependencies()) {
				if (POM_TYPE.equals(m.getType()) && SCOPE_IMPORT.equals(m.getScope())) {
					bomImports.add(m);
					continue;
				}
				String key = ga(m.getGroupId(), m.getArtifactId());
				if (localManaged.containsKey(key)) {
					continue;
				}
				String v = m.getVersion();
				if (v != null && v.contains("${")) {
					v = interpolateProperties(v, localProps);
				}
				if (v != null && !v.isBlank()) {
					localManaged.put(key, v);
				}
			}
			// Resolve BOM imports — their dependencyManagement entries become managed versions
			for (Dependency bom : bomImports) {
				String bomVersion = bom.getVersion();
				if (bomVersion != null && bomVersion.contains("${")) {
					bomVersion = interpolateProperties(bomVersion, localProps);
				}
				if (bomVersion == null || bomVersion.isBlank() || bomVersion.contains("${")) {
					continue;
				}
				try {
					Artifact bomPom = maven.resolve(bom.getGroupId(), bom.getArtifactId(),
							bomVersion, POM_TYPE, null, null, monitor);
					if (bomPom != null && bomPom.getFile() != null && bomPom.getFile().isFile()) {
						Model bomModel = new MavenXpp3Reader()
								.read(Files.newBufferedReader(bomPom.getFile().toPath(), java.nio.charset.StandardCharsets.UTF_8));
						collectModelHierarchy(bomModel, localProps, localManaged, maven, monitor, maxDepth - 1);
					}
				} catch (Exception e) {
					// BOM not available in local repo
				}
			}
		}
		// Resolve parent POM chain
		Parent parent = model.getParent();
		if (parent != null && parent.getVersion() != null && !parent.getVersion().contains("${")) {
			try {
				Artifact parentPom = maven.resolve(parent.getGroupId(), parent.getArtifactId(),
						parent.getVersion(), POM_TYPE, null, null, monitor);
				if (parentPom != null && parentPom.getFile() != null && parentPom.getFile().isFile()) {
					Model parentModel = new MavenXpp3Reader()
							.read(Files.newBufferedReader(parentPom.getFile().toPath(), java.nio.charset.StandardCharsets.UTF_8));
					collectModelHierarchy(parentModel, localProps, localManaged, maven, monitor, maxDepth - 1);
				}
			} catch (Exception e) {
				// Parent not available in local repo
			}
		}

		// Cache the full result for this GAV
		if (gav != null) {
			hierarchyPropsCache.put(gav, localProps);
			hierarchyManagedCache.put(gav, localManaged);
		}

		// Merge into caller's maps
		for (Map.Entry<String, String> e : localProps.entrySet()) {
			properties.putIfAbsent(e.getKey(), e.getValue());
		}
		for (Map.Entry<String, String> e : localManaged.entrySet()) {
			managed.putIfAbsent(e.getKey(), e.getValue());
		}
	}

	/**
	 * Extracts the GAV ({@code groupId:artifactId:version}) from a Maven model,
	 * inheriting groupId and version from the parent when not set directly.
	 *
	 * @return the GAV string, or {@code null} if any coordinate is missing
	 */
	private static String pomGav(Model model) {
		String g = model.getGroupId();
		if (g == null && model.getParent() != null) {
			g = model.getParent().getGroupId();
		}
		String v = model.getVersion();
		if (v == null && model.getParent() != null) {
			v = model.getParent().getVersion();
		}
		String a = model.getArtifactId();
		if (g == null || a == null || v == null) {
			return null;
		}
		return gav(g, a, v);
	}

	/**
	 * Resolves {@code ${property.name}} placeholders in a string using the given
	 * properties map. Supports chained properties (up to {@value #MAX_PROPERTY_ITERATIONS}
	 * iterations). Returns {@code null} if a referenced property is not found.
	 */
	private static String interpolateProperties(String value, Map<String, String> properties) {
		if (value == null || !value.contains("${")) {
			return value;
		}
		String result = value;
		int iterations = 0;
		while (result.contains("${") && iterations++ < MAX_PROPERTY_ITERATIONS) {
			int start = result.indexOf("${");
			int end = result.indexOf('}', start);
			if (end < 0) {
				break;
			}
			String propName = result.substring(start + 2, end);
			String propValue = properties.get(propName);
			if (propValue == null) {
				return null;
			}
			result = result.substring(0, start) + propValue + result.substring(end + 1);
		}
		return result;
	}

	private static void addDependencyModule(String depId, Artifact artifact,
			Map<String, MbtDependencyModuleOutput> dependencyModules, IMaven maven, SubMonitor monitor) {
		if (dependencyModules.containsKey(depId)) {
			return;
		}
		File file = artifact.getFile();
		if (file != null && !file.getName().endsWith(".jar")) {
			return;
		}
		MbtDependencyModuleOutput dep = new MbtDependencyModuleOutput();
		dep.id = depId;
		if (file != null) {
			dep.jar = file.toPath().toUri().toString();
		}
		// Resolve sources JAR (same as ClasspathExtractorParticipant)
		try {
			Artifact sourcesArtifact = maven.resolve(
					artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(),
					JAR_TYPE, SOURCES_CLASSIFIER, null, monitor);
			if (sourcesArtifact != null && sourcesArtifact.getFile() != null) {
				dep.sources = sourcesArtifact.getFile().toPath().toUri().toString();
			}
		} catch (CoreException e) {
			// Sources not available — not critical
		}
		dependencyModules.put(depId, dep);
	}

	// ---- JDK detection ----

	private static String getJavaHome(MavenProject project) {
		// TODO: Use ToolchainManager.getToolchainFromBuildContext("jdk", session)
		return System.getProperty("java.home");
	}

	// ---- Compiler options extraction (same as MavenCompilerOptionsExtractor) ----

	private static List<String> extractCompileJavacOptionsFromPlugin(Plugin plugin) {
		// Per-execution extraction (same as ClasspathExtractorParticipant)
		for (PluginExecution execution : plugin.getExecutions()) {
			if (isCompileExecution(execution)) {
				List<String> opts = extractJavacOptionsFromExecution(execution);
				if (!opts.isEmpty()) {
					return opts;
				}
			}
		}
		// Fallback: plugin-level config
		Object config = plugin.getConfiguration();
		if (config instanceof Xpp3Dom dom) {
			return extractJavacOptionsFromConfig(dom);
		}
		return List.of();
	}

	private static List<String> extractTestCompileJavacOptionsFromPlugin(Plugin plugin) {
		for (PluginExecution execution : plugin.getExecutions()) {
			if (isTestCompileExecution(execution)) {
				List<String> opts = extractJavacOptionsFromExecution(execution);
				if (!opts.isEmpty()) {
					return opts;
				}
			}
		}
		// Fallback: same as compile options
		return extractCompileJavacOptionsFromPlugin(plugin);
	}

	private static List<String> extractJavacOptionsFromExecution(PluginExecution execution) {
		Object configuration = execution.getConfiguration();
		if (configuration instanceof Xpp3Dom dom) {
			return extractJavacOptionsFromConfig(dom);
		}
		return List.of();
	}

	private static List<String> extractJavacOptionsFromConfig(Xpp3Dom config) {
		List<String> args = new ArrayList<>();

		String release = getChildValue(config, "release");
		if (release != null) {
			args.add("-release");
			args.add(release);
		}
		String source = getChildValue(config, "source");
		if (source != null) {
			args.add("-source");
			args.add(source);
		}
		String target = getChildValue(config, "target");
		if (target != null) {
			args.add("-target");
			args.add(target);
		}
		String encoding = getChildValue(config, "encoding");
		if (encoding != null) {
			args.add("-encoding");
			args.add(encoding);
		}
		args.addAll(parseCompilerArgs(config));
		return args;
	}

	private static String getChildValue(Xpp3Dom parent, String childName) {
		Xpp3Dom child = parent.getChild(childName);
		if (child == null || child.getValue() == null || child.getValue().isBlank()) {
			return null;
		}
		return child.getValue().trim();
	}

	private static List<String> parseCompilerArgs(Xpp3Dom config) {
		List<String> result = new ArrayList<>();

		String compilerArgument = getChildValue(config, "compilerArgument");
		if (compilerArgument != null) {
			result.add(compilerArgument);
			return result;
		}

		Xpp3Dom compilerArgs = config.getChild("compilerArgs");
		if (compilerArgs != null) {
			for (Xpp3Dom child : compilerArgs.getChildren()) {
				if (child != null && child.getValue() != null && !child.getValue().isBlank()) {
					result.add(child.getValue().trim());
				}
			}
			return result;
		}

		Xpp3Dom compilerArguments = config.getChild("compilerArguments");
		if (compilerArguments != null) {
			for (Xpp3Dom child : compilerArguments.getChildren()) {
				if (child == null) {
					continue;
				}
				String optName = "-" + child.getName();
				String optValue = child.getValue() != null ? child.getValue().trim() : null;
				if (optValue != null && !optValue.isEmpty()) {
					if (optName.startsWith("-A")) {
						result.add(optName + "=" + optValue);
					} else {
						result.add(optName);
						result.add(optValue);
					}
				} else {
					result.add(optName);
				}
			}
		}
		return result;
	}

	private static boolean isCompileExecution(PluginExecution execution) {
		if (execution.getGoals() != null && execution.getGoals().contains("compile")) {
			return true;
		}
		return "default-compile".equals(execution.getId()) || "compile".equals(execution.getPhase());
	}

	private static boolean isTestCompileExecution(PluginExecution execution) {
		if (execution.getGoals() != null && execution.getGoals().contains("testCompile")) {
			return true;
		}
		return "default-testCompile".equals(execution.getId()) || "test-compile".equals(execution.getPhase());
	}

	// ---- Promote workspace deps ----

	private static void promoteDependencyModulesThatAreTargets(Map<String, MbtTargetOutput> targets,
			Map<String, MbtDependencyModuleOutput> dependencyModules) {
		for (MbtTargetOutput target : targets.values()) {
			if (target.dependencyModules == null || target.dependencyModules.isEmpty()) {
				continue;
			}
			List<String> workspaceDeps = new ArrayList<>();
			for (String depId : target.dependencyModules) {
				if (targets.containsKey(depId)) {
					workspaceDeps.add(depId);
				}
			}
			if (!workspaceDeps.isEmpty()) {
				target.dependencyModules.removeAll(workspaceDeps);
				if (target.dependsOn == null) {
					target.dependsOn = workspaceDeps;
				} else {
					target.dependsOn.addAll(workspaceDeps);
				}
			}
		}
		dependencyModules.keySet().removeIf(targets::containsKey);
	}

	// ---- GAV helpers ----

	private static String gav(String groupId, String artifactId, String version) {
		return groupId + ":" + artifactId + ":" + version;
	}

	private static String ga(String groupId, String artifactId) {
		return groupId + ":" + artifactId;
	}

	// ---- Path helpers ----

	private static List<String> toWorkspaceRelativePaths(List<String> absolutePaths, Path workspacePath) {
		if (absolutePaths == null) {
			return List.of();
		}
		List<String> result = new ArrayList<>(absolutePaths.size());
		for (String absPath : absolutePaths) {
			result.add(toWorkspaceRelativePath(absPath, workspacePath));
		}
		return result;
	}

	private static String toWorkspaceRelativePath(String absolutePath, Path workspacePath) {
		Path abs = Paths.get(absolutePath).toAbsolutePath().normalize();
		Path rel = workspacePath.relativize(abs);
		return rel.toString().replace('\\', '/');
	}

	// ---- Root POM scanner ----

	private static File findRootPom(Path workspacePath) {
		File rootPom = workspacePath.resolve(POM_XML).toFile();
		if (rootPom.isFile()) {
			return rootPom;
		}
		try (var stream = Files.find(workspacePath, 3,
				(p, a) -> a.isRegularFile() && POM_XML.equals(p.getFileName().toString()))) {
			return stream.map(Path::toFile)
					.min(Comparator.comparingInt(f -> f.getAbsolutePath().length()))
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	// ---- Write mbt.json and return parsed MbtInfo ----

	private static MbtInfo writeMbtJson(Path workspacePath, Map<String, MbtTargetOutput> targets,
			Map<String, MbtDependencyModuleOutput> dependencyModules, boolean persistToFile) throws IOException {
		MbtInfoOutput mbtInfo = new MbtInfoOutput(targets, dependencyModules.values());

		// Serialize to string so we can write to disk AND parse to MbtInfo without re-reading
		StringWriter stringWriter = new StringWriter();
		try (JsonWriter writer = GSON.newJsonWriter(stringWriter)) {
			writer.setIndent("  ");
			GSON.toJson(mbtInfo, MbtInfoOutput.class, writer);
		}
		String json = stringWriter.toString();

		if (persistToFile) {
			Path mbtJsonPath = MbtJson.getPath(workspacePath);
			Files.writeString(mbtJsonPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		// Parse directly from the JSON string
		return GSON.fromJson(json, MbtInfo.class);
	}

	// ---- Output model ----

	@SuppressWarnings("unused")
	private static class MbtInfoOutput {
		Map<String, MbtTargetOutput> namespaces;
		Collection<MbtDependencyModuleOutput> dependencyModules;

		MbtInfoOutput(Map<String, MbtTargetOutput> targets, Collection<MbtDependencyModuleOutput> deps) {
			this.namespaces = targets;
			this.dependencyModules = deps;
		}
	}

	@SuppressWarnings("unused")
	private static class MbtTargetOutput {
		List<String> compilerOptions;
		String javaHome;
		List<String> sources;
		List<String> classes;
		List<String> dependencyModules;
		List<String> dependsOn;
	}

	@SuppressWarnings("unused")
	private static class MbtDependencyModuleOutput {
		String id;
		String jar;
		String sources;
	}
}
