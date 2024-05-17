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

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
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
	private final BaseDocumentLifeCycleHandler lifeCycleHandler;

	public ClasspathUpdateHandler(JavaClientConnection client) {
		this(client, null);
	}

	public ClasspathUpdateHandler(JavaClientConnection client, BaseDocumentLifeCycleHandler lifeCycleHandler) {
		this.connection = client;
		this.lifeCycleHandler = lifeCycleHandler;
	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
		// Collect project names which have classpath changed.
		Set<IJavaProject> projects = new HashSet<>();
		processDelta(event.getDelta(), projects);
		if (connection != null && projects != null && !projects.isEmpty()) {
			for (IJavaProject javaProject : projects) {
				String uri = ProjectUtils.getProjectRealFolder(javaProject.getProject()).toFile().toURI().toString();
				PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
				if (!preferenceManager.getPreferences().isAutobuildEnabled()) {
					if (lifeCycleHandler != null) {
						for (ICompilationUnit unit : JavaCore.getWorkingCopies(null)) {
							if (unit.getJavaProject().equals(javaProject)) {
								try {
									lifeCycleHandler.triggerValidation(unit);
								} catch (JavaModelException e) {
									JavaLanguageServerPlugin.logException("Failed to revalidate document after classpath change: " + unit.getPath(), e);
								}
							}
						}
					}
				} else if (preferenceManager.getPreferences().getNullAnalysisMode().equals(FeatureStatus.automatic)) {
					WorkspaceJob job = new WorkspaceJob("Classpath Update Job") {
						@Override
						public IStatus runInWorkspace(IProgressMonitor monitor) {
							if (!preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions(javaProject, true)) {
								// When the project's compiler options didn't change, rebuilding is not required.
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
					job.setRule(javaProject.getProject());
					job.schedule();
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

	private void processDeltaChildren(IJavaElementDelta delta, Set<IJavaProject> projects) {
		for (IJavaElementDelta c : delta.getAffectedChildren()) {
			processDelta(c, projects);
		}
	}

	private void processDelta(IJavaElementDelta delta, Set<IJavaProject> projects) {
		IJavaElement element = delta.getElement();
		switch (element.getElementType()) {
		case IJavaElement.JAVA_MODEL:
			processDeltaChildren(delta, projects);
			break;
		case IJavaElement.JAVA_PROJECT:
			if (isClasspathChanged(delta.getFlags())) {
				IJavaProject javaProject = (IJavaProject) element;
				projects.add(javaProject);
			}
			break;
		default:
			break;
		}
	}

	private boolean isClasspathChanged(int flags) {
		return 0 != (flags & (IJavaElementDelta.F_CLASSPATH_CHANGED | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
				| IJavaElementDelta.F_CLOSED | IJavaElementDelta.F_OPENED));
	}
}