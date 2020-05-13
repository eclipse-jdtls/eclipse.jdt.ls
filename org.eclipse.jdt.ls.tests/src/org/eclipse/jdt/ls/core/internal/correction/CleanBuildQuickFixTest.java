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

package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;

public class CleanBuildQuickFixTest extends AbstractQuickFixTest {

	@Test
	public void testCleanBuildForUnresolvedImport() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");

		Path compilationUnitPath = Paths.get(project.getRawLocation().toOSString(), "src", "main", "java", "java", "Foo3.java");
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(compilationUnitPath.toUri().toString());
		
		Path classesPath = Paths.get(project.getRawLocation().toOSString(), "target", "classes");
		File classDir = classesPath.toFile();
		FileUtils.deleteDirectory(classDir);

		IFile sourceFile = project.getFile("src/main/java/java/Foo3.java");
		String content = ResourceUtils.getContent(sourceFile);
		content = content.replace("// TestImport testImport = new TestImport();", "TestImport testImport = new TestImport();");
		ResourceUtils.setContent(sourceFile, content);
		cu.getUnderlyingResource().refreshLocal(IResource.DEPTH_ZERO, monitor);
		waitForBackgroundJobs();

		List<Either<Command, CodeAction>> codeActions = evaluateCodeActionsForIMarker(cu);
		Expected e1 = new Expected("Execute 'clean build'", "");
		for (Either<Command, CodeAction> c : codeActions) {
			if (Objects.equals("Execute 'clean build'", getTitle(c))) {
				Command commandInCodeAction = c.getRight().getCommand();
				assertEquals("java.workspace.compile", commandInCodeAction.getCommand());

				List<Object> argumentList = commandInCodeAction.getArguments();
				assertEquals(1, argumentList.size());
				assertEquals(true, argumentList.get(0));
				return;
			}
		}
		fail(e1.name + " not found");
	}
}
