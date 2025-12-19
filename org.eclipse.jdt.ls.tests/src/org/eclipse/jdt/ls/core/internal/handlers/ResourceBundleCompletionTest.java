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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
import org.eclipse.lsp4j.Range;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Integration tests for resource bundle key completion.
 */
@RunWith(MockitoJUnitRunner.class)
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

	@Before
	@Override
	public void setup() throws Exception {
		mockLSP3Client();
		reset();
		setupEclipseProject("resourcebundle");
	}

	@After
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
		assertNotNull("Completion list should not be null", list);

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse("Should have filtered resource bundle key completions", resourceBundleItems.isEmpty());

		// All items should start with "greeting."
		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue("Should contain greeting.hello", labels.contains("greeting.hello"));
		assertTrue("Should contain greeting.goodbye", labels.contains("greeting.goodbye"));
		// Should not contain keys that don't start with "greeting."
		assertFalse("Should not contain error.notfound", labels.contains("error.notfound"));
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
		assertNotNull("Completion list should not be null", list);

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse("Should have resource bundle key completions when cursor is in middle of string", resourceBundleItems.isEmpty());

		// Should show both greeting.hello and greeting.goodbye
		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue("Should contain greeting.hello", labels.contains("greeting.hello"));
		assertTrue("Should contain greeting.goodbye", labels.contains("greeting.goodbye"));

		// Verify that the text edit range replaces the entire string content
		CompletionItem helloItem = resourceBundleItems.stream()
				.filter(item -> "greeting.hello".equals(item.getLabel()))
				.findFirst()
				.orElse(null);
		assertNotNull("Should find greeting.hello completion item", helloItem);
		assertNotNull("Should have text edit", helloItem.getTextEdit());
		Range range = helloItem.getTextEdit().getLeft().getRange();
		// The range should replace the entire content between quotes
		// Verify that the range covers the entire "greeting.goodbye" string (not just "greeting.")
		// The range should start after the opening quote and end before the closing quote
		assertTrue("Text edit should replace entire string content",
				range.getStart().getCharacter() > 0 && range.getEnd().getCharacter() > range.getStart().getCharacter());
		// Verify the insert text is just the key (without quotes, since we're inside quotes)
		String insertText = helloItem.getTextEdit().getLeft().getNewText();
		assertEquals("Insert text should be the key without quotes", "greeting.hello", insertText);
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
		assertNotNull("Completion list should not be null", list);

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse("Should have resource bundle key completions", resourceBundleItems.isEmpty());

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		// Should contain validation keys
		assertTrue("Should contain validation.required", labels.contains("validation.required"));
		assertTrue("Should contain validation.email", labels.contains("validation.email"));

		// Should NOT contain keys from messages bundle
		assertFalse("Should not contain greeting.hello from messages bundle", labels.contains("greeting.hello"));
		assertFalse("Should not contain error.notfound from messages bundle", labels.contains("error.notfound"));
		assertFalse("Should not contain user.name from messages bundle", labels.contains("user.name"));
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
		assertNotNull("Completion list should not be null", list);

		// Filter for resource bundle key completions
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse("Should have resource bundle key completions", resourceBundleItems.isEmpty());

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		// Should contain messages keys
		assertTrue("Should contain greeting.hello", labels.contains("greeting.hello"));
		assertTrue("Should contain error.notfound", labels.contains("error.notfound"));
		assertTrue("Should contain user.name", labels.contains("user.name"));

		// Should NOT contain keys from validation bundle
		assertFalse("Should not contain validation.required from validation bundle", labels.contains("validation.required"));
		assertFalse("Should not contain validation.email from validation bundle", labels.contains("validation.email"));
		assertFalse("Should not contain validation.phone from validation bundle", labels.contains("validation.phone"));
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
		assertNotEquals("Completion list should not be empty", 0, list.getItems().size());

		// Find a resource bundle completion item
		CompletionItem item = list.getItems().stream()
				.filter(i -> i.getKind() == CompletionItemKind.Property && "greeting.hello".equals(i.getLabel()))
				.findFirst()
				.orElse(null);

		assertNotNull("Should find greeting.hello completion item", item);
		assertNotNull("Should have documentation", item.getDocumentation());
		// Documentation should contain the property value
		String documentation = item.getDocumentation().getRight().getValue();
		assertTrue("Documentation should contain the property value 'Hello'",
				documentation.contains("Hello"));
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
		assertNotEquals("Completion list should not be empty", 0, list.getItems().size());

		// Find the multiline completion item
		CompletionItem item = list.getItems().stream()
				.filter(i -> i.getKind() == CompletionItemKind.Property && "message.multiline".equals(i.getLabel()))
				.findFirst()
				.orElse(null);

		assertNotNull("Should find message.multiline completion item", item);
		assertNotNull("Should have documentation", item.getDocumentation());
		// Documentation should contain the multiline property value with markdown formatting
		String documentation = item.getDocumentation().getRight().getValue();
		assertTrue("Documentation should contain the multiline property value",
				documentation.contains("This is a multiline message"));
		assertTrue("Documentation should contain markdown-formatted newlines (double newlines)",
				documentation.contains("  \n"));
		// Verify that single \n has been replaced with \n\n
		assertFalse("Documentation should not contain single newlines (should be prefixed by 2 spaces)",
				documentation.contains("message.\nIt") || documentation.contains("lines.\nEach"));
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
		assertNotNull("Completion list should not be null", list);

		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertFalse("Should have resource bundle key completions", resourceBundleItems.isEmpty());

		List<String> labels = resourceBundleItems.stream()
				.map(CompletionItem::getLabel)
				.collect(Collectors.toList());

		assertTrue("Should contain greeting.hello", labels.contains("greeting.hello"));
		assertTrue("Should contain greeting.goodbye", labels.contains("greeting.goodbye"));
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
		assertNotNull("Completion list should not be null", list);

		// Filter for resource bundle key completions (Property kind)
		List<CompletionItem> resourceBundleItems = list.getItems().stream()
				.filter(item -> item.getKind() == CompletionItemKind.Property)
				.collect(Collectors.toList());

		assertTrue("Should not have resource bundle key completions when preference is disabled", resourceBundleItems.isEmpty());
	}

}

