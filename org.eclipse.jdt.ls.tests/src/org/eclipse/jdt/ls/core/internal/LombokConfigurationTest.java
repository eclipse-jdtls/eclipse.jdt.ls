/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;

/**
 * Lombok resolves the {@code lombok.config} that applies to a source file by turning the
 * workspace relative file name reported by ECJ into an absolute file system location, which
 * requires {@code org.eclipse.core.resources} and {@code org.eclipse.core.runtime} to be
 * visible from the class loader of the bundle hosting ECJ
 * ({@code org.eclipse.jdt.core.compiler.batch}). When they are not, Lombok silently falls back
 * to a bogus location and no {@code lombok.config} is ever read.
 *
 * @see <a href="https://github.com/redhat-developer/vscode-java/issues/4461">vscode-java#4461</a>
 */
public class LombokConfigurationTest extends AbstractProjectsManagerBasedTest {

	// this test needs the Lombok agent (-javaagent:<lombok_jar>) to be meaningful
	@Test
	public void testLombokConfigIsApplied() throws Exception {
		if (Boolean.getBoolean("jdt.ls.lombok.disabled")) {
			return;
		}
		importProjects("maven/mavenlombokconfig");
		IProject project = WorkspaceHelper.getProject("mavenlombokconfig");
		IFile file = project.getFile("src/main/java/org/sample/Chained.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		IType type = cu.getType("Chained");
		IMethod setter = Stream.of(type.getMethods()).filter(m -> "setName".equals(m.getElementName())).findFirst().orElse(null);
		if (setter == null) {
			// the Lombok agent isn't installed in this JVM, nothing to check
			return;
		}
		assertEquals("Chained", Signature.getSignatureSimpleName(setter.getReturnType()),
				"'lombok.accessors.chain=true' from the project's lombok.config was not applied");
	}
}
