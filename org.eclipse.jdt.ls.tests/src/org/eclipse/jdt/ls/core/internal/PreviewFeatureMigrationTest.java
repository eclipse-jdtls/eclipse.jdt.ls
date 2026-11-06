/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.UnmanagedFolderNature;
import org.eclipse.jdt.ls.core.internal.managers.UpdateClasspathJob;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PreviewFeatureMigrationTest extends AbstractInvisibleProjectBasedTest {

	@Mock
	private JavaLanguageClient migrationClient;

	private JavaClientConnection connection;
	private CompletableFuture<MessageActionItem> response;
	private final Map<String, IVMInstall> fixtureVms = new HashMap<>();
	private JDTLanguageServer previousProtocol;
	private boolean protocolReplaced;

	@BeforeEach
	public void setupMigration() {
		connection = new JavaClientConnection(migrationClient);
		response = new CompletableFuture<>();
		fixtureVm(olderVersion());
		latestVm();
	}

	@Override
	@AfterEach
	public void cleanUp() throws Exception {
		try {
			response.complete(null);
			waitForBackgroundJobs();
			super.cleanUp();
		} finally {
			try {
				for (IVMInstall vm : fixtureVms.values()) {
					vm.getVMInstallType().disposeVMInstall(vm.getId());
				}
				fixtureVms.clear();
				waitForBackgroundJobs();
			} finally {
				if (protocolReplaced) {
					JavaLanguageServerPlugin.getInstance().setProtocol(previousProtocol);
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testForcedImportWaitsForUnchangedLibraries(boolean startupImport) throws Exception {
		IJavaProject javaProject = newStaleProject();
		IPath root = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		addLibs(root.toFile().toPath());
		UpdateClasspathJob.getInstance().updateClasspath(javaProject, preferences.getReferencedLibraries());
		waitForBackgroundJobs();
		IClasspathEntry[] settledClasspath = javaProject.getRawClasspath();
		List<Integer> settledKinds = Arrays.stream(settledClasspath).map(IClasspathEntry::getEntryKind).toList();
		assertTrue(settledKinds.indexOf(IClasspathEntry.CPE_SOURCE) < settledKinds.indexOf(IClasspathEntry.CPE_LIBRARY));
		Map<String, String> originalOptions = javaProject.getOptions(false);
		connectMigrationClient();
		when(migrationClient.showMessageRequest(any())).thenReturn(response);

		Job.getJobManager().suspend();
		try {
			if (startupImport) {
				InvisibleProjectImporter importer = new InvisibleProjectImporter();
				importer.initialize(root.toFile());
				importer.importToWorkspace(monitor);
			} else {
				assertTrue(InvisibleProjectImporter.loadInvisibleProject(root.append("foo/bar/Foo.java"), root, true, monitor));
			}
			List<Integer> importedKinds = Arrays.stream(javaProject.getRawClasspath()).map(IClasspathEntry::getEntryKind).toList();
			assertTrue(importedKinds.indexOf(IClasspathEntry.CPE_LIBRARY) < importedKinds.indexOf(IClasspathEntry.CPE_SOURCE));
			assertEquals(originalOptions, javaProject.getOptions(false));
			verify(migrationClient, never()).showMessageRequest(any());
		} finally {
			Job.getJobManager().resume();
		}
		waitForBackgroundJobs();

		assertArrayEquals(settledClasspath, javaProject.getRawClasspath());
		assertEquals(originalOptions, javaProject.getOptions(false));
		ArgumentCaptor<ShowMessageRequestParams> request = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
		verify(migrationClient).showMessageRequest(request.capture());
		assertFalse(response.isDone());
		response.complete(migrateAction(request.getValue()));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertEquals(latestVm(), JavaRuntime.getVMInstall(javaProject));
		assertEquals(Arrays.stream(settledClasspath).filter(entry -> !isJreEntry(entry)).toList(),
				Arrays.stream(javaProject.getRawClasspath()).filter(entry -> !isJreEntry(entry)).toList());
		verify(migrationClient, never()).showMessage(argThat(message -> message.getType() == MessageType.Warning));
	}

	@Test
	public void testFailedLibraryPassStillOffersFreshPreviewConsent() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IPath root = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		addLibs(root.toFile().toPath());
		UpdateClasspathJob.getInstance().updateClasspath(javaProject, preferences.getReferencedLibraries());
		waitForBackgroundJobs();
		Map<String, String> originalOptions = javaProject.getOptions(false);
		CoreException failure = new CoreException(StatusFactory.newErrorStatus("Unrelated library scan failure."));
		connectMigrationClient();
		when(migrationClient.showMessageRequest(any())).thenReturn(response);
		CompletableFuture<Void> update;
		IClasspathEntry[] settledClasspath;

		Job.getJobManager().suspend();
		try {
			update = failImportedLibraryPass(javaProject, failure);
			settledClasspath = javaProject.getRawClasspath();
			assertSame(failure, assertThrows(CompletionException.class, update::join).getCause());
			verify(migrationClient, never()).showMessageRequest(any());
		} finally {
			Job.getJobManager().resume();
		}
		waitForBackgroundJobs();

		assertTrue(javaProject.getProject().isAccessible());
		assertArrayEquals(settledClasspath, javaProject.getRawClasspath());
		assertEquals(originalOptions, javaProject.getOptions(false));
		assertTrue(logListener.getErrors().stream().anyMatch(error -> error.contains("Failed to update libraries before preview reconciliation")));
		ArgumentCaptor<ShowMessageRequestParams> request = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
		verify(migrationClient).showMessageRequest(request.capture());
		assertFalse(response.isDone());
		response.complete(migrateAction(request.getValue()));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertEquals(latestVm(), JavaRuntime.getVMInstall(javaProject));
		assertEquals(Arrays.stream(settledClasspath).filter(entry -> !isJreEntry(entry)).toList(),
				Arrays.stream(javaProject.getRawClasspath()).filter(entry -> !isJreEntry(entry)).toList());
		assertTrue(update.isCompletedExceptionally(), "Preview consent must not turn the library failure into success.");
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testFailedLibraryPassDoesNotPromptUnavailableProject(boolean delete) throws Exception {
		IJavaProject javaProject = newStaleProject();
		IProject project = javaProject.getProject();
		CoreException failure = new CoreException(StatusFactory.newErrorStatus("Unrelated library scan failure."));
		connectMigrationClient();

		Job.getJobManager().suspend();
		try {
			failImportedLibraryPass(javaProject, failure);
			if (delete) {
				project.delete(true, monitor);
			} else {
				project.close(monitor);
			}
		} finally {
			Job.getJobManager().resume();
		}
		waitForBackgroundJobs();

		assertFalse(project.isAccessible());
		assertEquals(!delete, project.exists());
		verify(migrationClient, never()).showMessageRequest(any());
	}

	@Test
	public void testCanceledLibraryPassStopsBeforeConsentAndAllowsExplicitRetry() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IPath root = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		Map<String, String> originalOptions = javaProject.getOptions(false);
		connectMigrationClient();
		IClasspathEntry[] importedClasspath;

		Job.getJobManager().suspend();
		try {
			assertTrue(InvisibleProjectImporter.loadInvisibleProject(root.append("foo/bar/Foo.java"), root, true, monitor));
			importedClasspath = javaProject.getRawClasspath();
			CompletableFuture<Void> update = UpdateClasspathJob.getInstance().updateClasspathAsync(javaProject, preferences.getReferencedLibraries());
			NullProgressMonitor canceledMonitor = new NullProgressMonitor();
			canceledMonitor.setCanceled(true);
			assertEquals(IStatus.CANCEL, UpdateClasspathJob.getInstance().runInWorkspace(canceledMonitor).getSeverity());
			assertThrows(CancellationException.class, update::join);
		} finally {
			Job.getJobManager().resume();
		}
		waitForBackgroundJobs();

		assertEquals(originalOptions, javaProject.getOptions(false));
		assertArrayEquals(importedClasspath, javaProject.getRawClasspath());
		verify(migrationClient, never()).showMessageRequest(any());
		ShowMessageRequestParams retry = requestChoice(javaProject);
		assertFalse(response.isDone());
		response.complete(migrateAction(retry));
		waitForBackgroundJobs();
		assertLatestPreview(javaProject);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testCanceledLibraryFailureDoesNotOfferConsent(boolean operationCanceled) throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> originalOptions = javaProject.getOptions(false);
		Throwable failure = operationCanceled ? new OperationCanceledException() : new CoreException(Status.CANCEL_STATUS);
		connectMigrationClient();
		IClasspathEntry[] importedClasspath;

		Job.getJobManager().suspend();
		try {
			CompletableFuture<Void> update = failImportedLibraryPass(javaProject, failure);
			assertSame(failure, update.handle((result, error) -> error).join());
			importedClasspath = javaProject.getRawClasspath();
		} finally {
			Job.getJobManager().resume();
		}
		waitForBackgroundJobs();

		assertEquals(originalOptions, javaProject.getOptions(false));
		assertArrayEquals(importedClasspath, javaProject.getRawClasspath());
		verify(migrationClient, never()).showMessageRequest(any());
	}

	@Test
	public void testStalePreviewIsUnchangedUntilConsent() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();

		ShowMessageRequestParams request = requestChoice(javaProject);

		assertEquals(MessageType.Error, request.getType());
		assertTrue(request.getMessage().contains("project JDK " + olderVersion()));
		assertTrue(request.getMessage().contains("Earlier preview syntax and APIs"));
		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		assertFalse(response.isDone());
	}

	@Test
	public void testMigrationPreservesPreferencesAndJreAttributes() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IVMInstall globalVm = JavaRuntime.getDefaultVMInstall();
		Map<String, String> options = javaProject.getOptions(false);
		options.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);
		javaProject.setOptions(options);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		for (int i = 0; i < classpath.length; i++) {
			IClasspathEntry entry = classpath[i];
			if (isJreEntry(entry)) {
				classpath[i] = JavaCore.newContainerEntry(entry.getPath(),
						new IAccessRule[] { JavaCore.newAccessRule(new Path("sun/**"), IAccessRule.K_NON_ACCESSIBLE) },
						new IClasspathAttribute[] { JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true"),
								JavaCore.newClasspathAttribute("preview-test", "preserve") }, true);
			}
		}
		javaProject.setRawClasspath(classpath, monitor);
		IClasspathEntry originalJre = jreEntry(javaProject);

		ShowMessageRequestParams request = requestChoice(javaProject);
		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertEquals(latestVm(), JavaRuntime.getVMInstall(javaProject));
		assertEquals(globalVm, JavaRuntime.getDefaultVMInstall());
		assertEquals(JavaCore.WARNING, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, true));
		assertEquals(JavaCore.ERROR, javaProject.getOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, true));
		IClasspathEntry newJre = jreEntry(javaProject);
		assertArrayEquals(originalJre.getExtraAttributes(), newJre.getExtraAttributes());
		assertArrayEquals(originalJre.getAccessRules(), newJre.getAccessRules());
		assertEquals(originalJre.isExported(), newJre.isExported());
		assertEquals(Arrays.stream(classpath).filter(entry -> !isJreEntry(entry)).toList(),
				Arrays.stream(javaProject.getRawClasspath()).filter(entry -> !isJreEntry(entry)).toList());
	}

	@Test
	public void testAlreadyMatchingVmStillRequiresConsent() throws Exception {
		IJavaProject javaProject = newStaleProject();
		setProjectVm(javaProject, latestVm());
		IClasspathEntry[] classpath = javaProject.getRawClasspath();

		ShowMessageRequestParams request = requestChoice(javaProject);
		assertEquals(olderVersion(), javaProject.getOption(JavaCore.COMPILER_SOURCE, true));
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true));
		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertArrayEquals(classpath, javaProject.getRawClasspath());
	}

	@Test
	public void testSourceMismatchIsDetectedWithLatestCompliance() throws Exception {
		IJavaProject javaProject = newStaleProject();
		setProjectVm(javaProject, latestVm());
		javaProject.setOption(JavaCore.COMPILER_COMPLIANCE, JavaCore.latestSupportedJavaVersion());
		javaProject.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.latestSupportedJavaVersion());

		ShowMessageRequestParams request = requestChoice(javaProject);
		assertTrue(request.getMessage().contains("Java source " + olderVersion()));
		assertEquals(olderVersion(), javaProject.getOption(JavaCore.COMPILER_SOURCE, true));
	}

	@Test
	public void testLatestSourceWithStaleComplianceAndTargetRequiresConsent() throws Exception {
		IJavaProject javaProject = newStaleProject();
		setProjectVm(javaProject, latestVm());
		javaProject.setOption(JavaCore.COMPILER_SOURCE, JavaCore.latestSupportedJavaVersion());

		requestChoice(javaProject);

		assertEquals(olderVersion(), javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true));
		assertEquals(olderVersion(), javaProject.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, true));
	}

	@Test
	public void testDisabledPreviewDoesNotPrompt() throws Exception {
		IJavaProject javaProject = newStaleProject();
		javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
		Map<String, String> options = javaProject.getOptions(false);

		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));

		assertEquals(options, javaProject.getOptions(false));
		verifyNoInteractions(migrationClient);
	}

	@Test
	public void testValidLatestPreviewIsUntouched() throws Exception {
		IJavaProject javaProject = newStaleProject();
		setProjectVm(javaProject, latestVm());
		Map<String, String> options = javaProject.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.latestSupportedJavaVersion(), options);
		options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		javaProject.setOptions(options);

		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));
		assertLatestPreview(javaProject);
		verifyNoInteractions(migrationClient);
	}

	@Test
	public void testNoConfiguredMatchingVmOffersExistingSettingsAction() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		IVMInstall currentVm = JavaRuntime.getVMInstall(javaProject);
		IVMInstallType onlyOldVm = mock(IVMInstallType.class);
		when(onlyOldVm.getVMInstalls()).thenReturn(new IVMInstall[] { currentVm });

		try (MockedStatic<JavaRuntime> runtimes = mockStatic(JavaRuntime.class, CALLS_REAL_METHODS)) {
			runtimes.when(() -> JavaRuntime.getVMInstall(javaProject)).thenReturn(currentVm);
			runtimes.when(JavaRuntime::getVMInstallTypes).thenReturn(new IVMInstallType[] { onlyOldVm });
			ShowMessageRequestParams request = requestChoice(javaProject);
			assertTrue(request.getMessage().contains("No configured Java " + JavaCore.latestSupportedJavaVersion() + " JDK"));
			assertTrue(request.getActions().stream().noneMatch(action -> action.getTitle().startsWith("Use Java ")));
			response.complete(action(request, "Configure Java Runtimes"));
		}
		waitForBackgroundJobs();

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verify(migrationClient).sendNotification(argThat(params -> "java.runtimeValidation.open".equals(params.getCommand())
				&& params.getArguments() != null && params.getArguments().isEmpty()));
	}

	@Test
	public void testOnlyExactMajorMatchingVmsAreOffered() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IVMInstallType type = JavaRuntime.getVMInstallType(TestVMType.VMTYPE_ID);
		String newerVersion = Integer.toString(Integer.parseInt(JavaCore.latestSupportedJavaVersion()) + 1);
		IVMInstall newerVm = type.createVMInstall(newerVersion);
		newerVm.setInstallLocation(latestVm().getInstallLocation());
		newerVm.setName("higher-version-test");
		assertTrue(newerVm.getInstallLocation().isDirectory());
		assertTrue(type.validateInstallLocation(newerVm.getInstallLocation()).isOK());
		try {
			setProjectVm(javaProject, newerVm);
			Map<String, String> options = javaProject.getOptions(false);
			JavaCore.setComplianceOptions(JavaCore.latestSupportedJavaVersion(), options);
			options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
			javaProject.setOptions(options);

			ShowMessageRequestParams request = requestChoice(javaProject);
			assertTrue(request.getMessage().contains("project JDK " + newerVersion));
			List<MessageActionItem> migrations = request.getActions().stream().filter(item -> item.getTitle().startsWith("Use Java ")).toList();
			assertFalse(migrations.isEmpty());
			assertTrue(migrations.stream().noneMatch(item -> item.getTitle().contains("higher-version-test")));
			assertTrue(migrations.stream().noneMatch(item -> item.getTitle().contains("(null - ")));
			assertTrue(migrations.get(0).getTitle().startsWith("Use Java " + JavaCore.latestSupportedJavaVersion() + " "));
		} finally {
			response.complete(null);
			type.disposeVMInstall(newerVersion);
		}
	}

	@Test
	public void testUnresolvedProjectVmDoesNotFallBackToServerVm() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);

		try (MockedStatic<JavaRuntime> runtimes = mockStatic(JavaRuntime.class, CALLS_REAL_METHODS)) {
			runtimes.when(() -> JavaRuntime.getVMInstall(javaProject)).thenReturn(null);
			ShowMessageRequestParams request = requestChoice(javaProject);
			assertTrue(request.getMessage().contains("project JDK unknown"));
		}
		assertEquals(options, javaProject.getOptions(false));
	}

	@Test
	public void testDisableOnlyChangesPreviewEnablement() throws Exception {
		IJavaProject javaProject = newStaleProject();
		javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.WARNING);
		Map<String, String> expected = new HashMap<>(javaProject.getOptions(false));
		expected.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		IVMInstall vm = JavaRuntime.getVMInstall(javaProject);

		ShowMessageRequestParams request = requestChoice(javaProject);
		response.complete(action(request, "Disable Preview Features"));
		waitForBackgroundJobs();

		assertEquals(expected, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		assertEquals(vm, JavaRuntime.getVMInstall(javaProject));
	}

	@Test
	public void testCancellationAndReimportDoNotMutateOrRepeat() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		requestChoice(javaProject);
		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));
		response.complete(null);

		javaProject.getProject().close(monitor);
		javaProject.getProject().open(monitor);
		reimport(javaProject);

		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));
		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verify(migrationClient, times(1)).showMessageRequest(any());
	}

	@Test
	public void testConcurrentSettingsEditIsNotOverwritten() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		ShowMessageRequestParams request = requestChoice(javaProject);
		javaProject.setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);
		Map<String, String> editedOptions = javaProject.getOptions(false);

		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertEquals(editedOptions, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verify(migrationClient).showMessage(argThat(message -> message.getType() == MessageType.Warning && message.getMessage().contains("changed")));
	}

	@Test
	public void testConcurrentRuntimeEditIsNotOverwritten() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);
		ShowMessageRequestParams request = requestChoice(javaProject);
		IVMInstall changedVm = JavaRuntime.getVMInstallType(TestVMType.VMTYPE_ID).findVMInstall(JavaCore.VERSION_21);
		setProjectVm(javaProject, changedVm);
		IClasspathEntry[] changedClasspath = javaProject.getRawClasspath();

		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(changedClasspath, javaProject.getRawClasspath());
		assertEquals(changedVm, JavaRuntime.getVMInstall(javaProject));
	}

	@Test
	public void testLaterLibraryEditRejectsChoiceAndAllowsFreshConsent() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IPath root = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		addLibs(root.toFile().toPath());
		UpdateClasspathJob.getInstance().updateClasspath(javaProject, preferences.getReferencedLibraries());
		waitForBackgroundJobs();
		Map<String, String> options = javaProject.getOptions(false);
		IVMInstall currentVm = JavaRuntime.getVMInstall(javaProject);
		ShowMessageRequestParams request = requestChoice(javaProject);
		long originalStamp = javaProject.getProject().getFile(".classpath").getModificationStamp();
		IClasspathEntry[] editedClasspath = Arrays.stream(javaProject.getRawClasspath())
				.filter(entry -> entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY).toArray(IClasspathEntry[]::new);
		javaProject.setRawClasspath(editedClasspath, monitor);
		assertNotEquals(originalStamp, javaProject.getProject().getFile(".classpath").getModificationStamp());

		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(editedClasspath, javaProject.getRawClasspath());
		assertEquals(currentVm, JavaRuntime.getVMInstall(javaProject));
		verify(migrationClient).showMessage(argThat(message -> message.getType() == MessageType.Warning && message.getMessage().contains("changed")));
		response = new CompletableFuture<>();
		ShowMessageRequestParams retry = requestChoice(javaProject, 2);
		assertFalse(response.isDone());
		assertEquals(options, javaProject.getOptions(false));
		response.complete(migrateAction(retry));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertEquals(latestVm(), JavaRuntime.getVMInstall(javaProject));
		assertEquals(Arrays.stream(editedClasspath).filter(entry -> !isJreEntry(entry)).toList(),
				Arrays.stream(javaProject.getRawClasspath()).filter(entry -> !isJreEntry(entry)).toList());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void testUnavailableProjectIsNotReopenedByPendingChoice(boolean delete) throws Exception {
		IJavaProject javaProject = newStaleProject();
		IProject project = javaProject.getProject();
		ShowMessageRequestParams request = requestChoice(javaProject);
		if (delete) {
			project.delete(true, monitor);
		} else {
			project.close(monitor);
		}

		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertFalse(project.isAccessible());
		assertEquals(!delete, project.exists());
		verify(migrationClient).showMessage(argThat(message -> message.getType() == MessageType.Warning));
	}

	@Test
	public void testLinkedUserSettingsAreUntouched() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IFolder settings = javaProject.getProject().getFolder(ProjectUtils.SETTINGS);
		settings.delete(true, monitor);
		java.nio.file.Path userSettings = Files.createDirectories(getWorkingProjectDirectory().toPath().resolve("user-settings"));
		settings.createLink(userSettings.toUri(), IResource.REPLACE, monitor);
		setStaleOptions(javaProject);
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		assertTrue(ProjectUtils.isSettingsFolderLinked(javaProject.getProject()));

		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verifyNoInteractions(migrationClient);
	}

	@ParameterizedTest
	@ValueSource(strings = { "maven/salut-java11", "gradle/gradle-11" })
	public void testManagedProjectsAreUntouched(String fixture) throws Exception {
		IJavaProject javaProject = importProjects(fixture).stream().filter(ProjectUtils::isJavaProject)
				.filter(project -> !ProjectsManager.DEFAULT_PROJECT_NAME.equals(project.getName()))
				.map(JavaCore::create).findFirst().orElseThrow();
		setStaleOptions(javaProject);
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();

		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verifyNoInteractions(migrationClient);
	}

	@ParameterizedTest
	@ValueSource(strings = { IMavenConstants.NATURE_ID, GradleProjectNature.ID })
	public void testMixedManagedAndUnmanagedNaturesAreExcluded(String managedNature) throws Exception {
		IJavaProject actualProject = newStaleProject();
		IProject project = mock(IProject.class);
		when(project.getName()).thenReturn(actualProject.getElementName());
		when(project.getLocation()).thenReturn(actualProject.getProject().getLocation());
		when(project.isAccessible()).thenReturn(true);
		when(project.hasNature(anyString())).thenAnswer(invocation -> {
			String nature = invocation.getArgument(0);
			return UnmanagedFolderNature.NATURE_ID.equals(nature) || managedNature.equals(nature);
		});
		IJavaProject javaProject = mock(IJavaProject.class);
		when(javaProject.exists()).thenReturn(true);
		when(javaProject.getProject()).thenReturn(project);
		assertTrue(ProjectUtils.isUnmanagedFolder(project));

		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));

		verify(javaProject, never()).getOptions(anyBoolean());
		verifyNoInteractions(migrationClient);
	}

	@Test
	public void testMigrationCompilesRealPreviewSourceAndPersists() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IProject project = javaProject.getProject();
		IFile source = project.getFolder(ProjectUtils.WORKSPACE_LINK).getFile("foo/bar/Foo.java");
		ResourceUtils.setContent(source, """
				package foo.bar;
				public class Foo {
					public static int preview(Object value) {
						return value instanceof int result ? result : -1;
					}
				}
				""");
		project.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
		project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
		IFile classFile = ResourcesPlugin.getWorkspace().getRoot().getFile(javaProject.getOutputLocation().append("foo/bar/Foo.class"));
		assertFalse(classFile.exists());

		ShowMessageRequestParams request = requestChoice(javaProject);
		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertNoErrors(project);
		assertTrue(classFile.exists(), "Migration must produce a class file, not merely change options.");
		try (InputStream contents = classFile.getContents()) {
			byte[] header = contents.readNBytes(8);
			assertEquals(65535, (header[4] & 0xff) << 8 | header[5] & 0xff, "The source must really require preview.");
			assertEquals(Integer.parseInt(JavaCore.latestSupportedJavaVersion()) + 44, (header[6] & 0xff) << 8 | header[7] & 0xff);
		}

		project.close(monitor);
		project.open(monitor);
		reimport(javaProject);
		assertLatestPreview(javaProject);
		assertEquals(latestVm(), JavaRuntime.getVMInstall(javaProject));
		assertFalse(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));

		javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
		project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		assertHasErrors(project);
	}

	@Test
	public void testIncompatibleEarlierPreviewSourceRemainsAnError() throws Exception {
		IJavaProject javaProject = newStaleProject();
		IProject project = javaProject.getProject();
		ResourceUtils.setContent(project.getFolder(ProjectUtils.WORKSPACE_LINK).getFile("foo/bar/Foo.java"), """
				package foo.bar;
				import static java.lang.StringTemplate.STR;
				public class Foo {
					String text = STR."hello";
				}
				""");

		ShowMessageRequestParams request = requestChoice(javaProject);
		response.complete(migrateAction(request));
		waitForBackgroundJobs();

		assertLatestPreview(javaProject);
		assertHasErrors(project);
		assertTrue(ResourceUtils.getErrorMarkers(project).stream()
				.noneMatch(marker -> marker.getAttribute(IJavaModelMarker.ID, 0) == IProblem.PreviewFeaturesNotAllowed));
	}

	@Test
	public void testUnavailableChosenVmDoesNotChangeTheProject() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		IVMInstall currentVm = JavaRuntime.getVMInstall(javaProject);
		AbstractVMInstall candidate = mock(AbstractVMInstall.class);
		IVMInstallType type = mock(IVMInstallType.class);
		when(type.getVMInstalls()).thenReturn(new IVMInstall[] { candidate });
		when(candidate.getJavaVersion()).thenReturn(JavaCore.latestSupportedJavaVersion());
		when(candidate.getInstallLocation()).thenReturn(latestVm().getInstallLocation());
		when(candidate.getVMInstallType()).thenReturn(type);
		when(candidate.getId()).thenReturn("preview-candidate");
		when(candidate.getName()).thenReturn("preview-candidate");
		when(type.findVMInstall("preview-candidate")).thenReturn(candidate);
		when(type.validateInstallLocation(candidate.getInstallLocation())).thenReturn(Status.OK_STATUS);

		ShowMessageRequestParams request;
		try (MockedStatic<JavaRuntime> runtimes = mockStatic(JavaRuntime.class, CALLS_REAL_METHODS)) {
			runtimes.when(() -> JavaRuntime.getVMInstall(javaProject)).thenReturn(currentVm);
			runtimes.when(JavaRuntime::getVMInstallTypes).thenReturn(new IVMInstallType[] { type });
			request = requestChoice(javaProject);
		}
		when(type.findVMInstall("preview-candidate")).thenReturn(null);
		response.complete(migrateAction(request, candidate));
		waitForBackgroundJobs();

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verify(migrationClient).showMessage(argThat(message -> message.getType() == MessageType.Error && message.getMessage().contains("no longer available")));
	}

	@Test
	public void testRequestFailureIsReportedWithoutChangingSettings() throws Exception {
		IJavaProject javaProject = newStaleProject();
		Map<String, String> options = javaProject.getOptions(false);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		requestChoice(javaProject);

		response.completeExceptionally(new IllegalStateException("Client disconnected"));

		assertEquals(options, javaProject.getOptions(false));
		assertArrayEquals(classpath, javaProject.getRawClasspath());
		verify(migrationClient).showMessage(argThat(message -> message.getType() == MessageType.Error && message.getMessage().contains("Client disconnected")));
	}

	private IJavaProject newStaleProject() throws Exception {
		IJavaProject javaProject = JavaCore.create(copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java"));
		setProjectVm(javaProject, fixtureVm(olderVersion()));
		setStaleOptions(javaProject);
		return javaProject;
	}

	private void setStaleOptions(IJavaProject javaProject) {
		Map<String, String> options = javaProject.getOptions(false);
		JavaCore.setComplianceOptions(olderVersion(), options);
		options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		javaProject.setOptions(options);
	}

	private void setProjectVm(IJavaProject javaProject, IVMInstall vm) throws Exception {
		assertNotNull(vm);
		IPath jrePath = vm.equals(JavaRuntime.getDefaultVMInstall()) ? JavaRuntime.newDefaultJREContainerPath() : JavaRuntime.newJREContainerPath(vm);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		for (int i = 0; i < classpath.length; i++) {
			if (isJreEntry(classpath[i])) {
				classpath[i] = JavaCore.newContainerEntry(jrePath,
						classpath[i].getAccessRules(), classpath[i].getExtraAttributes(), classpath[i].isExported());
			}
		}
		javaProject.setRawClasspath(classpath, monitor);
	}

	private ShowMessageRequestParams requestChoice(IJavaProject javaProject) throws Exception {
		return requestChoice(javaProject, 1);
	}

	private ShowMessageRequestParams requestChoice(IJavaProject javaProject, int requestCount) throws Exception {
		when(migrationClient.showMessageRequest(any())).thenReturn(response);
		assertTrue(JVMConfigurator.reconcilePreviewFeatureSettings(javaProject, connection));
		ArgumentCaptor<ShowMessageRequestParams> request = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
		verify(migrationClient, times(requestCount)).showMessageRequest(request.capture());
		return request.getValue();
	}

	private void connectMigrationClient() {
		var protocol = JavaLanguageServerPlugin.getInstance().getProtocol();
		assertTrue(protocol == null || protocol instanceof JDTLanguageServer);
		previousProtocol = protocol instanceof JDTLanguageServer server ? server : null;
		JDTLanguageServer server = mock(JDTLanguageServer.class);
		lenient().when(server.getClientConnection()).thenReturn(connection);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
		protocolReplaced = true;
	}

	private CompletableFuture<Void> failImportedLibraryPass(IJavaProject javaProject, Throwable failure) throws Exception {
		IPath root = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		assertTrue(InvisibleProjectImporter.loadInvisibleProject(root.append("foo/bar/Foo.java"), root, true, monitor));
		var libraries = preferences.getReferencedLibraries();
		CompletableFuture<Void> update = UpdateClasspathJob.getInstance().updateClasspathAsync(javaProject, libraries);
		try (MockedStatic<ProjectUtils> utils = mockStatic(ProjectUtils.class, CALLS_REAL_METHODS)) {
			utils.when(() -> ProjectUtils.collectBinaries(root, libraries.getInclude(), libraries.getExclude(), monitor)).thenThrow(failure);
			assertSame(failure, assertThrows(failure.getClass(), () -> UpdateClasspathJob.getInstance().runInWorkspace(monitor)));
		}
		return update;
	}

	private MessageActionItem migrateAction(ShowMessageRequestParams request) {
		return migrateAction(request, latestVm());
	}

	private MessageActionItem migrateAction(ShowMessageRequestParams request, IVMInstall vm) {
		String prefix = "Use Java " + JavaCore.latestSupportedJavaVersion() + " and Keep Preview (" + vm.getName() + " - ";
		return request.getActions().stream().filter(item -> item.getTitle().startsWith(prefix)).findFirst().orElseThrow();
	}

	private MessageActionItem action(ShowMessageRequestParams request, String title) {
		return request.getActions().stream().filter(item -> title.equals(item.getTitle())).findFirst().orElseThrow();
	}

	private IVMInstall latestVm() {
		return fixtureVm(JavaCore.latestSupportedJavaVersion());
	}

	private IVMInstall fixtureVm(String version) {
		// Explicit JRE containers need names; leave the shared, unnamed fake VMs unchanged.
		return fixtureVms.computeIfAbsent(version, release -> {
			IVMInstallType type = JavaRuntime.getVMInstallType(TestVMType.VMTYPE_ID);
			String id = release + "-previewMigration";
			assertNull(type.findVMInstall(id));
			IVMInstall vm = type.createVMInstall(id);
			vm.setInstallLocation(new File(TestVMType.getFakeJDKsLocation(), release));
			vm.setName("Preview migration Java " + release);
			return vm;
		});
	}

	private String olderVersion() {
		List<String> versions = JavaCore.getAllVersions();
		return versions.get(versions.size() - 2);
	}

	private boolean isJreEntry(IClasspathEntry entry) {
		return entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER && JavaRuntime.JRE_CONTAINER.equals(entry.getPath().segment(0));
	}

	private IClasspathEntry jreEntry(IJavaProject javaProject) throws Exception {
		return Arrays.stream(javaProject.getRawClasspath()).filter(this::isJreEntry).findFirst().orElseThrow();
	}

	private void reimport(IJavaProject javaProject) throws Exception {
		IPath root = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
		assertTrue(InvisibleProjectImporter.loadInvisibleProject(root.append("foo/bar/Foo.java"), root, false, monitor));
		waitForBackgroundJobs();
	}

	private void assertLatestPreview(IJavaProject javaProject) {
		String latest = JavaCore.latestSupportedJavaVersion();
		assertEquals(latest, javaProject.getOption(JavaCore.COMPILER_SOURCE, true));
		assertEquals(latest, javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true));
		assertEquals(latest, javaProject.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, true));
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true));
	}
}
