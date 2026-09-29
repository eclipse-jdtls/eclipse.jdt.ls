/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author jjohnstn
 *
 */
public class TypeArgumentMismatchCorrectionTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testNonGenericType() throws Exception {
		Map<String, String> options9 = new HashMap<>();
		JavaModelUtil.setComplianceOptions(options9, JavaCore.VERSION_9);
		options9.put(JavaCore.COMPILER_PB_MISSING_OVERRIDE_ANNOTATION, JavaCore.ERROR);
		options9.put(JavaCore.COMPILER_PB_MISSING_DEPRECATED_ANNOTATION, JavaCore.ERROR);
		fJProject.setOptions(options9);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		String str = """
				package test1;
				class E {
					int x = 0;
				}
				public final class C {
				    public String foo() {
				    	System.out.println(new E<String>());
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("C.java", str, false, null);

		String str1 = """
				package test1;
				class E {
					int x = 0;
				}
				public final class C {
				    public String foo() {
				    	System.out.println(new E());
				    }
				}
				""";
		Expected e1 = new Expected("Remove type arguments", str1);
		assertCodeActionExists(cu, e1);
	}

}
