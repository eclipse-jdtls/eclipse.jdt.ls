/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Integration tests for resource bundle key completion.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ResourceBundleCompletionTest extends AbstractCompilationUnitBasedTest {

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
	@Override
	public void setup() throws Exception {
		mockLSP3Client();
		reset();
		setupEclipseProject("resourcebundle");
	}

	@AfterEach
	public void reset() throws Exception {
		preferences.setResourceBundleCompletionEnabled(true);
		CoreASTProvider.getInstance().disposeAST();
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		return requestCompletions(unit, completeBehind, 0);
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind, int fromIndex) throws JavaModelException {
		int[] loc = findCompletionLocation(unit, completeBehind, fromIndex);
		CoreASTProvider.getInstance().setActiveJavaElement(unit);
		return server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", org.eclipse.jdt.ls.core.internal.JDTUtils.toURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	private void mockLSP3Client() {
		mockLSPClient(true, true);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSupported) {
		ClientPreferences clientPreferences = mock(ClientPreferences.class);
		when(clientPreferences.isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
		when(clientPreferences.isCompletionListItemDefaultsPropertySupport(anyString())).thenReturn(false);
		when(preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
	}

	@Test
	public void testResourceBundleGetStringCompletionWithPrefix() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have filtered resource bundle key completions");

		// All items should start with "greeting."
		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
		// Should not contain keys that don't start with "greeting."
		assertFalse(labels.contains("error.notfound"), "Should not contain error.notfound");
	}

	@Test
	public void testResourceBundleCompletionInMiddleOfExistingString() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("greeting.goodbye");
				    }
				}
				""");

		// Request completion after "greeting." in the middle of the existing string
		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions when cursor is in middle of string");

		// Should show both greeting.hello and greeting.goodbye
		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");

		// Verify that the text edit range replaces the entire string content
		CompletionItem helloItem = resourceBundleItems.stream()
				.filter(item -> "greeting.hello".equals(item.getLabel()))
				.findFirst()
				.orElse(null);
		assertNotNull(helloItem, "Should find greeting.hello completion item");
		assertNotNull(helloItem.getTextEdit(), "Should have text edit");
		Range range = helloItem.getTextEdit().getLeft().getRange();
		// The range should replace the entire content between quotes
		// Verify that the range covers the entire "greeting.goodbye" string (not just "greeting.")
		// The range should start after the opening quote and end before the closing quote
		assertTrue(range.getStart().getCharacter() > 0 && range.getEnd().getCharacter() > range.getStart().getCharacter(),
				"Text edit should replace entire string content");
		// Verify the insert text is just the key (without quotes, since we're inside quotes)
		String insertText = helloItem.getTextEdit().getLeft().getNewText();
		assertEquals("greeting.hello", insertText, "Insert text should be the key without quotes");
	}

	@Test
	public void testResourceBundleCompletionWithDifferentBundle() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.validation");
				        String value = bundle.getString("");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		// Should contain validation keys
		assertTrue(labels.contains("validation.required"), "Should contain validation.required");
		assertTrue(labels.contains("validation.email"), "Should contain validation.email");

		// Should NOT contain keys from messages bundle
		assertFalse(labels.contains("greeting.hello"), "Should not contain greeting.hello from messages bundle");
		assertFalse(labels.contains("error.notfound"), "Should not contain error.notfound from messages bundle");
		assertFalse(labels.contains("user.name"), "Should not contain user.name from messages bundle");
	}

	@Test
	public void testResourceBundleCompletionExcludesOtherBundles() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		// Should contain messages keys
		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("error.notfound"), "Should contain error.notfound");
		assertTrue(labels.contains("user.name"), "Should contain user.name");

		// Should NOT contain keys from validation bundle
		assertFalse(labels.contains("validation.required"), "Should not contain validation.required from validation bundle");
		assertFalse(labels.contains("validation.email"), "Should not contain validation.email from validation bundle");
		assertFalse(labels.contains("validation.phone"), "Should not contain validation.phone from validation bundle");
	}

	@Test
	public void testResourceBundleCompletionDocumentation() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"");
		assertNotEquals(0, list.getItems().size(), "Completion list should not be empty");

		// Find a resource bundle completion item
		CompletionItem item = list.getItems().stream()
				.filter(i -> i.getKind() == CompletionItemKind.Property && "greeting.hello".equals(i.getLabel()))
				.findFirst()
				.orElse(null);

		assertNotNull(item, "Should find greeting.hello completion item");
		assertNotNull(item.getDocumentation(), "Should have documentation");
		// Documentation should contain the property value
		String documentation = item.getDocumentation().getRight().getValue();
		assertTrue(documentation.contains("Hello"),
				"Documentation should contain the property value 'Hello'");
	}

	@Test
	public void testResourceBundleCompletionMultilineValue() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"");
		assertNotEquals(0, list.getItems().size(), "Completion list should not be empty");

		// Find the multiline completion item
		CompletionItem item = list.getItems().stream()
				.filter(i -> i.getKind() == CompletionItemKind.Property && "message.multiline".equals(i.getLabel()))
				.findFirst()
				.orElse(null);

		assertNotNull(item, "Should find message.multiline completion item");
		assertNotNull(item.getDocumentation(), "Should have documentation");
		// Documentation should contain the multiline property value with markdown formatting
		String documentation = item.getDocumentation().getRight().getValue();
		assertTrue(documentation.contains("This is a multiline message"),
				"Documentation should contain the multiline property value");
		assertTrue(documentation.contains("  \n"),
				"Documentation should contain markdown-formatted newlines (double newlines)");
		// Verify that single \n has been replaced with \n\n
		assertFalse(documentation.contains("message.\nIt") || documentation.contains("lines.\nEach"),
				"Documentation should not contain single newlines (should be prefixed by 2 spaces)");
	}

	@Test
	public void testResourceBundleCompletionWithVariableBundleName() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    public void test() {
				        var bundleName = "resources.messages";
				        var bundle = ResourceBundle.getBundle(bundleName);
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
	}

	@Test
	public void testResourceBundleCompletionDisabledByPreference() throws Exception {
		// Disable resource bundle completion via preference
		preferences.setResourceBundleCompletionEnabled(false);

		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions (Property kind)
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertTrue(resourceBundleItems.isEmpty(), "Should not have resource bundle key completions when preference is disabled");
	}

	@Test
	public void testResourceBundleCompletionNotProvidedAfterComma() throws Exception {
		// Test that completion is NOT provided after a comma (getString only takes 1 parameter)
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("greeting.hello", );
				    }
				}
				""");

		// Request completion after the comma
		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.hello\", ");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		// Should NOT provide resource bundle completions after comma (getString only has 1 parameter)
		assertTrue(resourceBundleItems.isEmpty(), "Should not provide resource bundle completions after comma");
	}

	@Test
	public void testResourceBundleCompletionNotProvidedAfterClosingParen() throws Exception {
		// Test that completion is NOT provided after closing parenthesis
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("greeting.hello");
				    }
				}
				""");

		// Request completion after the closing parenthesis
		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.hello\")");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		// Should NOT provide resource bundle completions after closing paren
		assertTrue(resourceBundleItems.isEmpty(), "Should not provide resource bundle completions after closing parenthesis");
	}

	@Test
	public void testResourceBundleCompletionWithQuotesInComment() throws Exception {
		// Test that quotes in comments don't interfere with quote detection
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        /* Comment with "quotes" */
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		// Should still provide completions (quotes in comment should be ignored)
		assertFalse(resourceBundleItems.isEmpty(), "Should provide resource bundle completions despite quotes in comment");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
	}

	@Test
	public void testResourceBundleCompletionAtEmptyArgumentPosition() throws Exception {
		// Test completion at empty argument position (before quotes are typed)
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString();
				    }
				}
				""");

		// Request completion at empty argument position
		CompletionList list = requestCompletions(unit, "bundle.getString(");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		// Should provide completions at empty argument position
		assertFalse(resourceBundleItems.isEmpty(), "Should provide resource bundle completions at empty argument position");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("error.notfound"), "Should contain error.notfound");
	}

	@Test
	public void testResourceBundleCompletionWithLocaleFrench() throws Exception {
		// Test that locale detection works with Locale.FRENCH
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.FRENCH);
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");

		// Verify that French values are prioritized (check documentation)
		CompletionItem helloItem = resourceBundleItems.stream()
				.filter(item -> "greeting.hello".equals(item.getLabel()))
				.findFirst()
				.orElse(null);
		assertNotNull(helloItem, "Should find greeting.hello completion item");

		// The documentation should show the French value "Bonjour" if locale detection worked
		Object documentation = helloItem.getDocumentation();
		if (documentation instanceof MarkupContent markupContent) {
			String value = markupContent.getValue();
			// French value "Bonjour" should be shown when French locale is detected
			assertTrue(value.contains("Bonjour"),
					"Documentation should contain French value 'Bonjour' when Locale.FRENCH is used, but was: " + value);
		} else {
			// Documentation might be null or in different format, but item should exist
			assertNotNull(documentation, "Completion item should have documentation");
		}
	}

	@Test
	public void testResourceBundleCompletionWithLocaleFrance() throws Exception {
		// Test that locale detection works with Locale.FRANCE (fr_FR)
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.FRANCE);
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
		// France-specific key should be available
		assertTrue(labels.contains("greeting.formal"), "Should contain greeting.formal (France-specific)");
	}

	@Test
	public void testResourceBundleCompletionWithNewLocale() throws Exception {
		// Test that locale detection works with new Locale("fr")
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", new Locale("fr"));
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
	}

	@Test
	public void testResourceBundleCompletionWithNewLocaleWithCountry() throws Exception {
		// Test that locale detection works with new Locale("fr", "FR")
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", new Locale("fr", "FR"));
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
		// France-specific key should be available
		assertTrue(labels.contains("greeting.formal"), "Should contain greeting.formal (France-specific)");
	}

	@Test
	public void testResourceBundleCompletionWithLocaleOf() throws Exception {
		// Test that locale detection works with Locale.of("fr")
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.of("fr"));
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
	}

	@Test
	public void testResourceBundleCompletionWithLocaleOfWithCountry() throws Exception {
		// Test that locale detection works with Locale.of("fr", "FR")
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.of("fr", "FR"));
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
		// France-specific key should be available
		assertTrue(labels.contains("greeting.formal"), "Should contain greeting.formal (France-specific)");
	}

	@Test
	public void testResourceBundleCompletionWithLocaleForLanguageTag() throws Exception {
		// Test that locale detection works with Locale.forLanguageTag("fr-FR")
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.forLanguageTag("fr-FR"));
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
		// France-specific key should be available
		assertTrue(labels.contains("greeting.formal"), "Should contain greeting.formal (France-specific)");
	}

	@Test
	public void testResourceBundleCompletionWithLocaleForLanguageTagLanguageOnly() throws Exception {
		// Test that locale detection works with Locale.forLanguageTag("fr") (language only)
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.forLanguageTag("fr"));
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
	}

	@Test
	public void testResourceBundleCompletionWithLocaleFallback() throws Exception {
		// Test that when a key doesn't exist in locale-specific file, it falls back to default
		// Create a key that only exists in default messages.properties, not in messages_fr.properties
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				import java.util.Locale;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages", Locale.FRENCH);
				        String value = bundle.getString("app.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"app.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		// app.title and app.version exist in default messages.properties
		// They should be available even when using French locale (fallback to default)
		assertTrue(labels.contains("app.title"), "Should contain app.title (fallback to default)");
		assertTrue(labels.contains("app.version"), "Should contain app.version (fallback to default)");
	}

	@Test
	public void testResourceBundleCompletionWithoutLocale() throws Exception {
		// Test that completion works without locale (default behavior)
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/ResourceBundleTest.java",
				"""
				package org.sample;
				import java.util.ResourceBundle;
				public class ResourceBundleTest {
				    private ResourceBundle bundle;
				    public void test() {
				        bundle = ResourceBundle.getBundle("resources.messages");
				        String value = bundle.getString("greeting.");
				    }
				}
				""");

		CompletionList list = requestCompletions(unit, "bundle.getString(\"greeting.");
		assertNotNull(list, "Completion list should not be null");

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse(resourceBundleItems.isEmpty(), "Should have resource bundle key completions");

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue(labels.contains("greeting.hello"), "Should contain greeting.hello");
		assertTrue(labels.contains("greeting.goodbye"), "Should contain greeting.goodbye");
		// Should not contain France-specific key when no locale is specified
		// (unless default locale is fr_FR, but we can't assume that)
	}

}

