package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.ProjectScope;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompletionHandlerChainTest extends AbstractCompilationUnitBasedTest {

	private static String COMPLETION_TEMPLATE = """
			{
			    "id": "1",
			    "method": "textDocument/completion",
			    "params": {
			        "textDocument": {
			            "uri": "${file}"
			        },
			        "position": {
			            "line": ${line},
			            "character": ${char}
			        },
					"context": {
						"triggerKind": 1
					}
			    },
			    "jsonrpc": "2.0"
			}""";

	@BeforeEach
	public void setUp() {
		mockLSP3Client();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		preferences.setPostfixCompletionEnabled(false);
		preferences.setChainCompletionEnabled(true);
		Preferences.DISCOVERED_STATIC_IMPORTS.clear();
		increaseChainCompletionTimeout();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		return requestCompletions(unit, completeBehind, 0);
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind, int fromIndex) throws JavaModelException {
		int[] loc = findCompletionLocation(unit, completeBehind, fromIndex);
		return server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit)).replace("${line}", String.valueOf(line)).replace("${char}", String.valueOf(kar));
	}

	private void mockLSP3Client() {
		mockLSPClient(true, true);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSuported) {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
	}

	private void increaseChainCompletionTimeout() {
		// if they don't finish within 5secs we might have a performance issue.
		new ProjectScope(project.getProject()).getNode(JavaManipulation.getPreferenceNodeId()).putInt("recommenders.chain.timeout", 5);
	}

	@Test
	public void testChainCompletionsOnParameter() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.stream.Stream;
						public class Foo {
						    public static void main(String[] args) {
								Stream.of("1").collect()
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "collect(");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("toList")).collect(Collectors.toList());
		assertEquals(1, completionItems.size(), "toList completion count");

		CompletionItem completionItem = completionItems.get(0);
		assertNotNull(completionItem);
		assertEquals("Collectors.toList()", completionItem.getTextEdit().getLeft().getNewText(), "Completion getTextEditText");
		assertNotNull(completionItem.getLabel());
		assertEquals("Collectors.toList() : Collector<T,?,List<T>>", completionItem.getLabel(), "Completion Label");
		assertEquals("java.util.stream.Collectors.Collectors.toList() : Collector<T,?,List<T>>", completionItem.getDetail(), "Completion Details");
		assertNotNull(completionItem.getAdditionalTextEdits());
		assertEquals(1, completionItem.getAdditionalTextEdits().size(), "Additional edits count");
		assertNotNull(completionItem.getAdditionalTextEdits().get(0));
		assertEquals("import java.util.stream.Collectors;\n", completionItem.getAdditionalTextEdits().get(0).getNewText(), "Import");
	}

	@Test
	public void testChainCompletionsOnVariable() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
						    public static void main(String[] args) {
								List<String> names =
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names =");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("emptyList")).collect(Collectors.toList());
		assertEquals(1, completionItems.size(), "emptyList completion count");

		CompletionItem completionItem = completionItems.get(0);
		assertNotNull(completionItem);
		assertEquals("Collections.emptyList()", completionItem.getTextEdit().getLeft().getNewText(), "Completion getTextEditText");

		assertNotNull(completionItem.getAdditionalTextEdits());
		assertEquals(1, completionItem.getAdditionalTextEdits().size(), "Additional edits count");
		assertNotNull(completionItem.getAdditionalTextEdits().get(0));
		assertEquals("import java.util.Collections;\n", completionItem.getAdditionalTextEdits().get(0).getNewText(), "Import");
	}

	@Test
	public void testChainCompletionsOnVariableWithNewKeywordExpectNoChains() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
						    public static void main(String[] args) {
								List<String> names = new
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names = new");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().endsWith("emptyList() <T>")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count");
	}

	@Test
	public void testChainCompletionsOnVariableCompletingConstructorExpectNoChains() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
						    public static void main(String[] args) {
								List<String> names = new Arr
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names = new Arr");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().endsWith("emptyList() <T>")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count");
	}

	@Test
	public void testChainCompletionsOnChainsFromVisibleVariables() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
							public class Stream {
								public List<String> toList() {
									return null;
								}
							}

						    public static void main(String[] args) {
								Stream stream = new Stream();
								Stream[] streams = new Stream[0];
								List<String> names =
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names =");
		var item = list.getItems().stream().filter(i -> i.getLabel().startsWith("stream.")).findFirst();
		assertTrue(item.isPresent(), "completion");
		assertEquals("stream.toList() : List<String>", item.get().getLabel(), "completion label");
		assertEquals("stream.toList()", item.get().getTextEdit().getLeft().getNewText(), "completion edit text");

		item = list.getItems().stream().filter(i -> i.getLabel().startsWith("streams[")).findFirst();
		assertTrue(item.isPresent(), "array completion");
		assertEquals("streams[].toList() : List<String>", item.get().getLabel(), "array completion label");
		assertEquals("streams[${1:i}].toList()", item.get().getTextEdit().getLeft().getNewText(), "array completion edit text");

	}

	@Test
	public void testChainCompletionsOnChainsCorrectSnippetPlaceholders() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
							public class Stream {
								public List<String> toList(int size) {
									return null;
								}
							}

						    public static void main(String[] args) {
								Stream stream = new Stream();
								Stream[] streams = new Stream[0];
								List<String> names =
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names =");
		var item = list.getItems().stream().filter(i -> i.getLabel().startsWith("streams[].")).findFirst();
		assertTrue(item.isPresent(), "completion");
		assertEquals("streams[].toList(int size) : List<String>", item.get().getLabel(), "completion label");
		assertEquals("streams[${1:i}].toList(${2:size})", item.get().getTextEdit().getLeft().getNewText(), "completion edit text");
	}

	@Test
	public void testChainCompletionsOnPrimitiveVariableExpectNoCompletions() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						public class Foo {
						    public static boo(IntChain chain) {
								Integer variable = ;
						    }

							static class IntChain {
								public Integer newInt() {
									return 1;
								}
							}
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "variable = ");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("newInt")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count");
	}

	@Test
	public void testChainCompletionsOnStringVariableExpectNoCompletions() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						public class Foo {
						    public static boo(StringChain chain) {
								String variable = //
								"variable".concat("");
						    }

							static class StringChain {
								public String newString() {
									return "";
								}
							}
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "variable = ");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("newString")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count [binding]");

		list = requestCompletions(unit, "\"variable\".concat(");
		completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("newString")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count [type]");
	}

	@Test
	public void testChainCompletionsOnObjectVariableExpectNoCompletions() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						public class Foo {
						    public static boo(ObjectChain chain) {
								Object variable = //
								chain.equals(variable);
						    }

							static class ObjectChain {
								public Object newObject() {
									return new Object();
								}
							}
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "variable = ");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("newObject")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count");

		list = requestCompletions(unit, "chain.equals(");
		completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("newObject")).collect(Collectors.toList());
		assertEquals(0, completionItems.size(), "emptyList completion count [type]");
	}

	@Test
	public void testChainCompletionsWithToken_ExpectReplaceToken() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
						    public static void main(String[] args) {
								List<String> names = empty
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names = empty");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().contains("emptyList")).collect(Collectors.toList());
		assertEquals(1, completionItems.size(), "emptyList completion count");

		CompletionItem completionItem = completionItems.get(0);
		assertNotNull(completionItem);
		assertEquals("Collections.emptyList()", completionItem.getTextEdit().getLeft().getNewText(), "Completion getTextEditText");
		var loc = findCompletionLocation(unit, "names = empty", 0);
		var expected = new Position(loc[0], loc[1] - "empty".length());
		assertEquals(expected, completionItem.getTextEdit().getLeft().getRange().getStart(), "Completion TextEdit Range");
	}

	@Test
	public void testChainCompletionsOnVariableWithTokenMatchingEdge() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
						    public static void main(String[] args) {
								List<String> names = emptyL
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names = emptyL");
		assertEquals(2, list.getItems().size());
		assertTrue(list.getItems().stream().anyMatch(i -> i.getLabel().matches(".*\\.emptyList().*")), "emptyList");
		assertTrue(list.getItems().stream().anyMatch(i -> i.getLabel().matches(".*\\.EMPTY_LIST.*")), "EMPTY_LIST");
	}

	@Test
	public void testChainCompletionsOnVariableWithTokenMatchingStart() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"""
						import java.util.List;
						public class Foo {
						    public static void main(String[] args) {
								List<String> names = Coll
						    }
						}
						""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "names = Coll");
		assertTrue(list.getItems().size() > 0);
		assertTrue(list.getItems().stream().anyMatch(i -> i.getLabel().matches("Collections\\..*")), "All Collections.*");
	}
}
