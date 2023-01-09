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

		String contents = "" + //
				"package test1;\n" + //
				"public class A implements Runnable {\n" + //
				"    public void run() {} \n" + //
				"    /**\n" + //
				"     * @deprecated\n" + //
				"     */\n" + //
				"    public void destroy() {} \n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Collections.emptyList(), null);
		assertEquals(0, textEdits.size());
	}

	@Test
	public void testOverrideCleanUp() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, monitor);

		String contents = "" + //
				"package test1;\n" + //
				"public class A implements Runnable {\n" + //
				"    public void run() {} \n" + //
				"    /**\n" + //
				"     * @deprecated\n" + //
				"     */\n" + //
				"    public void destroy() {} \n" + //
				"}\n";
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

		String contents = "" + //
				"package test1;\n" + //
				"public class A implements Runnable {\n" + //
				"    public void run() {} \n" + //
				"    /**\n" + //
				"     * @deprecated\n" + //
				"     */\n" + //
				"    public void destroy() {} \n" + //
				"}\n";
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
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private int value;\n" + //
				"    public int getValue() {\n" + //
				"        return value;\n" + //
				"    }\n" + //
				"}\n";
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
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private static final int VALUE = 10;\n" + //
				"    public int getValue() {\n" + //
				"        return VALUE;\n" + //
				"    }\n" + //
				"}\n";
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
		String contents = "" + //
				"package test1;\n" + //
				"import static java.lang.System.out;\n" + //
				"public class A {\n" + //
				"    private static final int VALUE = 10;\n" + //
				"    public int getValue() {\n" + //
				"        out.println(\"moo\");\n" + //
				"        return A.VALUE;\n" + //
				"    }\n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "" + //
				"package test1;\n" + //
				"import static java.lang.System.out;\n" + //
				"public class A {\n" + //
				"    private static final int VALUE = 10;\n" + //
				"    public int getValue() {\n" + //
				"        System.out.println(\"moo\");\n" + //
				"        return A.VALUE;\n" + //
				"    }\n" + //
				"}\n";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

	@Test
	public void testAccessCleanUpsDontInterfere() throws Exception {
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private static final int NUMBER = 10;\n" + //
				"    private static final int value;\n" + //
				"    public int getValue() {\n" + //
				"        return this.value;\n" + //
				"    }\n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers", "qualifyMembers"), monitor);
		assertEquals(0, textEdits.size());

		contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private static final int NUMBER = 10;\n" + //
				"    private static final int value;\n" + //
				"    public int getValue() {\n" + //
				"        return A.NUMBER;\n" + //
				"    }\n" + //
				"}\n";
		unit = pack1.createCompilationUnit("A.java", contents, true, monitor);

		textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers", "qualifyMembers"), monitor);
		assertEquals(0, textEdits.size());
	}

	@Test
	public void testInvertEqualsCleanUp() throws Exception {
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    String message;\n" + //
				"    boolean result1 = message.equals(\"text\");\n" + //
				"    boolean result2 = message.equalsIgnoreCase(\"text\")" + //
				"}\n";
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
		String contents = "package test1;\n" //
				+ "\n" //
				+ "public class AddModifier {\n" //
				+ "\n" //
				+ "private String label1;\n" //
				+ "protected String label2;\n" //
				+ "public String label3;\n" //
				+ "private String label4 = \"\";\n" //
				+ "protected String label5 = \"\";\n" //
				+ "public String label6 = \"\";\n" //
				+ "private final String label7 = \"\";\n" //
				+ "\n" //
				+ "public void test(String foo) {\n" //
				+ "    String label8, label9 = \"\";\n" //
				+ "    String label10;\n" //
				+ "    String label11 = \"\";\n" //
				+ "    final String label12 = \"\";\n" //
				+ "	}\n" //
				+ "}";

		ICompilationUnit unit = pack1.createCompilationUnit("AddModifier.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("addFinalModifier"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = "package test1;\n" //
				+ "\n" //
				+ "public class AddModifier {\n" //
				+ "\n" //
				+ "private String label1;\n" //
				+ "protected String label2;\n" //
				+ "public String label3;\n" //
				+ "private final String label4 = \"\";\n" //
				+ "protected String label5 = \"\";\n" //
				+ "public String label6 = \"\";\n" //
				+ "private final String label7 = \"\";\n" //
				+ "\n" //
				+ "public void test(final String foo) {\n" //
				+ "    final String label8, label9 = \"\";\n" //
				+ "    final String label10;\n" //
				+ "    final String label11 = \"\";\n" //
				+ "    final String label12 = \"\";\n" //
				+ "	}\n" //
				+ "}";

		assertEquals(expected, actual);
	}

	@Test
	public void testConvertToSwitchExpression() throws Exception {
		String contents = "package test1;\n" //
				+ "\n" //
				+ "public class SwitchExpression {\n" //
				+ "    public void test() {\n" //
				+ "        Day day2 = Day.THURSDAY;\n" //
				+ "        String message2;\n" //
				+ "        switch (day2) {\n" //
				+ "            case SATURDAY:\n" //
				+ "            case SUNDAY:\n" //
				+ "                message2 = \"Weekend!\";\n" //
				+ "                break;\n" //
				+ "            case MONDAY:\n" //
				+ "            case TUESDAY:\n" //
				+ "            case WEDNESDAY:\n" //
				+ "            case THURSDAY:\n" //
				+ "            case FRIDAY:\n" //
				+ "                message2 = \"Weekday\";\n" //
				+ "                break;\n" //
				+ "            default:\n" //
				+ "                message2 = \"???\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public enum Day {\n" //
				+ "        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY\n" //
				+ "    };\n" //
				+ "}";

		ICompilationUnit unit = pack1.createCompilationUnit("SwitchExpression.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("switchExpression"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = "package test1;\n" //
				+ "\n" //
				+ "public class SwitchExpression {\n" //
				+ "    public void test() {\n" //
				+ "        Day day2 = Day.THURSDAY;\n" //
				+ "        String message2 = switch (day2) {\n" //
				+ "        case SATURDAY, SUNDAY -> \"Weekend!\";\n" //
				+ "        case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> \"Weekday\";\n" //
				+ "        default -> \"???\";\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public enum Day {\n" //
				+ "        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY\n" //
				+ "    };\n" //
				+ "}";

		assertEquals(expected, actual);
	}

	@Test
	public void testPatternMatchInstanceof() throws Exception {
		String contents = "package test1;\n" //
				+ "\n" //
				+ "public class InstanceofPatternMatch {\n" //
				+ "	public void test() {\n" //
				+ "		Object str = new String(\"test\");\n" //
				+ "		if (str instanceof String) {\n" //
				+ "			String real = (String) str;\n" //
				+ "			System.out.println(real.substring(0));\n" //
				+ "		}\n" //
				+ "	}\n" //
				+ "}";

		ICompilationUnit unit = pack1.createCompilationUnit("InstanceofPatternMatch.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("instanceofPatternMatch"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = "package test1;\n" //
				+ "\n" //
				+ "public class InstanceofPatternMatch {\n" //
				+ "	public void test() {\n" //
				+ "		Object str = new String(\"test\");\n" //
				+ "		if (str instanceof String real) {\n" //
				+ "			System.out.println(real.substring(0));\n" //
				+ "		}\n" //
				+ "	}\n" //
				+ "}";

		assertEquals(expected, actual);
	}

	@Test
	public void testLambdaExpression() throws Exception {
		String contents = "package test1;\n" //
				+ "\n" //
				+ "import java.util.function.IntConsumer;\n" //
				+ "\n" //
				+ "public class LambdaExpression {\n" //
				+ "    public void test() {\n" //
				+ "        IntConsumer c = new IntConsumer() {\n" //
				+ "            @Override\n" //
				+ "            public void accept(int value) {\n" //
				+ "                System.out.println(value);\n" //
				+ "	           }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}";

		ICompilationUnit unit = pack1.createCompilationUnit("LambdaExpression.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("lambdaExpression"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		String expected = "package test1;\n" //
				+ "\n" //
				+ "import java.util.function.IntConsumer;\n" //
				+ "\n" //
				+ "public class LambdaExpression {\n" //
				+ "    public void test() {\n" //
				+ "        IntConsumer c = value -> System.out.println(value);\n" //
				+ "    }\n" //
				+ "}";

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
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("lambdaExpression", "instanceofPatternMatch", "stringConcatToTextBlock", "addFinalModifier"), monitor);
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
		String contents = "" + //
				"package test1;\n" + //
				"import java.io.FileInputStream;\n" + //
				"import java.io.IOException;\n" + //
				"public class A {\n" + //
				"    public void test() {\n" + //
				"        final FileInputStream inputStream = new FileInputStream(\"out.txt\");\n" + //
				"        try {\n" + //
				"            System.out.println(inputStream.read());\n" + //
				"        } finally {\n" + //
				"            inputStream.close();\n" + //
				"        }\n" + //
				"    }\n" + //
				"}\n";

		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		String expected = "" + //
				"package test1;\n" + //
				"import java.io.FileInputStream;\n" + //
				"import java.io.IOException;\n" + //
				"public class A {\n" + //
				"    public void test() {\n" + //
				"        final FileInputStream inputStream = new FileInputStream(\"out.txt\");\n" + //
				"        try (inputStream) {\n" + //
				"            System.out.println(inputStream.read());\n" + //
				"        }\n" + //
				"    }\n" + //
				"}\n";
		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("tryWithResource"), monitor);
		String actual = TextEditUtil.apply(unit, textEdits);
		assertEquals(expected, actual);
	}

}
