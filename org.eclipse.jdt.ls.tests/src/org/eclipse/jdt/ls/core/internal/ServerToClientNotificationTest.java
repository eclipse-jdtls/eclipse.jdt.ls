/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.Severity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ServerToClientNotificationTest extends AbstractProjectsManagerBasedTest {
	private JavaClientConnection javaClient;

	@Before
	public void setup() throws Exception {
		mockPreferences();

		javaClient = new JavaClientConnection(client);
	}

	@After
	public void tearDown() throws Exception {
		javaClient.disconnect();
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	private Preferences mockPreferences() {
		Preferences mockPreferences = Mockito.mock(Preferences.class);
		Mockito.when(preferenceManager.getPreferences()).thenReturn(mockPreferences);
		Mockito.when(preferenceManager.getPreferences(Mockito.any())).thenReturn(mockPreferences);
		Mockito.when(mockPreferences.getIncompleteClasspathSeverity()).thenReturn(Severity.ignore);
		return mockPreferences;
	}

	@Test
	public void testSendNotification() throws Exception {
		IElementChangedListener listener = new IElementChangedListener() {

			@Override
			public void elementChanged(ElementChangedEvent event) {
				// This should sent Java Model events to a client
				javaClient.notify("notify", event);
			}
		};
		try {
			JavaCore.addElementChangedListener(listener);
			IJavaProject javaProject = newDefaultProject();
			IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
			IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

			// @formatter:off
			String standaloneFileContent =
					"package java;\n"+
					"public class Foo extends UnknownType {"+
					"	public void method1(){\n"+
					"		super.whatever();"+
					"	}\n"+
					"}";
			// @formatter:on
			ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);

			// Check if all the events are received and events aren't nulls
			List<ElementChangedEvent> eventReports = getClientRequests("notify");
			assertEquals(7, eventReports.size());

			for (ElementChangedEvent notification : eventReports) {
				assertNotNull(notification);
			}
		} finally {
			JavaCore.removeElementChangedListener(listener);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}
}
