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
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
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
		ClasspathQuery query = new ClasspathQuery();
		query.setProjectUri(getProjectUri(fJProject1));
		List<ClasspathNode> result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.CONTAINER, query), monitor);
		assertEquals(1, result.size());

		query.setPath(result.get(0).getPath());
		result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.JAR, query), monitor);

		assertEquals(1, result.size());

		query.setRootPath(result.get(0).getPath());
		result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.PACKAGE, query), monitor);

		assertEquals(38, result.size());

		query.setPath(result.get(0).getName());
		result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.CLASSFILE.getValue(), query), monitor);

		assertEquals(2, result.size());
		assertEquals("PropertyChangeListener.class", result.get(1).getName());

		query.setRootPath(null);
		query.setPath(result.get(1).getUri());
		String content = ClasspathCommand.getSource(Arrays.asList(query), monitor);
		assertEquals(content, "");
	}

	@Test
	public void testMavenProject() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		IJavaProject jProject = JavaCore.create(projects.get(1));
		ClasspathQuery query = new ClasspathQuery();
		query.setProjectUri(getProjectUri(jProject));

		List<ClasspathNode> result = command.getChildren(Arrays.asList(ClasspathNodeKind.CONTAINER.getValue(), query), monitor);
		assertEquals(2, result.size());
		assertEquals("Maven Dependencies", result.get(1).getName());

		query.setPath(result.get(1).getPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.JAR.getValue(), query), monitor);

		assertEquals(1, result.size());

		query.setRootPath(result.get(0).getPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.PACKAGE.getValue(), query), monitor);

		assertEquals(13, result.size());
		assertEquals(ClasspathNodeKind.Folder, result.get(12).getKind());

		query.setPath(result.get(0).getName());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.CLASSFILE.getValue(), query), monitor);

		assertEquals(28, result.size());
		assertEquals("AnnotationUtils.class", result.get(0).getName());

		query.setRootPath(null);
		query.setPath(result.get(0).getUri());
		String content = ClasspathCommand.getSource(Arrays.asList(query), monitor);
		assertTrue(content.contains("AnnotationUtils"));
	}

	@Test
	public void testGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/simple-gradle");
		IJavaProject jProject = JavaCore.create(projects.get(1));
		ClasspathQuery query = new ClasspathQuery();
		query.setProjectUri(getProjectUri(jProject));

		List<ClasspathNode> result = command.getChildren(Arrays.asList(ClasspathNodeKind.CONTAINER.getValue(), query), monitor);
		assertEquals(2, result.size());
		assertEquals("Project and External Dependencies", result.get(1).getName());

		query.setPath(result.get(1).getPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.JAR.getValue(), query), monitor);

		assertEquals(3, result.size());

		query.setRootPath(result.get(0).getPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.PACKAGE.getValue(), query), monitor);

		assertEquals(5, result.size());

		query.setPath(result.get(4).getPath());
		String content = ClasspathCommand.getSource(Arrays.asList(query), monitor);
		assertTrue(content.contains("BSD License"));

		query.setPath(result.get(0).getName());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.CLASSFILE.getValue(), query), monitor);

		assertEquals(16, result.size());
		assertEquals("BaseDescription.class", result.get(0).getName());

		query.setRootPath(null);
		query.setPath(result.get(0).getUri());
		content = ClasspathCommand.getSource(Arrays.asList(query), monitor);
		assertTrue(content.contains("BaseDescription"));
	}

	private String getProjectUri(IJavaProject project) {
		return fromFilepathToUri(project.getProject().getLocation().toOSString());
	}

	private String fromFilepathToUri(String filePath) {
		return (new File(filePath)).toURI().toString();
	}
}
