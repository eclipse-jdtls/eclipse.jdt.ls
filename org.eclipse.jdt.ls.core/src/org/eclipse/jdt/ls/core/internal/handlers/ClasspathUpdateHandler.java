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
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.lsp4j.MessageType;

public class ClasspathUpdateHandler implements IElementChangedListener {

	private static final String CLASSPATH_UPDATED_NOTIFICATION = "__CLASSPATH_UPDATED__";

	private final JavaClientConnection connetction;
	
	public ClasspathUpdateHandler(JavaClientConnection client) {
		this.connetction = client;
	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
		// Collect project names which have classpath changed.
		Set<String> uris = processDelta(event.getDelta(), null);
		if (connetction != null && uris != null && !uris.isEmpty()) {
			for (String uri : uris) {
				ActionableNotification notification = new ActionableNotification().withSeverity(MessageType.Log).withMessage(CLASSPATH_UPDATED_NOTIFICATION).withData(uri);
				this.connetction.sendActionableNotification(notification);
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
				uris.add(javaProject.getProject().getLocation().toOSString());
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