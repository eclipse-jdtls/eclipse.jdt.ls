/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.cleanup;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractMavenBasedTest;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for clean ups.
 */
public class CleanUpsTest extends AbstractMavenBasedTest {

	public static CleanUpRegistry registry = new CleanUpRegistry();

	private IJavaProject javaProject;
	private IPackageFragmentRoot fSourceFolder;
	private IProject project;
	private IPackageFragment pack1;

	@Before
	public void setup() throws Exception {
		importProjects("maven/quickstart");
		project = WorkspaceHelper.getProject("quickstart");
		javaProject = JavaCore.create(project);

		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_19, options);
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, String.valueOf(99));
		javaProject.setOptions(options);

		fSourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src/main/java"));
		File src = fSourceFolder.getResource().getLocation().toFile();
		src.mkdirs();
		project.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		pack1 = fSourceFolder.createPackageFragment("test1", false, monitor);
	}

	@After
	public void teardown() throws Exception {
		pack1.delete(false, null);
	}

	@Test
	public void testNoCleanUp() throws Exception {

		String contents = """
			package test1;
			public class A implements Runnable {
			    public void run() {}\s
			    /**
			     * @deprecated
			     */
			    public void destroy() {}\s
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Collections.emptyList(), null);
		assertEquals(0, textEdits.size());
	}

	@Test
	public void testOverrideCleanUp() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, monitor);

		String contents = """
			package test1;
			public class A implements Runnable {
			    public void run() {}\s
			    /**
			     * @deprecated
			     */
			    public void destroy() {}\s
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "package test1;\n" //
				+ "public class A implements Runnable {\n" //
				+ "    @Override\n" //
				+ "    public void run() {} \n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    public void destroy() {} \n" //
				+ "}\n" //
				+ "";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("addOverride"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testDeprecatedCleanUp() throws Exception {

		String contents = """
			package test1;
			public class A implements Runnable {
			    public void run() {}\s
			    /**
			     * @deprecated
			     */
			    public void destroy() {}\s
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "package test1;\n" //
				+ "public class A implements Runnable {\n" //
				+ "    public void run() {} \n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    public void destroy() {} \n" //
				+ "}\n" //
				+ "";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("addDeprecated"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testUseThisCleanUp() throws Exception {
		String contents = """
			package test1;
			public class A {
			    private int value;
			    public int getValue() {
			        return value;
			    }
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "package test1;\n" //
				+ "public class A {\n" //
				+ "    private int value;\n" //
				+ "    public int getValue() {\n" //
				+ "        return this.value;\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyMembers"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testUseClassNameCleanUp1() throws Exception {
		String contents = """
			package test1;
			public class A {
			    private static final int VALUE = 10;
			    public int getValue() {
			        return VALUE;
			    }
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "package test1;\n" //
				+ "public class A {\n" //
				+ "    private static final int VALUE = 10;\n" //
				+ "    public int getValue() {\n" //
				+ "        return A.VALUE;\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testUseClassNameCleanUp2() throws Exception {
		String contents = """
			package test1;
			import static java.lang.System.out;
			public class A {
			    private static final int VALUE = 10;
			    public int getValue() {
			        out.println("moo");
			        return A.VALUE;
			    }
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = """
			package test1;
			import static java.lang.System.out;
			public class A {
			    private static final int VALUE = 10;
			    public int getValue() {
			        System.out.println("moo");
			        return A.VALUE;
			    }
			}
			""";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testAccessCleanUpsDontInterfere() throws Exception {
		String contents = """
			package test1;
			public class A {
			    private static final int NUMBER = 10;
			    private static final int value;
			    public int getValue() {
			        return this.value;
			    }
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers", "qualifyMembers"), monitor);
		assertEquals(0, textEdits.size());

		contents = """
			package test1;
			public class A {
			    private static final int NUMBER = 10;
			    private static final int value;
			    public int getValue() {
			        return A.NUMBER;
			    }
			}
			""";
		unit = pack1.createCompilationUnit("A.java", contents, true, monitor);

		textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers", "qualifyMembers"), monitor);
		assertEquals(0, textEdits.size());
	}

	@Test
	public void testInvertEqualsCleanUp() throws Exception {
		String contents = """
			package test1;
			public class A {
			    String message;
			    boolean result1 = message.equals("text");
			    boolean result2 = message.equalsIgnoreCase("text")\
			}
			""";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "package test1;\n" //
				+ "public class A {\n" //
				+ "    String message;\n" //
				+ "    boolean result1 = \"text\".equals(message);\n" //
				+ "    boolean result2 = \"text\".equalsIgnoreCase(message)}\n" //
				+ "";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("invertEquals"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testAddFinalModifiersWherePossible() throws Exception {
		String contents = """
			package test1;

			public class AddModifier {

			private String label1;
			protected String label2;
			public String label3;
			private String label4 = "";
			protected String label5 = "";
			public String label6 = "";
			private final String label7 = "";

			public void test(String foo) {
			    String label8, label9 = "";
			    String label10;
			    String label11 = "";
			    final String label12 = "";
				}
			}""";

		ICompilationUnit unit = pack1.createCompilationUnit("AddModifier.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("addFinalModifier"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = """
			package test1;

			public class AddModifier {

			private String label1;
			protected String label2;
			public String label3;
			private final String label4 = "";
			protected String label5 = "";
			public String label6 = "";
			private final String label7 = "";

			public void test(final String foo) {
			    final String label8, label9 = "";
			    final String label10;
			    final String label11 = "";
			    final String label12 = "";
				}
			}""";

		assertEquals(expected, actual);
	}

	@Test
	public void testConvertToSwitchExpression() throws Exception {
		String contents = """
			package test1;

			public class SwitchExpression {
			    public void test() {
			        Day day2 = Day.THURSDAY;
			        String message2;
			        switch (day2) {
			            case SATURDAY:
			            case SUNDAY:
			                message2 = "Weekend!";
			                break;
			            case MONDAY:
			            case TUESDAY:
			            case WEDNESDAY:
			            case THURSDAY:
			            case FRIDAY:
			                message2 = "Weekday";
			                break;
			            default:
			                message2 = "???";
			        }
			    }

			    public enum Day {
			        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
			    };
			}""";

		ICompilationUnit unit = pack1.createCompilationUnit("SwitchExpression.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("switchExpression"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = """
			package test1;

			public class SwitchExpression {
			    public void test() {
			        Day day2 = Day.THURSDAY;
			        String message2 = switch (day2) {
			        case SATURDAY, SUNDAY -> "Weekend!";
			        case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> "Weekday";
			        default -> "???";
			        };
			    }

			    public enum Day {
			        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
			    };
			}""";

		assertEquals(expected, actual);
	}

	@Test
	public void testPatternMatchInstanceof() throws Exception {
		String contents = """
			package test1;

			public class InstanceofPatternMatch {
				public void test() {
					Object str = new String("test");
					if (str instanceof String) {
						String real = (String) str;
						System.out.println(real.substring(0));
					}
				}
			}""";

		ICompilationUnit unit = pack1.createCompilationUnit("InstanceofPatternMatch.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("instanceofPatternMatch"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = """
			package test1;

			public class InstanceofPatternMatch {
				public void test() {
					Object str = new String("test");
					if (str instanceof String real) {
						System.out.println(real.substring(0));
					}
				}
			}""";

		assertEquals(expected, actual);
	}

	@Test
	public void testLambdaExpression() throws Exception {
		String contents = """
			package test1;

			import java.util.function.IntConsumer;

			public class LambdaExpression {
			    public void test() {
			        IntConsumer c = new IntConsumer() {
			            @Override
			            public void accept(int value) {
			                System.out.println(value);
				           }
			        };
			    }
			}""";

		ICompilationUnit unit = pack1.createCompilationUnit("LambdaExpression.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("lambdaExpressionFromAnonymousClass"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = """
			package test1;

			import java.util.function.IntConsumer;

			public class LambdaExpression {
			    public void test() {
			        IntConsumer c = value -> System.out.println(value);
			    }
			}""";

		assertEquals(expected, actual);
	}

	@Test
	public void testMultiCleanup() throws Exception {
		String contents = "package test1;\n"
				+ "\n"
				+ "import java.io.File;\n"
				+ "import java.io.FileFilter;\n"
				+ "import java.util.Arrays;\n"
				+ "\n"
				+ "public class MutliCleanup {\n"
				+ "    public void test() {\n"
				+ "        String PATH = \"/this/is/some/path\";\n"
				+ "        String MESSAGE = \"This is a message.\" +\n"
				+ "                \"This message has multiple lines.\" +\n"
				+ "                \"We can convert it to a text block\";\n"
				+ "\n"
				+ "        Object[] obj = Arrays.asList(PATH).toArray();\n"
				+ "        if (obj[0] instanceof String) {\n"
				+ "            String tmp = (String) obj[0];\n"
				+ "            File f = new File(tmp);\n"
				+ "            File[] filtered = f.listFiles(new FileFilter() {\n"
				+ "                @Override\n"
				+ "                public boolean accept(File path) {\n"
				+ "                    return true;\n"
				+ "                }\n"
				+ "            });\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n"
				+ "";

		ICompilationUnit unit = pack1.createCompilationUnit("MultiCleanup.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("lambdaExpressionFromAnonymousClass", "instanceofPatternMatch", "stringConcatToTextBlock", "addFinalModifier"),
				monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = "package test1;\n"
				+ "\n"
				+ "import java.io.File;\n"
				+ "import java.io.FileFilter;\n"
				+ "import java.util.Arrays;\n"
				+ "\n"
				+ "public class MutliCleanup {\n"
				+ "    public void test() {\n"
				+ "        final String PATH = \"/this/is/some/path\";\n"
				+ "        final String MESSAGE = \"\"\"\n"
				+ "        	This is a message.\\\n"
				+ "        	This message has multiple lines.\\\n"
				+ "        	We can convert it to a text block\"\"\";\n"
				+ "\n"
				+ "        final Object[] obj = Arrays.asList(PATH).toArray();\n"
				+ "        if (obj[0] instanceof final String tmp) {\n"
				+ "            final File f = new File(tmp);\n"
				+ "            final File[] filtered = f.listFiles((FileFilter) path -> true);\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n"
				+ "";

		assertEquals(expected, actual);
	}

	@Test
	public void testTryWithResourceCleanUp() throws Exception {
		String contents = """
			package test1;
			import java.io.FileInputStream;
			import java.io.IOException;
			public class A {
			    public void test() {
			        final FileInputStream inputStream = new FileInputStream("out.txt");
			        try {
			            System.out.println(inputStream.read());
			        } finally {
			            inputStream.close();
			        }
			    }
			}
			""";

		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = """
			package test1;
			import java.io.FileInputStream;
			import java.io.IOException;
			public class A {
			    public void test() {
			        final FileInputStream inputStream = new FileInputStream("out.txt");
			        try (inputStream) {
			            System.out.println(inputStream.read());
			        }
			    }
			}
			""";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("tryWithResource"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

}
