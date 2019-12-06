/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.BinaryType;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.SourceContentProvider;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractMavenBasedTest;
import org.junit.After;
import org.junit.Test;

/**
 * The purpose of this test is that the LS needs to extract images embedded in a
 * source/javadoc jar if they are being referenced in a javadoc <img src="...">
 * tag.
 *
 * Normally it would work given a path relative to the jar, but the LSP does not
 * know that, and we must provide an absolute path.
 *
 * Initial Issue: https://github.com/redhat-developer/vscode-java/issues/1007
 *
 *
 * The test projects can be found in org.eclipse.jdt.ls.tests/projects/maven/...
 *
 * @author nkomonen
 *
 */
public class JavaDocImageExtractionTest extends AbstractMavenBasedTest {

	private IProject project;

	private String testFolderName;

	@After
	public void cleanup() throws Exception {
		testFolderName = null;
		project = null;
	}

	@Test
	public void testImageExtractionWithSourceJar() throws Exception {
		testFolderName = "javadoc-image-extraction-with-sources";
		helpTestImageExtractionWithXJar(testFolderName);
	}

	@Test
	public void testImageExtractionWithJavadocJar() throws Exception {
		testFolderName = "javadoc-image-extraction-with-javadoc";
		helpTestImageExtractionWithXJar(testFolderName);
	}

	public void helpTestImageExtractionWithXJar(String testFolderName) throws Exception {
		setupMockMavenProject(testFolderName, "reactor.core.publisher.Mono");

		URI uri = project.getFile("src/main/java/foo/JavaDocJarTest.java").getLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);

		assertTrue(cu.isStructureKnown());

		IJavaElement javaElement = JDTUtils.findElementAtSelection(cu, 10, 12, null, new NullProgressMonitor());

		File testExportFile = JavaDocHTMLPathHandler.EXTRACTED_JAR_IMAGES_FOLDER.append("reactor-core-3.2.10.RELEASE/error.svg").toFile();
		String testExportPath = testExportFile.toURI().toString();

		//@formatter:off
		// We don't use this. Just to show what it should look like
		String expectedOutput =
				"Create a [Mono](jdt://contents/reactor-core-3.2.10.RELEASE.jar/reactor.core.publisher/Mono.class?=javadoctest/%5C/home%5C/nkomonen%5C/.m2%5C/repository%5C/io%5C/projectreactor%5C/reactor-core%5C/3.2.10.RELEASE%5C/reactor-core-3.2.10.RELEASE.jar%3Creactor.core.publisher%28Mono.class#101) that terminates with the specified error immediately after being subscribed to.\n" +
				"\n" +
				"![Image](" + testExportPath + ")\n" +
				"\n" +
				" *  **Type Parameters:**\n" +
				"    \n" +
				"     *  **<T>** the reified [Subscriber](jdt://contents/reactive-streams-1.0.2.jar/org.reactivestreams/Subscriber.class?=javadoctest/%5C/home%5C/nkomonen%5C/.m2%5C/repository%5C/org%5C/reactivestreams%5C/reactive-streams%5C/1.0.2%5C/reactive-streams-1.0.2.jar%3Corg.reactivestreams%28Subscriber.class#29) type\n" +
				" *  **Parameters:**\n" +
				"    \n" +
				"     *  **error** the onError signal\n" +
				" *  **Returns:**\n" +
				"    \n" +
				"     *  a failing [Mono](jdt://contents/reactor-core-3.2.10.RELEASE.jar/reactor.core.publisher/Mono.class?=javadoctest/%5C/home%5C/nkomonen%5C/.m2%5C/repository%5C/io%5C/projectreactor%5C/reactor-core%5C/3.2.10.RELEASE%5C/reactor-core-3.2.10.RELEASE.jar%3Creactor.core.publisher%28Mono.class#101)";
		//@formatter:on

		String expectedImageMarkdown = "![Image](" + testExportPath + ")";

		String finalString = HoverInfoProvider.computeJavadoc(javaElement).getValue();

		assertTrue("Does finalString=\n\t\"" + finalString + "\"\nContain expectedImageMarkdown=\n\t\"" + expectedImageMarkdown + "\"", finalString.contains(expectedImageMarkdown));

		assertTrue(testExportFile.exists());
	}

	@Test
	public void testImageExtractionWithoutAnyJars() throws Exception {
		testFolderName = "javadoc-image-extraction-without-any";
		setupMockMavenProject(testFolderName);

		URI uri = project.getFile("src/main/java/foo/JavaDocJarTest.java").getLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);

		assertTrue(cu.isStructureKnown());

		IJavaElement javaElement = JDTUtils.findElementAtSelection(cu, 12, 22, null, new NullProgressMonitor());
		String finalString = HoverInfoProvider.computeJavadoc(javaElement).getValue();

		String expectedImageMarkdown = "![Image]()";

		assertTrue(finalString.contains(expectedImageMarkdown));

	}

	@Test
	public void testImageRelativeToFile() throws Exception {

		testFolderName = "relative-image";
		setupMockMavenProject(testFolderName);


		URI uri = project.getFile("src/main/java/foo/bar/RelativeImage.java").getLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);

		assertTrue(cu.isStructureKnown());

		IJavaElement javaElement = JDTUtils.findElementAtSelection(cu, 7, 23, null, new NullProgressMonitor());

		Path pp = Paths.get(uri).getParent();
		URI parentURI = pp.toUri();

		String parentExportPath = parentURI.getPath();

		String relativeExportPath = "FolderWithPictures/red-hat-logo.png";

		String absoluteExportPath = parentExportPath + relativeExportPath;

		String expectedImageMarkdown = "![Image](file:" + absoluteExportPath + ")";

		String finalString = HoverInfoProvider.computeJavadoc(javaElement).getValue();

		assertTrue(finalString.contains(expectedImageMarkdown));

	}

	@Test
	public void testHyperlinkImage() throws Exception {
		testFolderName = "javadoc-image-extraction-with-hyperlink";
		setupMockMavenProject(testFolderName);

		URI uri = project.getFile("src/main/java/foo/JavaDocJarTest.java").getLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);

		assertTrue(cu.isStructureKnown());

		IJavaElement javaElement = JDTUtils.findElementAtSelection(cu, 14, 22, null, new NullProgressMonitor());
		String finalString = HoverInfoProvider.computeJavadoc(javaElement).getValue();

		String expectedImageMarkdown = "![Image](https://www.redhat.com/cms/managed-files/Logo-redhat-color-375.png)";

		assertTrue(finalString.contains(expectedImageMarkdown));
	}

	@Test
	public void testIsAbsolutePath() {

		assertTrue(JavaDocHTMLPathHandler.isPathAbsolute("/usr/nikolas/file.txt"));
		assertTrue(JavaDocHTMLPathHandler.isPathAbsolute("file:/usr/nikolas/file.txt"));
		assertTrue(JavaDocHTMLPathHandler.isPathAbsolute("file:///usr/nikolas/file.txt"));
		assertTrue(JavaDocHTMLPathHandler.isPathAbsolute("https://nikolas.com/file.txt"));

		assertFalse(JavaDocHTMLPathHandler.isPathAbsolute("usr/nikolas/file.txt"));
		assertFalse(JavaDocHTMLPathHandler.isPathAbsolute("usr/nikolas/folder/"));
	}

	//******* Utils *******

	public void setupMockMavenProject(String folderName) throws Exception {
		setupMockMavenProject(folderName, null);
	}

	public void setupMockMavenProject(String folderName, String... classpathNames) throws Exception {
		setupMockXProject("maven", folderName, classpathNames);

	}

	public void setupMockEclipseProject(String folderName) throws Exception {
		setupMockXProject("eclipse", folderName);
	}

	public void setupMockXProject(String projectTypeFolderName, String projectFolderName, String... classpathNames) throws Exception {
		importProjects(projectTypeFolderName + "/" + projectFolderName);
		project = WorkspaceHelper.getProject(projectFolderName);

		if (classpathNames != null) {
			for (String classpathName : classpathNames) {
				if (classpathName != null) {
					ensureSourceOfClassIsDownloaded(classpathName);
				}
			}
		}
	}

	public void ensureSourceOfClassIsDownloaded(String classpathName) throws Exception {
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType(classpathName); //eg: reactor.core.publisher.Mono
		IClassFile classFile = ((BinaryType) type).getClassFile();
		String source = new SourceContentProvider().getSource(classFile, new NullProgressMonitor());
		if (source == null) {
			JobHelpers.waitForDownloadSourcesJobs(JobHelpers.MAX_TIME_MILLIS);
			source = new SourceContentProvider().getSource(classFile, new NullProgressMonitor());
		}
		assertNotNull(source);
	}
}
