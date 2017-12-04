/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class ClasspathCommandTest extends AbstractProjectsManagerBasedTest {

	private IJavaProject fJProject1;

	private ClasspathCommand command = new ClasspathCommand();

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
	}

	@Test
	public void testEclipseProject() throws CoreException {
		ArrayList<String> query = new ArrayList<>();
		query.add(getProjectUri(fJProject1));
		Either<ClasspathItem[], String> result = command.getClasspathItems(Arrays.asList(ClasspathItem.CONTAINER, query));
		assertEquals(1, result.getLeft().length);

		query.add(result.getLeft()[0].getPath());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.JAR, query));

		assertEquals(1, result.getLeft().length);

		query.set(1, fromFilepathToUri(result.getLeft()[0].getPath()));
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.PACKAGE, query));

		assertEquals(37, result.getLeft().length);

		query.add(result.getLeft()[0].getName());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.CLASSFILE, query));

		assertEquals(43, result.getLeft().length);
		assertEquals("Consumer.class", result.getLeft()[1].getName());

		query.add(result.getLeft()[1].getName());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.SOURCE, query));
		assertEquals(result.getRight(), "");
	}

	@Test
	public void testMavenProject() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		IJavaProject jProject = JavaCore.create(projects.get(1));
		ArrayList<String> query = new ArrayList<>();
		query.add(getProjectUri(jProject));
		Either<ClasspathItem[], String> result = command.getClasspathItems(Arrays.asList(ClasspathItem.CONTAINER, query));
		assertEquals(2, result.getLeft().length);
		assertEquals("Maven Dependencies", result.getLeft()[1].getName());

		query.add(result.getLeft()[1].getPath());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.JAR, query));

		assertEquals(1, result.getLeft().length);

		query.set(1, fromFilepathToUri(result.getLeft()[0].getPath()));
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.PACKAGE, query));

		assertEquals(12, result.getLeft().length);

		query.add(result.getLeft()[0].getName());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.CLASSFILE, query));

		assertEquals(58, result.getLeft().length);
		assertEquals("DateUtils.class", result.getLeft()[7].getName());

		query.add(result.getLeft()[7].getName());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.SOURCE, query));
		assertTrue(result.getRight().startsWith("/*"));
	}

	@Test
	public void testGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/simple-gradle");
		IJavaProject jProject = JavaCore.create(projects.get(1));
		ArrayList<String> query = new ArrayList<>();
		query.add(getProjectUri(jProject));
		Either<ClasspathItem[], String> result = command.getClasspathItems(Arrays.asList(ClasspathItem.CONTAINER, query));
		assertEquals(2, result.getLeft().length);
		assertEquals("Project and External Dependencies", result.getLeft()[1].getName());

		query.add(result.getLeft()[1].getPath());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.JAR, query));

		assertEquals(3, result.getLeft().length);

		query.set(1, fromFilepathToUri(result.getLeft()[0].getPath()));
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.PACKAGE, query));

		assertEquals(4, result.getLeft().length);

		query.add(result.getLeft()[0].getName());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.CLASSFILE, query));

		assertEquals(9, result.getLeft().length);
		assertEquals("ILoggerFactory.class", result.getLeft()[0].getName());

		query.add(result.getLeft()[0].getName());
		result = command.getClasspathItems(Arrays.asList(ClasspathItem.SOURCE, query));
		assertTrue(result.getRight().startsWith("/*"));
	}

	private String getProjectUri(IJavaProject project) {
		return fromFilepathToUri(project.getProject().getLocation().toOSString());
	}

	private String fromFilepathToUri(String filePath) {
		return (new File(filePath)).toURI().toString();
	}
}
