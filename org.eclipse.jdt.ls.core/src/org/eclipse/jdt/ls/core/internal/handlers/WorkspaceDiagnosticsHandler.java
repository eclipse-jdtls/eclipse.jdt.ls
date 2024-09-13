/*******************************************************************************
 * Copyright (c) 2016-2021 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.internal.resources.CheckMissingNaturesListener;
import org.eclipse.core.internal.resources.ValidateProjectEncoding;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.Messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Listens to the resource change events and converts {@link IMarker}s to {@link Diagnostic}s.
 *
 * @author Gorkem Ercan
 *
 */
@SuppressWarnings("restriction")
public final class WorkspaceDiagnosticsHandler implements IResourceChangeListener, IResourceDeltaVisitor {

	public static final String PROJECT_CONFIGURATION_IS_NOT_UP_TO_DATE_WITH_POM_XML = "Project configuration is not up-to-date with pom.xml, requires an update.";
	private final JavaClientConnection connection;
	private final ProjectsManager projectsManager;
	private final boolean isDiagnosticTagSupported;
	private final DocumentLifeCycleHandler handler;

	@Deprecated
	public WorkspaceDiagnosticsHandler(JavaClientConnection connection, ProjectsManager projectsManager) {
		this(connection, projectsManager, null);
	}

	public WorkspaceDiagnosticsHandler(JavaClientConnection connection, ProjectsManager projectsManager, ClientPreferences prefs) {
		this(connection, projectsManager, prefs, null);
	}

	public WorkspaceDiagnosticsHandler(JavaClientConnection connection, ProjectsManager projectsManager, ClientPreferences prefs, DocumentLifeCycleHandler handler) {
		this.connection = connection;
		this.projectsManager = projectsManager;
		this.isDiagnosticTagSupported = prefs != null ? prefs.isDiagnosticTagSupported() : false;
		this.handler = handler;
	}

	public void addResourceChangeListener() {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}

	public void removeResourceChangeListener() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			IResourceDelta delta = event.getDelta();
			delta.accept(this);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("failed to send diagnostics", e);
		}

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.
	 * resources.IResourceDelta)
	 */
	@Override
	public boolean visit(IResourceDelta delta) throws CoreException {
		if (delta.getFlags() == IResourceDelta.MARKERS && Arrays.stream(delta.getMarkerDeltas()).map(IMarkerDelta::getMarker).noneMatch(WorkspaceDiagnosticsHandler::isInteresting)) {
			return false;
		}
		IResource resource = delta.getResource();
		if (resource == null) {
			return false;
		}
		if (resource.getType() == IResource.FOLDER || resource.getType() == IResource.ROOT) {
			return true;
		}
		// WorkspaceEventsHandler only handles the case of deleting the specific file and removes it's diagnostics.
		// If delete a folder directly, no way to clean up the diagnostics for it's children.
		// The resource delta visitor will make sure to clean up all stale diagnostics.
		if (!resource.isAccessible()) { // Check if resource is accessible.
			if (isSupportedDiagnosticsResource(resource)) {
				cleanUpDiagnostics(resource);
				return resource.getType() == IResource.PROJECT;
			}

			// If delete a project folder directly, make sure to clean up its build file diagnostics.
			if (projectsManager.isBuildLikeFileName(resource.getName())) {
				cleanUpDiagnostics(resource);
				if(!resource.getParent().isAccessible()) { // Clean up the project folder diagnostics.
					cleanUpDiagnostics(resource.getParent(), Platform.OS_WIN32.equals(Platform.getOS()));
				}
			}

			return false;
		}
		if (resource.getType() == IResource.PROJECT) {
			// ignore problems caused by standalone files (problems in the default project)
			if (JavaLanguageServerPlugin.getProjectsManager().getDefaultProject().equals(resource.getProject())) {
				return false;
			}
			IProject project = (IProject) resource;
			// report problems for other projects
			IMarker[] markers = project.findMarkers(null, true, IResource.DEPTH_ZERO);
			publishMarkers(project, markers);
			return true;
		}
		// No marker changes continue to visit
		if ((delta.getFlags() & IResourceDelta.MARKERS) == 0) {
			return false;
		}
		IFile file = (IFile) resource;
		IDocument document = null;
		IMarker[] markers = null;
		// Check if it is a Java ...
		if (JavaCore.isJavaLikeFileName(file.getName())) {
			ICompilationUnit cu = (ICompilationUnit) JavaCore.create(file);
			// Clear the diagnostics for the resource not on the classpath
			IJavaProject javaProject = cu.getJavaProject();
			if (javaProject == null || !javaProject.isOnClasspath(cu)) {
				String uri = JDTUtils.getFileURI(resource);
				this.connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), Collections.emptyList()));
				return false;
			}
			if (!cu.isWorkingCopy()) {
				markers = resource.findMarkers(null, false, IResource.DEPTH_ONE);
				try {
					document = JsonRpcHelpers.toDocument(cu.getBuffer());
				} catch (JavaModelException e) {
					// do nothing
				}
			} else if (handler != null) {
				handler.triggerValidation(cu);
			}
		} // or a build file
		else if (projectsManager.isBuildFile(file)) {
			//all errors on that build file should be relevant
			markers = file.findMarkers(null, true, 1);
			document = JsonRpcHelpers.toDocument(file);
		}
		if (document != null) {
			String uri = JDTUtils.getFileURI(resource);
			if (!BaseDiagnosticsHandler.matchesDiagnosticFilter(uri, JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getDiagnosticFilter())) {
				this.connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), toDiagnosticsArray(document, markers, isDiagnosticTagSupported)));
			}
		}
		return false;
	}

	private void publishMarkers(IProject project, IMarker[] markers) throws CoreException {
		Range range = new Range(new Position(0, 0), new Position(0, 0));

		List<IMarker> projectMarkers = new ArrayList<>(markers.length);

		String uri = JDTUtils.getFileURI(project);
		if (BaseDiagnosticsHandler.matchesDiagnosticFilter(uri, JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getDiagnosticFilter())) {
			return;
		}
		IFile pom = project.getFile("pom.xml");
		IFile gradleWrapperProperties = project.getFile(GradleProjectImporter.GRADLE_WRAPPER_PROPERTIES_DESCRIPTOR);
		List<IMarker> pomMarkers = new ArrayList<>();
		List<IMarker> gradleMarkers = new ArrayList<>();
		for (IMarker marker : markers) {
			if (isIgnored(marker)) {
				continue;
			}
			if (IMavenConstants.MARKER_CONFIGURATION_ID.equals(marker.getType())) {
				pomMarkers.add(marker);
			} else if (GradleProjectImporter.GRADLE_UPGRADE_WRAPPER_MARKER_ID.equals(marker.getType())) {
				gradleMarkers.add(marker);
			} else {
				projectMarkers.add(marker);
			}
		}
		List<Diagnostic> diagnostics = toDiagnosticArray(range, projectMarkers, isDiagnosticTagSupported);
		String clientUri = ResourceUtils.toClientUri(uri);
		connection.publishDiagnostics(new PublishDiagnosticsParams(clientUri, diagnostics));
		if (pom.exists()) {
			IDocument document = JsonRpcHelpers.toDocument(pom);
			diagnostics = toDiagnosticsArray(document, pom.findMarkers(null, true, IResource.DEPTH_ZERO), isDiagnosticTagSupported);
			List<Diagnostic> diagnosicts2 = toDiagnosticArray(range, pomMarkers, isDiagnosticTagSupported);
			diagnostics.addAll(diagnosicts2);
			String pomSuffix = clientUri.endsWith("/") ? "pom.xml" : "/pom.xml";
			connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(clientUri + pomSuffix), diagnostics));
		}
		if (gradleWrapperProperties.exists()) {
			IDocument document = JsonRpcHelpers.toDocument(gradleWrapperProperties);
			diagnostics = toDiagnosticsArray(document, gradleWrapperProperties.findMarkers(null, true, IResource.DEPTH_ZERO), isDiagnosticTagSupported);
			List<Diagnostic> diagnosicts2 = toDiagnosticArray(range, gradleMarkers, isDiagnosticTagSupported);
			diagnostics.addAll(diagnosicts2);
			String gradleSuffix = clientUri.endsWith("/") ? GradleProjectImporter.GRADLE_WRAPPER_PROPERTIES_DESCRIPTOR : "/" + GradleProjectImporter.GRADLE_WRAPPER_PROPERTIES_DESCRIPTOR;
			connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(clientUri + gradleSuffix), diagnostics));
		}
	}

	public List<IMarker> publishDiagnostics(IProgressMonitor monitor) throws CoreException {
		List<IMarker> problemMarkers = getProblemMarkers(monitor);
		publishDiagnostics(problemMarkers);
		return problemMarkers;
	}

	private List<IMarker> getProblemMarkers(IProgressMonitor monitor) throws CoreException {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		List<IMarker> markers = new ArrayList<>();
		for (IProject project : projects) {
			if (monitor != null && monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			if (ProjectsManager.getDefaultProject().equals(project)) {
				continue;
			}
			IMarker[] allMarkers = project.findMarkers(null, true, IResource.DEPTH_INFINITE);
			for (IMarker marker : allMarkers) {
				if (isIgnored(marker)) {
					continue;
				}
				if (IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER.equals(marker.getType()) || IJavaModelMarker.TASK_MARKER.equals(marker.getType())) {
					markers.add(marker);
					continue;
				}
				IResource resource = marker.getResource();
				if (resource instanceof IFile && JavaCore.isJavaLikeFileName(resource.getName())) {
					markers.add(marker);
					continue;
				}
				if (project.equals(resource) || projectsManager.isBuildFile(resource)) {
					markers.add(marker);
				}
			}
		}
		return markers;
	}

	private static boolean isIgnored(IMarker marker) {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		boolean ignoreProjectEncoding = preferencesManager != null && ProjectEncodingMode.IGNORE.equals(preferencesManager.getPreferences().getProjectEncoding());
		try {
			if (!marker.exists() || CheckMissingNaturesListener.MARKER_TYPE.equals(marker.getType())) {
				return true;
			}
			if (ignoreProjectEncoding && ValidateProjectEncoding.MARKER_TYPE.equals(marker.getType())) {
				return true;
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return false;
	}

	private void publishDiagnostics(List<IMarker> markers) {
		Map<IResource, List<IMarker>> map = markers.stream().collect(Collectors.groupingBy(IMarker::getResource));
		for (Map.Entry<IResource, List<IMarker>> entry : map.entrySet()) {
			IResource resource = entry.getKey();
			if (resource instanceof IProject project) {
				try {
					publishMarkers(project, entry.getValue().toArray(new IMarker[0]));
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
				continue;
			}
			IFile file = resource.getAdapter(IFile.class);
			if (file == null) {
				continue;
			}
			IDocument document = null;
			String uri = JDTUtils.getFileURI(file);
			if (BaseDiagnosticsHandler.matchesDiagnosticFilter(uri, JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getDiagnosticFilter())) {
				continue;
			}
			if (JavaCore.isJavaLikeFileName(file.getName())) {
				ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
				//ignoring working copies, they're handled in the DocumentLifecycleHandler
				if (cu != null && !cu.isWorkingCopy()) {
					try {
						document = JsonRpcHelpers.toDocument(cu.getBuffer());
					} catch (JavaModelException e) {
						JavaLanguageServerPlugin.logException("Failed to publish diagnostics for " + uri, e);
					}
				}
			} else if (projectsManager.isBuildFile(file)) {
				document = JsonRpcHelpers.toDocument(file);
			}
			if (document != null) {
				List<Diagnostic> diagnostics = WorkspaceDiagnosticsHandler.toDiagnosticsArray(document, entry.getValue().toArray(new IMarker[0]), isDiagnosticTagSupported);
				connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), diagnostics));
			}
		}

		checkPreviewFeatureValidity(markers);
	}

	public static void checkPreviewFeatureValidity(List<IMarker> problemMarkers) {
		// Preview feature support enabled on incompatible release version
		List<IMarker> previewFeatureMarkers = problemMarkers.stream().filter(m -> m.getAttribute(IJavaModelMarker.ID, 0) == IProblem.PreviewFeaturesNotAllowed).collect(Collectors.toList());
		JsonArray errorList = new JsonArray();
		if (!previewFeatureMarkers.isEmpty()) {
			for (IMarker marker : previewFeatureMarkers) {
				// error message mentions invalid release level, and the supported level
				String errorMessage = ResourceUtils.getMessage(marker);
				String projectUri = JDTUtils.getFileURI(marker.getResource().getProject());
				JsonObject entry = new JsonObject();
				entry.addProperty("uri", projectUri);
				entry.addProperty("message", errorMessage);
				if (!errorList.contains(entry)) {
					errorList.add(entry);
				}
			}
			if (JavaLanguageServerPlugin.getProjectsManager().getConnection() != null) {
				EventNotification prevFeatNotAllowedNotification = new EventNotification().withType(EventType.PreviewFeaturesNotAllowed).withData(errorList);
				JavaLanguageServerPlugin.getProjectsManager().getConnection().sendEventNotification(prevFeatNotAllowedNotification);
			}
		}
	}

	@Deprecated
	public static List<Diagnostic> toDiagnosticArray(Range range, Collection<IMarker> markers) {
		return toDiagnosticArray(range, markers, false);
	}

	/**
	 * Transforms {@link IMarker}s into a list of {@link Diagnostic}s
	 *
	 * @param range
	 * @param markers
	 * @return a list of {@link Diagnostic}s
	 */
	public static List<Diagnostic> toDiagnosticArray(Range range, Collection<IMarker> markers, boolean isDiagnosticTagSupported) {
		return markers.stream().filter(WorkspaceDiagnosticsHandler::isInteresting).map(m -> toDiagnostic(range, m, isDiagnosticTagSupported)) //
				.filter(Objects::nonNull) //
				.collect(Collectors.toList());
	}

	private static Diagnostic toDiagnostic(Range range, IMarker marker, boolean isDiagnosticTagSupported) {
		if (marker == null || !marker.exists() || isIgnored(marker)) {
			return null;
		}
		Diagnostic d = new Diagnostic();
		d.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
		String message = marker.getAttribute(IMarker.MESSAGE, "");
		if (Messages.ProjectConfigurationUpdateRequired.equals(message)) {
			message = PROJECT_CONFIGURATION_IS_NOT_UP_TO_DATE_WITH_POM_XML;
		}
		d.setMessage(message);
		d.setSeverity(convertSeverity(marker.getAttribute(IMarker.SEVERITY, -1)));
		int problemId = marker.getAttribute(IJavaModelMarker.ID, 0);
		d.setCode(String.valueOf(problemId));
		if (isDiagnosticTagSupported) {
			d.setTags(DiagnosticsHandler.getDiagnosticTag(problemId));
		}
		d.setRange(range);
		try {
			if (marker.getAttribute(BaseDiagnosticsHandler.DIAG_JAVAC_CODE) != null) {
				String javacCode = (String) marker.getAttribute(BaseDiagnosticsHandler.DIAG_JAVAC_CODE);
				Map<String, Object> data = new HashMap<>();
				data.put(BaseDiagnosticsHandler.DIAG_ECJ_PROBLEM_ID, String.valueOf(problemId));
				d.setCode(javacCode);
				d.setData(data);
			}
		} catch (CoreException e) {
			// do nothing
		}
		return d;
	}

	@Deprecated
	public static List<Diagnostic> toDiagnosticsArray(IDocument document, IMarker[] markers) {
		return toDiagnosticsArray(document, markers, false);
	}

	/**
	 * Transforms {@link IMarker}s of a {@link IDocument} into a list of
	 * {@link Diagnostic}s; excluding marker types configured in
	 * {@link ClientPreferences}
	 *
	 * @param document
	 * @param markers
	 * @return a list of {@link Diagnostic}s
	 */
	public static List<Diagnostic> toDiagnosticsArray(IDocument document, IMarker[] markers, boolean isDiagnosticTagSupported) {
		List<Diagnostic> diagnostics = Stream.of(markers)
				.filter(WorkspaceDiagnosticsHandler::isInteresting).map(m -> toDiagnostic(document, m, isDiagnosticTagSupported)) //
				.filter(Objects::nonNull) //
				.collect(Collectors.toCollection(ArrayList::new));
		return diagnostics;
	}

	private static boolean isInteresting(IMarker marker) {
		return JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().excludedMarkerTypes().stream().noneMatch(markerType -> {
			try {
				return marker.isSubtypeOf(markerType);
			} catch (CoreException e) {
				return true; // resource not accessible makes the marker not interesting
			}
		});
	}

	private static Diagnostic toDiagnostic(IDocument document, IMarker marker, boolean isDiagnosticTagSupported) {
		if (marker == null || !marker.exists()) {
			return null;
		}
		Diagnostic d = new Diagnostic();
		d.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
		d.setMessage(marker.getAttribute(IMarker.MESSAGE, ""));
		int problemId = marker.getAttribute(IJavaModelMarker.ID, 0);
		d.setCode(String.valueOf(problemId));
		d.setSeverity(convertSeverity(marker.getAttribute(IMarker.SEVERITY, -1)));
		d.setRange(convertRange(document, marker));
		if (isDiagnosticTagSupported) {
			d.setTags(DiagnosticsHandler.getDiagnosticTag(problemId));
		}
		try {
			if (marker.getAttribute(BaseDiagnosticsHandler.DIAG_JAVAC_CODE) != null) {
				String javacCode = (String) marker.getAttribute(BaseDiagnosticsHandler.DIAG_JAVAC_CODE);
				Map<String, Object> data = new HashMap<>();
				data.put(BaseDiagnosticsHandler.DIAG_ECJ_PROBLEM_ID, String.valueOf(problemId));
				d.setCode(javacCode);
				d.setData(data);
			}
		} catch (CoreException e) {
			// do nothing
		}
		return d;
	}

	/**
	 * @param marker
	 * @return
	 */
	private static Range convertRange(IDocument document, IMarker marker) {
		int line = marker.getAttribute(IMarker.LINE_NUMBER, -1) - 1;
		if (line < 0) {
			int end = marker.getAttribute(IMarker.CHAR_END, -1);
			int start = marker.getAttribute(IMarker.CHAR_START, -1);
			if (start >= 0 && end >= start) {
				try {
					Range range = getAnnotationRange(document, marker);
					if (range != null) {
						return range;
					}
				} catch (BadLocationException | JavaModelException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
				int[] startPos = JsonRpcHelpers.toLine(document, start);
				int[] endPos = JsonRpcHelpers.toLine(document, end);
				return new Range(new Position(startPos[0], startPos[1]), new Position(endPos[0], endPos[1]));
			}
			return new Range(new Position(0, 0), new Position(0, 0));
		}
		int cStart = 0;
		int cEnd = 0;
		try {
			//Buildship doesn't provide markers for gradle files, Maven does
			if (marker.isSubtypeOf(IMavenConstants.MARKER_ID)) {
				cStart = marker.getAttribute(IMavenConstants.MARKER_COLUMN_START, -1);
				cEnd = marker.getAttribute(IMavenConstants.MARKER_COLUMN_END, -1);
			} else if (marker.isSubtypeOf(GradleProjectImporter.GRADLE_UPGRADE_WRAPPER_MARKER_ID)) {
				cStart = marker.getAttribute(GradleProjectImporter.GRADLE_MARKER_COLUMN_START, -1);
				cEnd = marker.getAttribute(GradleProjectImporter.GRADLE_MARKER_COLUMN_END, -1);
			} else {
				if (marker.getAttribute(IJavaModelMarker.ID, -1) == IProblem.UndefinedType) {
					try {
						Range range = getAnnotationRange(document, marker);
						if (range != null) {
							return range;
						}
					} catch (BadLocationException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				}

				int lineOffset = 0;
				try {
					lineOffset = document.getLineOffset(line);
				} catch (BadLocationException unlikelyException) {
					JavaLanguageServerPlugin.logException(unlikelyException.getMessage(), unlikelyException);
					return new Range(new Position(line, 0), new Position(line, 0));
				}
				cEnd = marker.getAttribute(IMarker.CHAR_END, -1) - lineOffset;
				cStart = marker.getAttribute(IMarker.CHAR_START, -1) - lineOffset;
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		cStart = Math.max(0, cStart);
		cEnd = Math.max(0, cEnd);
		return new Range(new Position(line, cStart), new Position(line, cEnd));
	}

	private static Range getAnnotationRange(IDocument document, IMarker marker) throws BadLocationException, JavaModelException {
		if (marker.getAttribute(IJavaModelMarker.ID, -1) == IProblem.UndefinedType) {
			int end = marker.getAttribute(IMarker.CHAR_END, -1);
			int start = marker.getAttribute(IMarker.CHAR_START, -1);
			if (start > 0) {
				start--;
				char ch = document.getChar(start);
				while (Character.isWhitespace(ch)) {
					start--;
					ch = document.getChar(start);
				}
				if (ch == '@') {
					return JDTUtils.toRange(document, start, end - start);
				}
			}
		}
		return null;
	}

	/**
	 * @param attribute
	 * @return
	 */
	private static DiagnosticSeverity convertSeverity(int severity) {
		if (severity == IMarker.SEVERITY_ERROR) {
			return DiagnosticSeverity.Error;
		}
		if (severity == IMarker.SEVERITY_WARNING) {
			return DiagnosticSeverity.Warning;
		}
		return DiagnosticSeverity.Information;
	}

	private void cleanUpDiagnostics(IResource resource) {
		cleanUpDiagnostics(resource, false);
	}

	private void cleanUpDiagnostics(IResource resource, boolean addTrailingSlash) {
		String uri = JDTUtils.getFileURI(resource);
		if (uri != null) {
			if (addTrailingSlash && !uri.endsWith("/")) {
				uri = uri + "/";
			}
			this.connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), Collections.emptyList()));
		}
	}

	private boolean isSupportedDiagnosticsResource(IResource resource) {
		if (resource.getType() == IResource.PROJECT) {
			return true;
		}

		IFile file = (IFile) resource;
		return JavaCore.isJavaLikeFileName(file.getName()) || projectsManager.isBuildFile(file);
	}
}
