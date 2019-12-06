/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand.SourceAttachmentAttribute;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand.SourceAttachmentRequest;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand.SourceAttachmentResult;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;

public class SourceAttachmentCommandTest extends AbstractProjectsManagerBasedTest {
	private static final String classFileUri = "jdt://contents/foo.jar/foo/bar.class?%3Dsource-attachment%2Ffoo.jar%3Cfoo%28bar.class";
	private IProject project;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/source-attachment");
		project = WorkspaceHelper.getProject("source-attachment");
	}

	@Test
	public void testResolveSourceAttachment_ParameterIsMissing() {
		SourceAttachmentResult resolveResult = SourceAttachmentCommand.resolveSourceAttachment((List<Object>) null, new NullProgressMonitor());
		assertNotNull(resolveResult);
		assertNotNull(resolveResult.errorMessage);
	}

	@Test
	public void testResolveSourceAttachment_InvalidParameter() {
		SourceAttachmentResult resolveResult = SourceAttachmentCommand.resolveSourceAttachment(Arrays.asList(classFileUri), new NullProgressMonitor());
		assertNotNull(resolveResult);
		assertNotNull(resolveResult.errorMessage);
	}

	@Test
	public void testResolveSourceAttachmentCall() throws Exception {
		SourceAttachmentRequest request = new SourceAttachmentRequest(classFileUri, null);
		String arguments = new Gson().toJson(request);
		SourceAttachmentResult resolveResult = SourceAttachmentCommand.resolveSourceAttachment(Arrays.asList(arguments), new NullProgressMonitor());
		assertNotNull(resolveResult);
		assertNull(resolveResult.errorMessage);
		assertNotNull(resolveResult.attributes);
		assertNotNull(resolveResult.attributes.jarPath);
		assertTrue(resolveResult.attributes.jarPath.endsWith("foo.jar"));
		assertNull(resolveResult.attributes.sourceAttachmentPath);
	}

	@Test
	public void testUpdateSourceAttachment_ParameterIsMissing() {
		SourceAttachmentResult updateResult = SourceAttachmentCommand.updateSourceAttachment((List<Object>) null, new NullProgressMonitor());
		assertNotNull(updateResult);
		assertNotNull(updateResult.errorMessage);
	}

	@Test
	public void testUpdateSourceAttachment_InvalidParameter() {
		SourceAttachmentResult updateResult = SourceAttachmentCommand.updateSourceAttachment(Arrays.asList(classFileUri), new NullProgressMonitor());
		assertNotNull(updateResult);
		assertNotNull(updateResult.errorMessage);
	}

	@Test
	public void testUpdateSourceAttachment_EmptySourceAttachmentPath() throws Exception {
		SourceAttachmentAttribute attributes = new SourceAttachmentAttribute(null, null, "UTF-8");
		SourceAttachmentRequest request = new SourceAttachmentRequest(classFileUri, attributes);
		String arguments = new Gson().toJson(request, SourceAttachmentRequest.class);
		SourceAttachmentResult updateResult = SourceAttachmentCommand.updateSourceAttachment(Arrays.asList(arguments), new NullProgressMonitor());
		assertNotNull(updateResult);
		assertNull(updateResult.errorMessage);

		// Verify no source is attached to the classfile.
		IClassFile classfile = JDTUtils.resolveClassFile(classFileUri);
		IBuffer buffer = classfile.getBuffer();
		assertNull(buffer);
	}

	@Test
	public void testUpdateSourceAttachmentFromProjectJar() throws Exception {
		IResource source = project.findMember("foo-sources.jar");
		assertNotNull(source);
		IPath sourcePath = source.getLocation();
		SourceAttachmentAttribute attributes = new SourceAttachmentAttribute(null, sourcePath.toOSString(), "UTF-8");
		SourceAttachmentRequest request = new SourceAttachmentRequest(classFileUri, attributes);
		String arguments = new Gson().toJson(request, SourceAttachmentRequest.class);
		SourceAttachmentResult updateResult = SourceAttachmentCommand.updateSourceAttachment(Arrays.asList(arguments), new NullProgressMonitor());
		assertNotNull(updateResult);
		assertNull(updateResult.errorMessage);

		// Verify the source is attached to the classfile.
		IClassFile classfile = JDTUtils.resolveClassFile(classFileUri);
		IBuffer buffer = classfile.getBuffer();
		assertNotNull(buffer);
		assertTrue(buffer.getContents().indexOf("return sum;") >= 0);

		// Verify whether project inside jar attachment is saved with project relative path.
		IJavaProject javaProject = JavaCore.create(project);
		IPath relativePath = source.getFullPath();
		IPath absolutePath = source.getLocation();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (Objects.equals("foo.jar", entry.getPath().lastSegment())) {
				assertNotNull(entry.getSourceAttachmentPath());
				assertEquals(relativePath, entry.getSourceAttachmentPath());
				assertNotEquals(absolutePath, entry.getSourceAttachmentPath());
				break;
			}
		}
	}

	@Test
	public void testUpdateSourceAttachmentFromExternalJar() throws Exception {
		File file = copyFiles("eclipse/external/foo-sources.jar", false);
		IPath sourcePath = Path.fromOSString(file.getAbsolutePath());
		SourceAttachmentAttribute attributes = new SourceAttachmentAttribute(null, sourcePath.toOSString(), "UTF-8");
		SourceAttachmentRequest request = new SourceAttachmentRequest(classFileUri, attributes);
		String arguments = new Gson().toJson(request, SourceAttachmentRequest.class);
		SourceAttachmentResult updateResult = SourceAttachmentCommand.updateSourceAttachment(Arrays.asList(arguments), new NullProgressMonitor());
		assertNotNull(updateResult);
		assertNull(updateResult.errorMessage);

		// Verify the source is attached to the classfile.
		IClassFile classfile = JDTUtils.resolveClassFile(classFileUri);
		IBuffer buffer = classfile.getBuffer();
		assertNotNull(buffer);
		assertTrue(buffer.getContents().indexOf("return sum;") >= 0);

		// Verify whether external jar attachment is saved with absolute path.
		IJavaProject javaProject = JavaCore.create(project);
		IPath relativePath = project.findMember("foo-sources.jar").getFullPath();
		IPath absolutePath = sourcePath;
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (Objects.equals("foo.jar", entry.getPath().lastSegment())) {
				assertNotNull(entry.getSourceAttachmentPath());
				assertEquals(absolutePath, entry.getSourceAttachmentPath());
				assertNotEquals(relativePath, entry.getSourceAttachmentPath());
				break;
			}
		}
	}
}
