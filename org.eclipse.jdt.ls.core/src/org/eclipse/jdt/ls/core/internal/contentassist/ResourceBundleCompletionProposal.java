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
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.ls.core.internal.CompletionUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Provides completion proposals for resource bundle keys.
 * Detects when completion is triggered inside ResourceBundle.getString() calls
 * and provides completions based on keys found in .properties files in the project.
 */
public class ResourceBundleCompletionProposal {

	private static final String RESOURCE_BUNDLE_CLASS = "java.util.ResourceBundle";
	private static final String GET_STRING_METHOD = "getString";

	/**
	 * Gets completion proposals for resource bundle keys.
	 *
	 * @param cu the compilation unit
	 * @param offset the offset where completion was triggered
	 * @param collector the completion proposal requestor
	 * @param monitor the progress monitor
	 * @return list of completion items for resource bundle keys
	 */
	public List<CompletionItem> getProposals(ICompilationUnit cu, int offset, CompletionProposalRequestor collector, IProgressMonitor monitor) {
		if (cu == null) {
			return Collections.emptyList();
		}

		List<CompletionItem> result = new ArrayList<>();
		try {
			// Check if we're in a resource bundle context
			String bundleName = detectResourceBundleContext(cu, offset, monitor);
			if (bundleName == null || bundleName.isEmpty()) {
				return result;
			}

			// Find all properties files and extract keys with their values
			Map<String, String> keyValueMap = findResourceBundleKeys(cu.getJavaProject(), bundleName, monitor);
			if (keyValueMap.isEmpty()) {
				return result;
			}

			// Create completion items for the keys
			IDocument document = JsonRpcHelpers.toDocument(cu.getBuffer());
			QuotePositions quotes = findQuotePositions(document, offset);
			boolean insideQuotes = quotes.openingQuote >= 0;
			String prefix = getPrefix(document, offset, quotes);
			Range range = calculateRange(document, offset, prefix, quotes);

			CompletionItemDefaults completionItemDefaults = collector.getCompletionItemDefaults();
			boolean useItemDefaults = shouldUseItemDefaults(range, completionItemDefaults);

			// Filter keys by prefix and create completion items
			// keyValueMap already contains deduplicated keys (from LinkedHashMap)
			for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (prefix.isEmpty() || key.toLowerCase().startsWith(prefix.toLowerCase())) {
					CompletionItem item = createCompletionItem(key, value, range, useItemDefaults, completionItemDefaults, insideQuotes);
					result.add(item);
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error providing resource bundle key completions", e);
		}

		return result;
	}

	/**
	 * Detects if we're in a resource bundle context (ResourceBundle.getString()).
	 * @return the bundle name if in a resource bundle context, null otherwise
	 */
	private String detectResourceBundleContext(ICompilationUnit cu, int offset, IProgressMonitor monitor) {
		try {
			CompilationUnit ast = SharedASTProviderCore.getAST(cu, SharedASTProviderCore.WAIT_ACTIVE_ONLY, monitor);
			if (ast == null) {
				return null;
			}

			// Try to find a node at the offset, expanding the search if needed
			ASTNode node = NodeFinder.perform(ast, offset, 0);
			if (node == null) {
				// Try with a small range around the offset
				node = NodeFinder.perform(ast, Math.max(0, offset - 1), 2);
			}

			// If the node itself is a StringLiteral, check its parent
			if (node instanceof StringLiteral) {
				StringLiteral stringLiteral = (StringLiteral) node;
				ASTNode parent = node.getParent();
				if (parent instanceof MethodInvocation) {
					MethodInvocation invocation = (MethodInvocation) parent;
					return checkMethodInvocation(invocation, stringLiteral, offset);
				}
			}

			// Find the enclosing method invocation
			MethodInvocation enclosingInvocation = findEnclosingMethodInvocation(node);
			if (enclosingInvocation == null) {
				return null;
			}

			// Check if any of the arguments is a StringLiteral containing the offset
			@SuppressWarnings("unchecked")
			List<Expression> arguments = enclosingInvocation.arguments();
			for (Expression arg : arguments) {
				if (arg instanceof StringLiteral stringLiteral) {
					if (isInsideStringLiteral(offset, stringLiteral)) {
						return checkMethodInvocation(enclosingInvocation, stringLiteral, offset);
					}
				}
			}

			// Check if we're at a position where a string literal argument is expected but not yet created
			// This handles cases like bundle.getString(|) where the quotes haven't been typed yet
			if (arguments.isEmpty() || isAtArgumentPosition(enclosingInvocation, offset, arguments)) {
				return checkMethodInvocation(enclosingInvocation, null, offset);
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error detecting resource bundle context", e);
		}

		return null;
	}

	/**
	 * Checks if the offset is at a position where a string argument would be expected.
	 * This handles the case when the cursor is at bundle.getString(|) before quotes are typed.
	 */
	private boolean isAtArgumentPosition(MethodInvocation invocation, int offset, List<Expression> arguments) {
		try {
			ASTNode root = invocation.getRoot();
			if (root instanceof CompilationUnit rootCU) {
				ICompilationUnit cu = (ICompilationUnit) rootCU.getJavaElement();
				if (cu != null) {
					String source = cu.getSource();
					if (source != null) {
						int invocationStart = invocation.getStartPosition();
						int invocationEnd = invocationStart + invocation.getLength();

						// Find the opening parenthesis position
						int openParenPos = findOpeningParenPosition(source, invocation, invocationStart);
						if (openParenPos < 0) {
							return false;
						}

						// Find the closing parenthesis position
						int closeParenPos = findClosingParenPosition(source, openParenPos, invocationEnd);
						if (closeParenPos < 0) {
							return false;
						}

						// Check if offset is within the argument area (between parentheses)
						if (offset >= openParenPos + 1 && offset <= closeParenPos) {
							// If there are arguments, check if we're after the last one
							if (!arguments.isEmpty()) {
								Expression lastArg = arguments.get(arguments.size() - 1);
								int lastArgEnd = lastArg.getStartPosition() + lastArg.getLength();
								// Allow some tolerance for whitespace and comma
								return offset >= lastArgEnd;
							}
							// No arguments, we're at the first argument position
							return true;
						}
					}
				}
			}
		} catch (Exception e) {
			// If we can't determine, fall back to false
		}
		return false;
	}

	/**
	 * Finds the position of the opening parenthesis in a method invocation using source code.
	 */
	private int findOpeningParenPosition(String source, MethodInvocation invocation, int invocationStart) {
		ASTNode nameNode = invocation.getName();
		if (nameNode != null) {
			int nameEnd = nameNode.getStartPosition() + nameNode.getLength();
			// Search for '(' after the method name
			for (int i = nameEnd; i < invocationStart + invocation.getLength() && i < source.length(); i++) {
				if (source.charAt(i) == '(') {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Finds the position of the closing parenthesis matching the opening one.
	 */
	private int findClosingParenPosition(String source, int openParenPos, int maxPos) {
		int depth = 1;
		for (int i = openParenPos + 1; i < maxPos && i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Checks if a method invocation is a resource bundle method and returns the bundle name.
	 * @param invocation the method invocation
	 * @param stringLiteral the string literal argument (may be null if not yet created)
	 * @param offset the completion offset
	 * @return the bundle name if this is a resource bundle method, null otherwise
	 */
	private String checkMethodInvocation(MethodInvocation invocation, StringLiteral stringLiteral, int offset) {
		IMethodBinding methodBinding = invocation.resolveMethodBinding();
		String methodName = null;

		if (methodBinding != null) {
			methodName = methodBinding.getName();
		}

		// Fallback: if binding doesn't resolve, try to get method name from AST
		if (methodName == null) {
			ASTNode astNameNode = invocation.getName();
			if (astNameNode instanceof org.eclipse.jdt.core.dom.SimpleName nameNode) {
				methodName = nameNode.getIdentifier();
			}
		}

		// Check if it's ResourceBundle.getString() or a subclass of ResourceBundle
		if (GET_STRING_METHOD.equals(methodName) && isResourceBundleSubclass(methodBinding)) {
			// Check if we're inside a string literal, or if stringLiteral is null (not yet created)
			if (stringLiteral == null || isInsideStringLiteral(offset, stringLiteral)) {
				// Try to find the bundle name from the receiver
				return extractBundleName(invocation.getExpression());
			}
		}

		return null;
	}

	/**
	 * Checks if the given type is ResourceBundle or a subclass of ResourceBundle.
	 */
	private boolean isResourceBundleSubclass(IMethodBinding methodBinding) {
		if (methodBinding == null) {
			return false;
		}
		ITypeBinding typeBinding = methodBinding.getDeclaringClass();
		if (typeBinding == null) {
			return false;
		}

		// Check if it's ResourceBundle itself
		if (RESOURCE_BUNDLE_CLASS.equals(typeBinding.getQualifiedName())) {
			return true;
		}

		// Walk up the superclass hierarchy to find ResourceBundle
		ITypeBinding current = typeBinding;
		while (current != null) {
			ITypeBinding superclass = current.getSuperclass();
			if (superclass != null) {
				if (RESOURCE_BUNDLE_CLASS.equals(superclass.getQualifiedName())) {
					return true;
				}
				current = superclass;
			} else {
				break;
			}
		}

		return false;
	}

	/**
	 * Finds the enclosing method invocation node.
	 */
	private MethodInvocation findEnclosingMethodInvocation(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof MethodInvocation) {
				return (MethodInvocation) current;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Checks if the offset is inside the given string literal.
	 */
	private boolean isInsideStringLiteral(int offset, StringLiteral stringLiteral) {
		int start = stringLiteral.getStartPosition();
		int end = start + stringLiteral.getLength();
		// The offset should be inside the string content (excluding quotes)
		// We allow the offset to be at the end (after the last quote) for completion
		return offset >= start + 1 && offset <= end;
	}

	/**
	 * Extracts the bundle name from the expression (e.g., ResourceBundle.getBundle("bundleName")).
	 * Also traces back variable assignments to find the bundle name.
	 */
	private String extractBundleName(Expression expression) {
		if (expression == null) {
			return null;
		}

		// Handle field access: extract the variable binding and trace it
		if (expression instanceof org.eclipse.jdt.core.dom.FieldAccess fieldAccess) {
			IBinding binding = fieldAccess.getName().resolveBinding();
			if (binding instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
				return findBundleNameFromVariableBinding(vb, expression.getRoot());
			}
		}

		// Use the unified extraction method for all other cases
		return extractBundleNameFromExpression(expression, expression.getRoot());
	}

	/**
	 * Extracts bundle name from an expression, handling both direct string literals
	 * and ResourceBundle.getBundle() calls, including tracing variable references.
	 */
	private String extractBundleNameFromExpression(Expression expression, ASTNode root) {
		if (expression == null) {
			return null;
		}

		// Direct string literal: var name = "messages";
		if (expression instanceof StringLiteral stringLiteral) {
			return stringLiteral.getLiteralValue();
		}

		// Method invocation: var bundle = ResourceBundle.getBundle("messages");
		if (expression instanceof MethodInvocation invocation) {
			IMethodBinding binding = invocation.resolveMethodBinding();
			if (binding != null && "getBundle".equals(binding.getName())) {
				@SuppressWarnings("unchecked")
				List<Expression> arguments = invocation.arguments();
				if (!arguments.isEmpty()) {
					Expression arg = arguments.get(0);
					// If argument is a string literal, return it directly
					if (arg instanceof StringLiteral stringLiteral) {
						return stringLiteral.getLiteralValue();
					}
					// If argument is a variable, trace it back recursively
					if (arg instanceof org.eclipse.jdt.core.dom.SimpleName name) {
						IBinding binding2 = name.resolveBinding();
						if (binding2 instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
							return findBundleNameFromVariableBinding(vb, root);
						}
					}
				}
			}
		}

		// Variable reference: trace back to find the actual value
		if (expression instanceof org.eclipse.jdt.core.dom.SimpleName name) {
			IBinding binding = name.resolveBinding();
			if (binding instanceof org.eclipse.jdt.core.dom.IVariableBinding vb) {
				return findBundleNameFromVariableBinding(vb, root);
			}
		}

		return null;
	}

	/**
	 * Finds the bundle name by tracing back to where a variable was assigned.
	 */
	private String findBundleNameFromVariableBinding(org.eclipse.jdt.core.dom.IVariableBinding varBinding, ASTNode root) {
		if (varBinding == null || root == null) {
			return null;
		}

		// Find the variable declaration or assignment in the AST
		VariableFinder finder = new VariableFinder(varBinding);
		root.accept(finder);

		// First check if there's an initializer in the declaration
		if (finder.declarationFragment != null) {
			Expression initializer = finder.declarationFragment.getInitializer();
			String bundleName = extractBundleNameFromExpression(initializer, root);
			if (bundleName != null) {
				return bundleName;
			}
		}

		// If no initializer, check for assignment statements
		if (finder.assignment != null) {
			Expression rightHandSide = finder.assignment.getRightHandSide();
			String bundleName = extractBundleNameFromExpression(rightHandSide, root);
			if (bundleName != null) {
				return bundleName;
			}
		}

		return null;
	}

	/**
	 * AST visitor to find a variable declaration fragment or assignment for a given variable binding.
	 */
	private static class VariableFinder extends ASTVisitor {
		private final org.eclipse.jdt.core.dom.IVariableBinding targetBinding;
		VariableDeclarationFragment declarationFragment;
		Assignment assignment;

		VariableFinder(org.eclipse.jdt.core.dom.IVariableBinding targetBinding) {
			this.targetBinding = targetBinding;
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			if (node.resolveBinding() == targetBinding) {
				declarationFragment = node;
				// Continue visiting to also check for assignments (in case initializer is null)
			}
			return true;
		}

		@Override
		public boolean visit(Assignment node) {
			Expression leftHandSide = node.getLeftHandSide();
			if (leftHandSide instanceof org.eclipse.jdt.core.dom.SimpleName) {
				org.eclipse.jdt.core.dom.SimpleName name = (org.eclipse.jdt.core.dom.SimpleName) leftHandSide;
				IBinding binding = name.resolveBinding();
				if (binding == targetBinding) {
					assignment = node;
					return false; // Stop visiting once we find the assignment
				}
			} else if (leftHandSide instanceof org.eclipse.jdt.core.dom.FieldAccess) {
				org.eclipse.jdt.core.dom.FieldAccess fieldAccess = (org.eclipse.jdt.core.dom.FieldAccess) leftHandSide;
				IBinding binding = fieldAccess.getName().resolveBinding();
				if (binding == targetBinding) {
					assignment = node;
					return false; // Stop visiting once we find the assignment
				}
			}
			return true;
		}
	}

	/**
	 * Finds all resource bundle keys from .properties files in the project.
	 * Respects classpath order and locale specificity.
	 * According to ResourceBundle.getBundle() behavior:
	 * - Files are searched in classpath order
	 * - More specific locale files override less specific ones
	 * - Later files in classpath override earlier ones for duplicate keys
	 */
	private Map<String, String> findResourceBundleKeys(IJavaProject javaProject, String bundleName, IProgressMonitor monitor) {
		Map<String, String> keyValueMap = new LinkedHashMap<>();
		if (bundleName == null || bundleName.isEmpty() || javaProject == null) {
			return keyValueMap;
		}

		try {
			IProject project = javaProject.getProject();
			if (project == null || !project.exists()) {
				return keyValueMap;
			}

			// Find all .properties files matching the bundle name, respecting classpath order
			// Files are returned in classpath order by findPropertiesFiles()
			// All files already match the bundle name pattern (e.g., "messages.properties", "messages_*.properties")
			List<IFile> propertiesFiles = findPropertiesFiles(javaProject, bundleName, monitor);

			// Extract base file name from bundle name for sorting
			String bundlePath = bundleName.replace('.', '/');
			int lastSlash = bundlePath.lastIndexOf('/');
			String baseFileName = lastSlash >= 0 ? bundlePath.substring(lastSlash + 1) : bundlePath;
			String basePattern = baseFileName + ".properties";

			// Sort files by locale specificity (more specific locales first)
			// This matches ResourceBundle.getBundle() search order behavior
			propertiesFiles.sort((f1, f2) -> compareLocaleSpecificity(f1.getName(), f2.getName(), basePattern));

			// Process files in order: more specific locales override less specific ones
			// This matches ResourceBundle fallback behavior
			for (IFile file : propertiesFiles) {
				Map<String, String> fileKeys = extractKeysFromPropertiesFile(file, monitor);
				// Put all keys, allowing more specific locales to override less specific ones
				for (Map.Entry<String, String> entry : fileKeys.entrySet()) {
					keyValueMap.put(entry.getKey(), entry.getValue());
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error finding resource bundle keys", e);
		}

		return keyValueMap;
	}

	/**
	 * Compares two file names by locale specificity.
	 * More specific locales (more underscores) come first.
	 * If basePattern is provided and matches one of the names, that file comes last.
	 *
	 * @param name1 first file name
	 * @param name2 second file name
	 * @param basePattern optional base pattern (e.g., "messages.properties"), null if not applicable
	 * @return negative if name1 should come before name2, positive if after, 0 if equal
	 */
	private static int compareLocaleSpecificity(String name1, String name2, String basePattern) {
		// Base file always comes last if basePattern is provided
		if (basePattern != null) {
			if (name1.equals(basePattern)) {
				return 1;
			}
			if (name2.equals(basePattern)) {
				return -1;
			}
		}
		// Count underscores (more = more specific locale)
		long underscores1 = name1.chars().filter(c -> c == '_').count();
		long underscores2 = name2.chars().filter(c -> c == '_').count();
		// More specific locales come first (reverse order)
		return Long.compare(underscores2, underscores1);
	}

	/**
	 * Finds all .properties files matching the bundle name, respecting classpath order.
	 * Builds the expected path from the bundle name and checks for matching files.
	 * According to ResourceBundle.getBundle() behavior, files are searched in classpath order:
	 * 1. Output folders (if on classpath)
	 * 2. Source folders (in classpath order)
	 */
	private List<IFile> findPropertiesFiles(IJavaProject javaProject, String bundleName, IProgressMonitor monitor) {
		// If bundle name is not specified, don't search for files
		if (bundleName == null || bundleName.isEmpty()) {
			return new ArrayList<>();
		}

		List<IFile> result = new ArrayList<>();

		try {
			IProject project = javaProject.getProject();
			if (project == null || !project.exists()) {
				return result;
			}

			// Convert bundle name to path: "com.example.messages" -> "com/example/messages"
			String bundlePath = bundleName.replace('.', '/');
			String baseFileName;
			String packagePath;
			int lastSlash = bundlePath.lastIndexOf('/');
			if (lastSlash >= 0) {
				baseFileName = bundlePath.substring(lastSlash + 1);
				packagePath = bundlePath.substring(0, lastSlash + 1);
			} else {
				// Simple bundle name without package: "messages"
				baseFileName = bundlePath;
				packagePath = "";
			}

			// Pattern to match: baseFileName.properties or baseFileName_*.properties
			String basePattern = baseFileName + ".properties";
			String localePatternPrefix = baseFileName + "_";

			// Get output location
			IPath outputPath = javaProject.getOutputLocation();
			if (outputPath != null) {
				IPath relativeOutputPath = outputPath.makeRelativeTo(project.getFullPath());
				IPath bundleDirPath = relativeOutputPath.append(packagePath);
				IContainer bundleDir = project.getFolder(bundleDirPath);
				if (bundleDir.exists()) {
					findMatchingPropertiesFiles(bundleDir, basePattern, localePatternPrefix, result);
				}
			}

			// Then check source folders in classpath order
			IClasspathEntry[] classpath = javaProject.getResolvedClasspath(true);
			for (IClasspathEntry entry : classpath) {
				if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					IPath sourcePath = entry.getPath().makeRelativeTo(project.getFullPath());
					IPath bundleDirPath = sourcePath.append(packagePath);
					IContainer bundleDir = project.getFolder(bundleDirPath);
					if (bundleDir.exists()) {
						findMatchingPropertiesFiles(bundleDir, basePattern, localePatternPrefix, result);
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Error finding properties files", e);
		}

		return result;
	}

	/**
	 * Finds all .properties files in the given directory that match the bundle name pattern.
	 * Matches both the base file (e.g., "messages.properties") and locale variants (e.g., "messages_en.properties").
	 * Files are sorted by locale specificity (more underscores = more specific).
	 */
	private void findMatchingPropertiesFiles(IContainer directory, String basePattern, String localePatternPrefix, List<IFile> result) {
		try {
			IResource[] members = directory.members();
			List<IFile> matchingFiles = new ArrayList<>();

			for (IResource resource : members) {
				if (resource.getType() == IResource.FILE && resource instanceof IFile file) {
					String fileName = file.getName();

					// Match base file or locale variants (baseFileName_*.properties)
					if (fileName.equals(basePattern) ||
						(fileName.startsWith(localePatternPrefix) && fileName.endsWith(".properties"))) {
						matchingFiles.add(file);
					}
				}
			}

			// Sort by locale specificity: more underscores = more specific locale
			// More specific locales come first, base file comes last
			matchingFiles.sort((f1, f2) -> compareLocaleSpecificity(f1.getName(), f2.getName(), basePattern));

			result.addAll(matchingFiles);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Error finding matching properties files", e);
		}
	}


	/**
	 * Extracts keys and their values from a properties file.
	 */
	private Map<String, String> extractKeysFromPropertiesFile(IFile file, IProgressMonitor monitor) {
		Map<String, String> keyValueMap = new LinkedHashMap<>();
		if (file == null || !file.exists()) {
			return keyValueMap;
		}

		try (InputStream inputStream = file.getContents()) {
			Properties properties = new Properties();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
				properties.load(reader);
			}
			// Extract both keys and values
			for (String key : properties.stringPropertyNames()) {
				String value = properties.getProperty(key);
				if (value != null) {
					keyValueMap.put(key, value);
				}
			}
		} catch (IOException | CoreException e) {
			JavaLanguageServerPlugin.logException("Error reading properties file: " + file.getFullPath(), e);
		}

		return keyValueMap;
	}

	/**
	 * Gets the prefix string at the current offset.
	 * This extracts the partial key that the user has typed so far.
	 * Handles both cases: inside quotes (bundle.getString("key|")) and outside quotes (bundle.getString(key|)).
	 */
	private String getPrefix(IDocument document, int offset, QuotePositions quotes) {
		try {
			if (offset < 0 || offset > document.getLength()) {
				return "";
			}
			boolean insideQuotes = quotes.openingQuote >= 0;

			int start = offset;
			// Find the start of the current word (backwards from offset)
			// Stop at the opening quote, opening parenthesis, comma, or whitespace
			while (start > 0) {
				char c = document.getChar(start - 1);
				if (c == '"' || c == '(' || c == ',' || Character.isWhitespace(c)) {
					break;
				}
				if (!isKeyChar(c)) {
					break;
				}
				start--;
			}

			// Find the end of the current word (forwards from offset)
			// When inside quotes, only look up to the cursor position (don't include text after cursor)
			// When outside quotes, stop at closing parenthesis, comma, or non-key character
			int end = offset;
			if (insideQuotes) {
				// When inside quotes, only extract prefix up to the cursor position
				// Don't include text that comes after the cursor
				end = offset;
			} else {
				// When outside quotes, stop at closing parenthesis, comma, or non-key character
				while (end < document.getLength()) {
					char c = document.getChar(end);
					if (c == ')' || c == ',' || Character.isWhitespace(c)) {
						break;
					}
					if (!isKeyChar(c)) {
						break;
					}
					end++;
				}
			}

			if (start < end) {
				return document.get(start, end - start);
			}
			return "";
		} catch (BadLocationException e) {
			return "";
		}
	}

	/**
	 * Result of finding quote positions in a string literal.
	 */
	private static record QuotePositions(int openingQuote, int closingQuote) {
		boolean isValid() {
			return openingQuote >= 0 && closingQuote > openingQuote;
		}
	}

	/**
	 * Finds the positions of opening and closing quotes around the given offset.
	 * @return QuotePositions with the quote positions, or invalid positions if not found
	 */
	private QuotePositions findQuotePositions(IDocument document, int offset) {
		try {
			if (offset < 0 || offset > document.getLength()) {
				return new QuotePositions(-1, -1);
			}

			// Find the opening quote (backwards from offset)
			int openingQuote = -1;
			for (int i = offset - 1; i >= 0; i--) {
				char c = document.getChar(i);
				if (c == '"') {
					openingQuote = i;
					break;
				}
				if (c == '(' || c == ',' || Character.isWhitespace(c)) {
					break;
				}
			}

			// Find the closing quote (forwards from offset)
			int closingQuote = -1;
			for (int i = offset; i < document.getLength(); i++) {
				char c = document.getChar(i);
				if (c == '"') {
					closingQuote = i;
					break;
				}
			}

			return new QuotePositions(openingQuote, closingQuote);
		} catch (BadLocationException e) {
			return new QuotePositions(-1, -1);
		}
	}


	/**
	 * Calculates the range to replace based on the prefix.
	 * When inside quotes, replaces the entire string content (from opening quote to closing quote).
	 * @param quotes the quote positions (can be invalid if not inside quotes)
	 */
	private Range calculateRange(IDocument document, int offset, String prefix, QuotePositions quotes) {
		try {
			if (quotes.isValid()) {
				// When inside quotes, replace the entire string content
				// Replace everything between the quotes (excluding the quotes themselves)
				int start = quotes.openingQuote + 1;
				int length = quotes.closingQuote - start;
				return JDTUtils.toRange(document, start, length);
			}

			// Fallback: replace just the prefix
			if (prefix.isEmpty()) {
				// If no prefix, just insert at the current position
				return JDTUtils.toRange(document, offset, 0);
			}
			// Calculate the start position of the prefix
			int start = offset - prefix.length();
			int length = prefix.length();
			return JDTUtils.toRange(document, start, length);
		} catch (Exception e) {
			// Fallback to simple range
			try {
				return JDTUtils.toRange(document, offset, 0);
			} catch (Exception e2) {
				// Last resort: create a range at the offset
				try {
					int[] loc = org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers.toLine(document, offset);
					org.eclipse.lsp4j.Position pos = new org.eclipse.lsp4j.Position(loc[0], loc[1]);
					return new Range(pos, pos);
				} catch (Exception e3) {
					org.eclipse.lsp4j.Position pos = new org.eclipse.lsp4j.Position(0, 0);
					return new Range(pos, pos);
				}
			}
		}
	}

	/**
	 * Checks if a character is valid in a resource bundle key.
	 */
	private boolean isKeyChar(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-';
	}

	/**
	 * Determines whether to use completion item defaults for the edit range.
	 * Uses item defaults if the client supports it and the calculated range matches
	 * the default edit range from the completion item defaults.
	 *
	 * @param range the calculated range for the completion
	 * @param completionItemDefaults the completion item defaults from the collector
	 * @return true if item defaults should be used, false otherwise
	 */
	private boolean shouldUseItemDefaults(Range range, CompletionItemDefaults completionItemDefaults) {
		return JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences()
				.isCompletionListItemDefaultsPropertySupport("editRange")
				&& completionItemDefaults != null
				&& completionItemDefaults.getEditRange() != null
				&& completionItemDefaults.getEditRange().getLeft() != null
				&& range.equals(completionItemDefaults.getEditRange().getLeft());
	}

	/**
	 * Creates a completion item for a resource bundle key.
	 * @param insideQuotes true if we're inside quotes (insert just the key), false if outside quotes (insert "key")
	 */
	private CompletionItem createCompletionItem(String key, String value, Range range, boolean useItemDefaults, CompletionItemDefaults completionItemDefaults, boolean insideQuotes) {
		CompletionItem item = new CompletionItem();
		item.setLabel(key);
		item.setKind(CompletionItemKind.Property);
		// Use very high relevance to get lowest sort text (highest priority)
		// Regular completions use relevance * 16 + offsets (typically < 1,000,000)
		// Using a value close to MAX_RELEVANCE_VALUE ensures resource bundle keys appear first
		item.setSortText(SortTextHelper.convertRelevance(SortTextHelper.MAX_RELEVANCE_VALUE - 1000));
		item.setFilterText(key);

		// If we're not inside quotes, wrap the key in quotes
		String insertText = insideQuotes ? key : "\"" + key + "\"";

		if (useItemDefaults && completionItemDefaults != null) {
			item.setTextEditText(insertText);
		} else {
			item.setTextEdit(Either.forLeft(new TextEdit(range, insertText)));
		}

		CompletionUtils.setInsertTextFormat(item, completionItemDefaults);
		CompletionUtils.setInsertTextMode(item, completionItemDefaults);

		// Set the property value as documentation
		if (value != null) {
			// Format multiline values for markdown: replace "\n" with "  \n"
			String formattedValue = value.replace("\n", "  \n");
			MarkupContent documentation = new MarkupContent(MarkupKind.MARKDOWN, formattedValue);
			item.setDocumentation(documentation);
			documentation.setValue(formattedValue);
		}

		return item;
	}

}
