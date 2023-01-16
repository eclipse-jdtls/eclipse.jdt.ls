/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;

public class SharedIndexTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void testSharedIndex() throws Exception {
		final String sharedIndexKey = "jdt.core.sharedIndexLocation";
		Field SHARED_INDEX_LOCATION = ClasspathEntry.class.getDeclaredField("SHARED_INDEX_LOCATION");
		SHARED_INDEX_LOCATION.setAccessible(true);
		try {
			Path newIndexPath = Paths.get(getWorkingProjectDirectory().toString(), ".index");
			System.setProperty(sharedIndexKey, newIndexPath.toString());
			SHARED_INDEX_LOCATION.set(ClasspathEntry.class, newIndexPath.toString());

			IJavaProject javaProject = newEmptyProject();
			IndexUtils.copyIndexesToSharedLocation();
			IClasspathEntry[] entries = ((JavaProject) javaProject).getResolvedClasspath();
				for (IClasspathEntry entry : entries) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
						URL expectedIndexUrl = ((ClasspathEntry) entry).getLibraryIndexLocation();
						File sharedIndexFile = null;
						try {
							URI localFileURI = new URI(expectedIndexUrl.toExternalForm());
							sharedIndexFile = new File(localFileURI);
						} catch(Exception ex) {
							sharedIndexFile = new File(expectedIndexUrl.getPath());
						}

						assertTrue("shared index file should exist", sharedIndexFile.exists());
						assertTrue("library index should be generated in shared location", sharedIndexFile.toPath().startsWith(newIndexPath.toAbsolutePath()));
					}
				}
		} finally {
			System.clearProperty(sharedIndexKey);
			SHARED_INDEX_LOCATION.set(ClasspathEntry.class, null);
		}
	}
}
