/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

/**
 * @author snjeza
 *
 */
public class RedundantInterfaceQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		Map<String, String> testProjectOptions = TestOptions.getDefaultOptions();
		testProjectOptions.put(JavaCore.COMPILER_PB_REDUNDANT_SUPERINTERFACE, JavaCore.WARNING);
		fJProject.setOptions(testProjectOptions);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testRedundantSuperinterface() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class RedundantInterface implements Int1, Int2 {}\n");
		buf.append("interface Int1 {}\n");
		buf.append("interface Int2 extends Int1 {}\n");
		ICompilationUnit cu = pack.createCompilationUnit("RedundantInterface.java", buf.toString(), true, null);
		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class RedundantInterface implements Int2 {}\n");
		buf.append("interface Int1 {}\n");
		buf.append("interface Int2 extends Int1 {}\n");
		Expected e1 = new Expected("Remove super interface", buf.toString());
		Range selection = new Range(new Position(1, 45), new Position(1, 45));
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testIgnoreRedundantSuperinterface() throws Exception {
		Map<String, String> testProjectOptions = fJProject.getOptions(false);
		testProjectOptions.put(JavaCore.COMPILER_PB_REDUNDANT_SUPERINTERFACE, JavaCore.IGNORE);
		fJProject.setOptions(testProjectOptions);
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class RedundantInterface implements Int1, Int2 {}\n");
		buf.append("interface Int1 {}\n");
		buf.append("interface Int2 extends Int1 {}\n");
		ICompilationUnit cu = pack.createCompilationUnit("RedundantInterface.java", buf.toString(), true, null);
		Range selection = new Range(new Position(1, 45), new Position(1, 45));
		setIgnoredCommands(ActionMessages.GenerateConstructorsAction_ellipsisLabel, ActionMessages.GenerateConstructorsAction_label);
		assertCodeActionNotExists(cu, selection, CorrectionMessages.LocalCorrectionsSubProcessor_remove_redundant_superinterface);
	}
}
