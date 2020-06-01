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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

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