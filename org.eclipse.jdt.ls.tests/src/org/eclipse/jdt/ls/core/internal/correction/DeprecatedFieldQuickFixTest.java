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

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.FixMessages;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class DeprecatedFieldQuickFixTest extends AbstractQuickFixTest {

    private IJavaProject fJProject;
    private IPackageFragmentRoot fSourceFolder;

    @Before
    public void setup() throws Exception {
        fJProject = newEmptyProject();
        Hashtable<String, String> options = TestOptions.getDefaultOptions();
        options.put(JavaCore.COMPILER_PB_DEPRECATION, JavaCore.WARNING);
        fJProject.setOptions(options);
        fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
    }

    @Test
    public void testReplaceDeprecatedField_externalInterface_staticAccess() throws Exception {
        IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
        String str2 = """
                package test2;

                public interface K {
                    String field1 = "abc";
                    String field2 = "def";
                }
                """;
        pack2.createCompilationUnit("K.java", str2, false, null);

        IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
        String str1 = """
                package test1;

                import test2.K;

                public interface B {
                    /**
                     * @deprecated use {@link K#field2} instead
                     */
                    @Deprecated
                    String field = "blah";
                }
                """;
        pack1.createCompilationUnit("B.java", str1, false, null);

        String str = """
                package test1;

                class E {
                    public void foo() {
                        String x = B.field;
                        System.out.println(x);
                    }
                }
                """;

        ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

        String expected = """
                package test1;

                import test2.K;

                class E {
                    public void foo() {
                        String x = K.field2;
                        System.out.println(x);
                    }
                }
                """;

        Range selection = CodeActionUtil.getRange(cu, "B.field");
        Expected e = new Expected(FixMessages.ReplaceDeprecatedField_msg, expected);
        assertCodeActions(cu, selection, e);
    }

    @Test
    public void testReplaceDeprecatedField_externalInterface_innerClassAccess() throws Exception {
        IPackageFragment pack2 = fSourceFolder.createPackageFragment("test2", false, null);
        String str2 = """
                package test2;

                public interface K {
                    String field1 = "abc";
                    String field2 = "def";
                }
                """;
        pack2.createCompilationUnit("K.java", str2, false, null);

        IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
        String str1 = """
                package test1;

                import test2.K;

                public interface B {
                    /**
                     * @deprecated use {@link K#field2} instead
                     */
                    @Deprecated
                    String field = "blah";
                }
                """;
        pack1.createCompilationUnit("B.java", str1, false, null);

        String str = """
                package test1;

                class E {
                    private class Z implements B {
                    }

                    public void foo() {
                        String x = new Z().field;
                        System.out.println(x);
                    }
                }
                """;

        ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

        String expected = """
                package test1;

                import test2.K;

                class E {
                    private class Z implements B {
                    }

                    public void foo() {
                        String x = K.field2;
                        System.out.println(x);
                    }
                }
                """;

        Range selection = CodeActionUtil.getRange(cu, "new Z().field");
        Expected e = new Expected(FixMessages.ReplaceDeprecatedField_msg, expected);
        assertCodeActions(cu, selection, e);
    }
}
