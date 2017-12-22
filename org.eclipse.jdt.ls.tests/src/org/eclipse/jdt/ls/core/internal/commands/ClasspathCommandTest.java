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
		List<ClasspathNode> result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.CONTAINER, query));
		assertEquals(1, result.size());

		query.setNodePtah(result.get(0).getItemPath());
		result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.JAR, query));

		assertEquals(1, result.size());

		query.setNodePtah(result.get(0).getItemPath());
		result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.PACKAGE, query));

		assertEquals(37, result.size());

		query.setNodeId(result.get(0).getName());
		result = ClasspathCommand.getChildren(Arrays.asList(ClasspathNodeKind.CLASSFILE.getValue(), query));

		assertEquals(43, result.size());
		assertEquals("Consumer.class", result.get(1).getName());

		List<Object> sourceQuery = new ArrayList<>();
		sourceQuery.add(result.get(1).getUri());
		String content = ClasspathCommand.getSource(sourceQuery);
		assertEquals(content, "");
	}

	@Test
	public void testMavenProject() throws Exception {
		List<IProject> projects = importProjects("maven/salut");
		IJavaProject jProject = JavaCore.create(projects.get(1));
		ClasspathQuery query = new ClasspathQuery();
		query.setProjectUri(getProjectUri(jProject));

		List<ClasspathNode> result = command.getChildren(Arrays.asList(ClasspathNodeKind.CONTAINER.getValue(), query));
		assertEquals(2, result.size());
		assertEquals("Maven Dependencies", result.get(1).getName());

		query.setNodePtah(result.get(1).getItemPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.JAR.getValue(), query));

		assertEquals(1, result.size());

		query.setNodePtah(result.get(0).getItemPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.PACKAGE.getValue(), query));

		assertEquals(12, result.size());

		query.setNodeId(result.get(0).getName());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.CLASSFILE.getValue(), query));

		assertEquals(58, result.size());
		assertEquals("DateUtils.class", result.get(7).getName());


		List<Object> sourceQuery = new ArrayList<>();
		sourceQuery.add(result.get(7).getUri());
		String content = ClasspathCommand.getSource(sourceQuery);
		assertTrue(content.startsWith("/*"));
	}

	@Test
	public void testGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/simple-gradle");
		IJavaProject jProject = JavaCore.create(projects.get(1));
		ClasspathQuery query = new ClasspathQuery();
		query.setProjectUri(getProjectUri(jProject));

		List<ClasspathNode> result = command.getChildren(Arrays.asList(ClasspathNodeKind.CONTAINER.getValue(), query));
		assertEquals(2, result.size());
		assertEquals("Project and External Dependencies", result.get(1).getName());

		query.setNodePtah(result.get(1).getItemPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.JAR.getValue(), query));

		assertEquals(3, result.size());

		query.setNodePtah(result.get(0).getItemPath());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.PACKAGE.getValue(), query));

		assertEquals(4, result.size());

		query.setNodeId(result.get(0).getName());
		result = command.getChildren(Arrays.asList(ClasspathNodeKind.CLASSFILE.getValue(), query));

		assertEquals(9, result.size());
		assertEquals("ILoggerFactory.class", result.get(0).getName());

		List<Object> sourceQuery = new ArrayList<>();
		sourceQuery.add(result.get(0).getUri());
		String content = ClasspathCommand.getSource(sourceQuery);
		assertTrue(content.startsWith("/*"));
	}

	private String getProjectUri(IJavaProject project) {
		return fromFilepathToUri(project.getProject().getLocation().toOSString());
	}

	private String fromFilepathToUri(String filePath) {
		return (new File(filePath)).toURI().toString();
	}
}
