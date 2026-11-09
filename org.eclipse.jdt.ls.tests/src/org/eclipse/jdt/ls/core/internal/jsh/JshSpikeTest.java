/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.jsh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.junit.jupiter.api.Test;

/**
 * Spike for https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3973 (Phase 0):
 * proves that a synthetic, non-persisted working copy - bound to a real project's
 * classpath, with content supplied entirely in-memory rather than read from disk -
 * can still produce correct, classpath-aware completion proposals. This is the
 * foundational assumption the rest of the .jsh content-assist plan depends on.
 */
public class JshSpikeTest extends AbstractInvisibleProjectBasedTest {

	@Test
	public void testCompletionOnSyntheticWorkingCopyUsesRealProjectClasspath() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		IJavaProject javaProject = JavaCore.create(project);

		// Deliberately not written to disk anywhere: this stands in for the
		// wrapped .jsh content that would eventually be produced by JshSnippetWrapper.
		String wrapped = "public class __JdtLsJshScratchpad__ {\n"
				+ "    void __run__() throws Exception {\n"
				+ "        org.apache.commons.lang3.StringUtils.isBla\n"
				+ "    }\n"
				+ "}\n";

		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pkg = sourceFolder.getPackageFragment("org.sample");

		WorkingCopyOwner owner = new WorkingCopyOwner() {
		};
		ICompilationUnit cu = pkg.getCompilationUnit("__JdtLsJshScratchpad__.java").getWorkingCopy(owner, new NullProgressMonitor());
		try {
			cu.getBuffer().setContents(wrapped);
			cu.makeConsistent(new NullProgressMonitor());

			int offset = wrapped.indexOf("isBla") + "isBla".length();
			List<String> proposals = new ArrayList<>();
			cu.codeComplete(offset, new CompletionRequestor() {
				@Override
				public void accept(CompletionProposal proposal) {
					proposals.add(new String(proposal.getCompletion()));
				}
			});

			assertTrue(proposals.stream().anyMatch(p -> p.contains("isBlank")),
					"Expected completion on a synthetic, non-persisted working copy to resolve "
					+ "org.apache.commons.lang3.StringUtils (a real dependency of the 'salut' "
					+ "project) and propose isBlank(...), but got: " + proposals);
		} finally {
			cu.discardWorkingCopy();
		}
	}

}
