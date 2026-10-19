/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.FixMessages;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeprecatedMethodQuickFixTest extends AbstractQuickFixTest {

    private IJavaProject fJProject;
    private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
    public void setup() throws Exception {
        fJProject = newEmptyProject();
		Map<String, String> options = TestOptions.getDefaultOptions();
        options.put(JavaCore.COMPILER_PB_DEPRECATION, JavaCore.WARNING);
        fJProject.setOptions(options);
        fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
    }

    @Test
    public void testSelectionOnMethod() throws Exception {
        ICompilationUnit cu = createDeprecatedMethodTestUnits();
        String expected = """
                package test;

                public class E {

                    public void test() {
                        K.newMethod();
                    }
                }
                """;
        Range selection = CodeActionUtil.getRange(cu, "deprecatedMethod");
        Expected e = new Expected(FixMessages.InlineDeprecatedMethod_msg, expected);
        assertCodeActions(cu, selection, e);
    }

    @Test
    public void testCaretInsideMethod() throws Exception {
        ICompilationUnit cu = createDeprecatedMethodTestUnits();
        Position start = CodeActionUtil.getRange(cu, "deprecatedMethod").getStart();
        Range selection = new Range(new Position(start.getLine(), start.getCharacter() + 1), new Position(start.getLine(), start.getCharacter() + 1));

        String expected = """
                package test;

                public class E {

                    public void test() {
                        K.newMethod();
                    }
                }
                """;
        Expected e = new Expected(FixMessages.InlineDeprecatedMethod_msg, expected);
        assertCodeActions(cu, selection, e);
    }

    private ICompilationUnit createDeprecatedMethodTestUnits() throws Exception {
        IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

        String str2 = """
                package test;

                public class K {
                    public static void newMethod() {
                        System.out.println("Calling newMethod()");
                    }
                }
                """;
        pack.createCompilationUnit("K.java", str2, false, null);

        String str1 = """
                package test;

                public class B {

                    /**
                     * @deprecated use {@link K#newMethod()} instead
                     */
                    @Deprecated
                    public static void deprecatedMethod() {
                        K.newMethod();
                    }
                }
                """;
        pack.createCompilationUnit("B.java", str1, false, null);

        String str = """
                package test;

                public class E {

                    public void test() {
                        B.deprecatedMethod();
                    }
                }
                """;
        return pack.createCompilationUnit("E.java", str, false, null);
    }
}
