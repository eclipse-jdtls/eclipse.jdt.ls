/*******************************************************************************
* Copyright (c) 2023 Microsoft Corporation and others.
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.ISourceDownloader;

public class SourceAttachUpdateHandler implements IElementChangedListener {
	private final JavaClientConnection connection;

	public SourceAttachUpdateHandler(JavaClientConnection client) {
		this.connection = client;
	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
		Set<IJavaElement> sourceAttachChangedElements = new HashSet<>();
		processDelta(event.getDelta(), sourceAttachChangedElements);
		if (connection != null && !sourceAttachChangedElements.isEmpty()) {
			SourceInvalidatedEvent invalidatedJarSource = new SourceInvalidatedEvent();
			for (IJavaElement changedElement : sourceAttachChangedElements) {
				if (changedElement instanceof JarPackageFragmentRoot jar) {
					try {
						if (jar.getSourceAttachmentPath() != null) {
							int downloadStatus = JavaLanguageServerPlugin.getDefaultSourceDownloader().getDownloadStatus(jar);
							if (downloadStatus == ISourceDownloader.DOWNLOAD_REQUESTED) {
								// The download job completes quickly and the source provider
								// uses the source jar right away. No action is needed.
								JavaLanguageServerPlugin.getDefaultSourceDownloader().clearDownloadStatus(jar);
								continue;
							} else if (downloadStatus == ISourceDownloader.DOWNLOAD_WAIT_JOB_DONE) {
								// The source attachment event arrives after the source
								// request is responded. This means the decompiled source
								// is in use first, and need to reload the source provider
								// to use the new source jar.
								invalidatedJarSource.addRootPath(jar, true);
								JavaLanguageServerPlugin.getDefaultSourceDownloader().clearDownloadStatus(jar);
							} else if (downloadStatus == ISourceDownloader.DOWNLOAD_NONE) {
								// The source attachment event is not triggered by download
								// job.
								invalidatedJarSource.addRootPath(jar, false);
							}
						}
					} catch (JavaModelException e) {
						// skip
					}
				}
			}

			if (!invalidatedJarSource.isEmpty()) {
				EventNotification notification = new EventNotification().withType(EventType.SourceInvalidated).withData(invalidatedJarSource);
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

	private void processDelta(IJavaElementDelta delta, Set<IJavaElement> result) {
		IJavaElement element = delta.getElement();
		if (element.getElementType() != IJavaElement.JAVA_MODEL && !isClasspathChangedEvent(delta)) {
			return;
		}

		IJavaElementDelta[] children = delta.getAffectedChildren();
		if (children == null || children.length == 0) {
			if ((delta.getFlags() & IJavaElementDelta.F_SOURCEATTACHED) != 0) {
				result.add(delta.getElement());
			}
			return;
		}

		for (IJavaElementDelta child : children) {
			processDelta(child, result);
		}
	}

	private boolean isClasspathChangedEvent(IJavaElementDelta delta) {
		return (delta.getFlags() & (IJavaElementDelta.F_CLASSPATH_CHANGED | IJavaElementDelta.F_SOURCEATTACHED
			| IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED)) != 0;
	}

	/**
	 * The package fragment roots are updated with new source attachments,
	 * and it should notify the client to reload the source that was requested
	 * previously.
	 */
	public static class SourceInvalidatedEvent {
		private Map<String, Boolean> affectedRootPaths = new LinkedHashMap<>();

		public void addRootPath(IPackageFragmentRoot root, boolean isAutoDownloadedSource) {
			affectedRootPaths.put(root.getPath().toPortableString(), isAutoDownloadedSource);
		}

		public boolean isEmpty() {
			return affectedRootPaths.isEmpty();
		}

		public boolean contains(IPackageFragmentRoot root, boolean isAutoDownloadedSource) {
			return affectedRootPaths.getOrDefault(root.getPath().toPortableString(), !isAutoDownloadedSource) == isAutoDownloadedSource;
		}
	}
}
