/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
import org.eclipse.jdt.internal.corext.fix.FixMessages;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class AssistQuickFixTest21 extends AbstractQuickFixTest {

	private IJavaProject fJProject;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setUp() throws Exception {
		fJProject = newEmptyProject();
		Map<String, String> options21 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options21, JavaCore.VERSION_21);
		fJProject.setOptions(options21);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertPatternInstanceofToSwitch1() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						if (x instanceof Integer xint) {
							i = xint.intValue();
						} else if (x instanceof Double xdouble) {
							d = xdouble.doubleValue();
						} else if (x instanceof Boolean xboolean) {
							b = xboolean.booleanValue();
						} else {
							i = 0;
							d = 0.0D;
							b = false;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", str1, false, null);
		assertNoErrors(cu.getResource());

		String expected = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						switch (x) {
							case Integer xint -> i = xint.intValue();
							case Double xdouble -> d = xdouble.doubleValue();
							case Boolean xboolean -> b = xboolean.booleanValue();
							case null, default -> {
								i = 0;
								d = 0.0D;
								b = false;
							}
						}
					}
				}
				""";

		Expected e = new Expected(FixMessages.PatternInstanceof_convert_if_to_switch, expected);
		Range selection = CodeActionUtil.getRange(cu, "doubleValue");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertPatternInstanceofToSwitch2() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						if (x instanceof Integer xint) {
							j = 7;
						} else if (x instanceof Double xdouble) {
							j = 8; // comment
						} else if (x instanceof Boolean xboolean) {
							j = 9;
						} else {
							i = 0;
							d = 0.0D;
							j = 10;
							b = false;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", str1, false, null);
		assertNoErrors(cu.getResource());

		String expected = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						switch (x) {
							case Integer xint -> j = 7;
							case Double xdouble -> j = 8; // comment
							case Boolean xboolean -> j = 9;
							case null, default -> {
								i = 0;
								d = 0.0D;
								j = 10;
								b = false;
							}
						}
					}
				}
				""";

		Expected e = new Expected(FixMessages.PatternInstanceof_convert_if_to_switch, expected);
		Range selection = CodeActionUtil.getRange(cu, "xboolean");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertPatternInstanceofToSwitch3() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class E {
					public int square(int x) {
						return x * x;
					}
					public int foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						if (y instanceof Integer xint) {
							return 7;
						} else if (y instanceof final Double xdouble) {
							return square(8); // square
						} else if (y instanceof final Boolean xboolean) {
							throw new NullPointerException();
						} else {
							i = 0;
							d = 0.0D;
							b = false;
							if (x instanceof Integer) {
								return 10;
							}
							return 11;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", str1, false, null);
		assertNoErrors(cu.getResource());

		String expected = """
				package test;

				public class E {
					public int square(int x) {
						return x * x;
					}
					public int foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						switch (y) {
							case Integer xint -> {
								return 7;
							}
							case Double xdouble -> {
								return square(8); // square
							}
							case Boolean xboolean -> throw new NullPointerException();
							case null, default -> {
								i = 0;
								d = 0.0D;
								b = false;
								if (x instanceof Integer) {
									return 10;
								}
								return 11;
							}
						}
					}
				}
				""";

		Expected e = new Expected(FixMessages.PatternInstanceof_convert_if_to_switch, expected);
		Range selection = CodeActionUtil.getRange(cu, "throw");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertPatternInstanceofToSwitch4() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						if (x instanceof Integer xint) {
							j = 7;
						} else if (x instanceof Double xdouble) {
							j = 8; // comment
						} else if (x instanceof Boolean xboolean) {
							j = 9;
						} else {
							i = 0;
							d = 0.0D;
							b = false;
							j = 10;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", str1, false, null);
		assertNoErrors(cu.getResource());

		String expected = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						j = switch (x) {
							case Integer xint -> 7;
							case Double xdouble -> 8; // comment
							case Boolean xboolean -> 9;
							case null, default -> {
								i = 0;
								d = 0.0D;
								b = false;
								yield 10;
							}
						};
					}
				}
				""";

		Expected e = new Expected(FixMessages.PatternInstanceof_convert_if_to_switch, expected);
		Range selection = CodeActionUtil.getRange(cu, "false");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertPatternInstanceofToSwitch5() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class E {
					public int square(int x) {
						return x * x;
					}
					public int foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						if (y instanceof Integer xint) {
							return 7;
						} else if (y instanceof final Double xdouble) {
							return square(8); // square
						} else if (y instanceof final Boolean xboolean) {
							throw new NullPointerException();
						} else {
							i = 0;
							d = 0.0D;
							b = false;
							return 10;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", str1, false, null);
		assertNoErrors(cu.getResource());

		String expected = """
				package test;

				public class E {
					public int square(int x) {
						return x * x;
					}
					public int foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						return switch (y) {
							case Integer xint -> 7;
							case Double xdouble -> square(8); // square
							case Boolean xboolean -> throw new NullPointerException();
							case null, default -> {
								i = 0;
								d = 0.0D;
								b = false;
								yield 10;
							}
						};
					}
				}
				""";

		Expected e = new Expected(FixMessages.PatternInstanceof_convert_if_to_switch, expected);
		Range selection = CodeActionUtil.getRange(cu, "false");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testDoNotConvertPatternInstanceofToSwitch1() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class E {
					public void foo(Object x, Object y) {
						int i, j;
						double d;
						boolean b;
						if (x instanceof Integer xint) {
							i = xint.intValue();
						} else if (y instanceof Double xdouble) {
							d = xdouble.doubleValue();
						} else if (x instanceof Boolean xboolean) {
							b = xboolean.booleanValue();
						} else {
							i = 0;
							d = 0.0D;
							b = false;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", str1, false, null);
		assertNoErrors(cu.getResource());
		assertCodeActionNotExists(cu, FixMessages.PatternInstanceof_convert_if_to_switch);
	}

}
