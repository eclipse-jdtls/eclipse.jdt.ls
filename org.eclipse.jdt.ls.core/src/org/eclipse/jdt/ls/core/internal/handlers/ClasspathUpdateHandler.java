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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.extended.ProjectBuildParams;

public class ClasspathUpdateHandler implements IElementChangedListener {

	private final JavaClientConnection connection;

	public ClasspathUpdateHandler(JavaClientConnection client) {
		this.connection = client;
	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
		// Collect project names which have classpath changed.
		Set<String> uris = processDelta(event.getDelta(), null);
		if (connection != null && uris != null && !uris.isEmpty()) {
			for (String uri : uris) {
				PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
				if (preferenceManager.getPreferences().getNullAnalysisMode().equals(FeatureStatus.automatic)) {
					IProject project = ProjectUtils.getProjectFromUri(uri);
					if (project != null) {
						IJavaProject javaProject = ProjectUtils.getJavaProject(project);
						if (javaProject != null) {
							WorkspaceJob job = new WorkspaceJob("Classpath Update Job") {
								@Override
								public IStatus runInWorkspace(IProgressMonitor monitor) {
									if (!preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions(javaProject, true) || !preferenceManager.getPreferences().isAutobuildEnabled()) {
										// When the project's compiler options didn't change or auto build is disabled, rebuilding is not required.
										return Status.OK_STATUS;
									}
									BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
									ProjectBuildParams params = new ProjectBuildParams(Arrays.asList(new TextDocumentIdentifier(uri)), true);
									BuildWorkspaceStatus status = buildWorkspaceHandler.buildProjects(params, monitor);
									switch (status) {
										case FAILED:
										case WITH_ERROR:
											return Status.error("error occurs during building project");
										case SUCCEED:
											return Status.OK_STATUS;
										case CANCELLED:
											return Status.CANCEL_STATUS;
										default:
											return Status.OK_STATUS;
									}
								}
							};
							job.setPriority(Job.SHORT);
							job.setRule(project);
							job.schedule();
						}
					}
				}
				EventNotification notification = new EventNotification().withType(EventType.ClasspathUpdated).withData(uri);
				this.connection.sendEventNotification(notification);
			}
		}
	}

	public void addElementChangeListener() {
		JavaCore.addElementChangedListener(this);
	}

	public void removeElementChangeListener() {
		JavaCore.removeElementChangedListener(this);
	}

	private Set<String> processDeltaChildren(IJavaElementDelta delta, Set<String> uris) {
		for (IJavaElementDelta c : delta.getAffectedChildren()) {
			uris = processDelta(c, uris);
		}
		return uris;
	}

	private Set<String> processDelta(IJavaElementDelta delta, Set<String> uris) {
		IJavaElement element = delta.getElement();
		switch (element.getElementType()) {
		case IJavaElement.JAVA_MODEL:
			uris = processDeltaChildren(delta, uris);
			break;
		case IJavaElement.JAVA_PROJECT:
			if (isClasspathChanged(delta.getFlags())) {
				if (uris == null) {
					uris = new HashSet<String>();
				}
				IJavaProject javaProject = (IJavaProject) element;
				uris.add(ProjectUtils.getProjectRealFolder(javaProject.getProject()).toFile().toURI().toString());
			}
			break;
		default:
			break;
		}
		return uris;
	}

	private boolean isClasspathChanged(int flags) {
		return 0 != (flags & (IJavaElementDelta.F_CLASSPATH_CHANGED | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
				| IJavaElementDelta.F_CLOSED | IJavaElementDelta.F_OPENED));
	}
}