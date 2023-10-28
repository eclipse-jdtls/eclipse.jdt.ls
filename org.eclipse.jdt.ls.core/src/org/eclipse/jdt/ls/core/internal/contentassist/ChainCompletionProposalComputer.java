/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.text.Chain;
import org.eclipse.jdt.internal.ui.text.ChainElement;
import org.eclipse.jdt.internal.ui.text.ChainElement.ElementType;
import org.eclipse.jdt.internal.ui.text.ChainElementAnalyzer;
import org.eclipse.jdt.internal.ui.text.ChainFinder;
import org.eclipse.jdt.internal.ui.text.ChainType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.TextEdit;

public class ChainCompletionProposalComputer {

	private static final char[] KEYWORD_NEW = "new".toCharArray();

	private List<ChainElement> entrypoints;

	private String[] excludedTypes;

	private ICompilationUnit cu;

	private CompletionProposalRequestor coll;

	private Map<String, List<TextEdit>> additionalEdits = new HashMap<>();

	private boolean snippetStringSupported;

	public ChainCompletionProposalComputer(ICompilationUnit cu, CompletionProposalRequestor coll, boolean snippetStringSupported) {
		this.cu = cu;
		this.coll = coll;
		this.snippetStringSupported = snippetStringSupported;
	}

	public void computeCompletionProposals() {
		if (shouldPerformCompletionOnExpectedType()) {
			executeCallChainSearch();
		}
	}

	private void executeCallChainSearch() {
		final int maxChains = Integer.parseInt(JavaManipulation.getPreference("recommenders.chain.max_chains", cu.getJavaProject()));
		final int minDepth = Integer.parseInt(JavaManipulation.getPreference("recommenders.chain.min_chain_length", cu.getJavaProject()));
		final int maxDepth = Integer.parseInt(JavaManipulation.getPreference("recommenders.chain.max_chain_length", cu.getJavaProject()));

		excludedTypes = JavaManipulation.getPreference("recommenders.chain.ignore_types", cu.getJavaProject()).split("\\|");
		for (int i = 0; i < excludedTypes.length; ++i) {
			excludedTypes[i] = "L" + excludedTypes[i].replace('.', '/');
		}

		final IType invocationType = cu.findPrimaryType();

		final List<ChainType> expectedTypes = resolveBindingsForExpectedTypes(cu.getJavaProject(), coll.getContext());
		final ChainFinder mainFinder = new ChainFinder(expectedTypes, Arrays.asList(excludedTypes), invocationType);
		final ChainFinder contextFinder = new ChainFinder(expectedTypes, Arrays.asList(excludedTypes), invocationType);
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			CompletableFuture<Void> mainChains = CompletableFuture.runAsync(() -> {
				if (findEntrypoints(expectedTypes, cu.getJavaProject())) {
					mainFinder.startChainSearch(entrypoints, maxChains, minDepth, maxDepth);
				}
			}, executor);

			CompletableFuture<Void> contextChains = CompletableFuture.runAsync(() -> {
				try {
					List<ChainElement> contextEntrypoint = computeContextEntrypoint(expectedTypes, cu.getJavaProject());
					if (!contextEntrypoint.isEmpty()) {
						contextFinder.startChainSearch(contextEntrypoint, maxChains, 1, 2);
					}
				} catch (JavaModelException e) {
					// ignore
				}
			}, executor);
			CompletableFuture<?> future = CompletableFuture.anyOf(mainChains, contextChains);

			long timeout = Long.parseLong(JavaManipulation.getPreference("recommenders.chain.timeout", cu.getJavaProject()));
			future.get(timeout, TimeUnit.SECONDS);
		} catch (final Exception e) {
			mainFinder.cancel();
			contextFinder.cancel();
			executor.shutdownNow();
		}
		List<Chain> found = new ArrayList<>();
		found.addAll(mainFinder.getChains());
		found.addAll(contextFinder.getChains());
		buildCompletionProposals(found);
	}

	private void buildCompletionProposals(final List<Chain> chains) {
		for (final Chain chain : chains) {
			try {
				var completionProposal = createCompletionProposal(chain);
				if (completionProposal != null) {
					coll.addAdditionalProposal(completionProposal);
				}
			} catch (JavaModelException e) {
				// ignore
			}
		}
	}

	private boolean findEntrypoints(List<ChainType> expectedTypes, IJavaProject project) {
		entrypoints = new LinkedList<>();
		Set<IJavaElement> processed = new HashSet<>();

		for (CompletionProposal prop : coll.getProposals()) {
			IJavaElement javaElement = resolveJavaElement(prop, cu.getJavaProject());
			if (javaElement != null) {
				IJavaElement e = javaElement;
				if (matchesExpectedPrefix(e) && !ChainFinder.isFromExcludedType(Arrays.asList(excludedTypes), e)) {
					ChainElement ce = new ChainElement(e, false);
					if (ce.getElementType() != null) {
						entrypoints.add(ce);
						processed.add(javaElement);
					}
				}
			}
		}
		IJavaElement[] visibleElements = coll.getContext().getVisibleElements(null);
		for (IJavaElement ve : visibleElements) {
			if (!processed.contains(ve) && matchesExpectedPrefix(ve) && !ChainFinder.isFromExcludedType(Arrays.asList(excludedTypes), ve)) {
				ChainElement ce = new ChainElement(ve, false);
				if (ce.getElementType() != null) {
					entrypoints.add(ce);
				}
			}
		}
		return !entrypoints.isEmpty();
	}

	private IJavaElement resolveJavaElement(CompletionProposal prop, IJavaProject proj) {
		try {
			if (prop.getKind() == CompletionProposal.FIELD_REF) {
				return JDTUtils.resolveField(prop, proj);
			} else if (prop.getKind() == CompletionProposal.METHOD_REF || prop.getKind() == CompletionProposal.ANNOTATION_ATTRIBUTE_REF) {
				return JDTUtils.resolveMethod(prop, proj);
			}
		} catch (JavaModelException e) {
			// continue
		}
		return null;
	}

	private boolean shouldPerformCompletionOnExpectedType() {
		if (coll.getContext().getToken() != null && CharOperation.equals(coll.getContext().getToken(), KEYWORD_NEW)) {
			return false;
		}

		if (coll.getContext().getTokenLocation() == CompletionContext.TL_CONSTRUCTOR_START) {
			return false;
		}

		CompilationUnit cuNode = getASTRoot();
		AST ast = cuNode.getAST();
		ITypeBinding binding = ast.resolveWellKnownType(ChainElementAnalyzer.getExpectedFullyQualifiedTypeName(coll.getContext()));
		IType type = ChainElementAnalyzer.getExpectedType(cu.getJavaProject(), coll.getContext());
		return hasValidExpectedTypeResolution(binding, type);
	}

	private boolean hasValidExpectedTypeResolution(ITypeBinding binding, IType type) {
		if (binding != null) {
			return !isPrimitiveOrBoxedPrimitive(binding) && !"java.lang.String".equals(binding.getQualifiedName()) && !"java.lang.Object".equals(binding.getQualifiedName());
		} else if (type != null) {
			return !"java.lang.String".equals(type.getFullyQualifiedName()) && !"java.lang.Object".equals(type.getFullyQualifiedName());
		} else {
			return false;
		}
	}

	private boolean isPrimitiveOrBoxedPrimitive(ITypeBinding binding) {
		if (binding.isPrimitive()) {
			return true;
		}

		return switch (binding.getQualifiedName()) {
			case "java.lang.Boolean" -> true;
			case "java.lang.Byte" -> true;
			case "java.lang.Character" -> true;
			case "java.lang.Short" -> true;
			case "java.lang.Double" -> true;
			case "java.lang.Float" -> true;
			case "java.lang.Integer" -> true;
			case "java.lang.Long" -> true;
			default -> false;
		};
	}

	private CompilationUnit getASTRoot() {
		CompilationUnit cuNode = SharedASTProviderCore.getAST(cu, SharedASTProviderCore.WAIT_NO, null);
		if (cuNode == null) {
			ASTParser p = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
			p.setSource(cu);
			p.setResolveBindings(true);
			p.setCompilerOptions(RefactoringASTParser.getCompilerOptions(cu));
			cuNode = (CompilationUnit) p.createAST(null);
		}
		return cuNode;
	}

	private boolean matchesExpectedPrefix(final IJavaElement element) {
		String prefix = String.valueOf(coll.getContext().getToken());
		return String.valueOf(element.getElementName()).startsWith(prefix);
	}

	// given Collection.emptyList()	return Collection.emptyList
	// args[i].lines().toList()	return args[i].lines().toList
	private char[] getQualifiedMethodName(String name) {
		var nonSnippetName = removeSnippetString(name);
		int index = nonSnippetName.lastIndexOf('(');
		if (index > 0) {
			return nonSnippetName.substring(0, index).toCharArray();
		}
		return nonSnippetName.toCharArray();
	}

	private String removeSnippetString(String name) {
		return name.replaceAll("\\[\\$\\{.*\\}\\]", "[i]");
	}

	private CompletionProposal createCompletionProposal(final Chain chain) throws JavaModelException {
		final var edge = chain.getElements().get(chain.getElements().size() - 1);
		final var insertText = createInsertText(chain, chain.getExpectedDimensions());
		final var root = chain.getElements().get(0);
		CompletionProposal cp = null;

		switch (edge.getElementType()) {
			case FIELD: {
				cp = CompletionProposal.create(CompletionProposal.FIELD_REF, coll.getContext().getOffset());
				var field = ((IField) edge.getElement());
				cp.setName(getQualifiedMethodName(insertText));
				cp.setSignature(field.getTypeSignature().toCharArray());
				cp.setDeclarationSignature(Signature.createTypeSignature(field.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
				cp.setCompletion(insertText.toCharArray());
				cp.setReplaceRange(coll.getContext().getOffset(), coll.getContext().getOffset() + insertText.length());
				if(coll.getContext().getToken() != null && coll.getContext().getToken().length > 0) {
					cp.setTokenRange(coll.getContext().getTokenStart(), coll.getContext().getTokenEnd());
				}
				break;
			}
			case METHOD: {
				cp = CompletionProposal.create(CompletionProposal.METHOD_REF, coll.getContext().getOffset());
				var method = ((IMethod) edge.getElement());
				cp.setName(getQualifiedMethodName(insertText));
				cp.setSignature(toGenericSignature(method));
				cp.setDeclarationSignature(Signature.createTypeSignature(method.getDeclaringType().getFullyQualifiedName(), true).toCharArray());
				cp.setCompletion(insertText.toCharArray());
				cp.setReplaceRange(coll.getContext().getOffset(), coll.getContext().getOffset() + insertText.length());
				cp.setParameterNames(toCharArray(method.getParameterNames()));
				if(coll.getContext().getToken() != null && coll.getContext().getToken().length > 0) {
					cp.setTokenRange(coll.getContext().getTokenStart(), coll.getContext().getTokenEnd());
				}
				break;
			}
			default:
		}

		if (cp != null) {
			// set replace rage if applicable
			if (coll.getContext().getToken() != null && coll.getContext().getToken().length > 0) {
				cp.setReplaceRange(coll.getContext().getTokenStart(), coll.getContext().getTokenEnd());
			}
			if (root.getElementType() == ElementType.TYPE) {
				var type = ((IType) root.getElement());
				var importCompletion = CompletionProposal.create(CompletionProposal.TYPE_REF, coll.getContext().getOffset());
				importCompletion.setSignature(Signature.createTypeSignature(type.getFullyQualifiedName(), true).toCharArray());
				importCompletion.setCompletion(type.getFullyQualifiedName().toCharArray());
				var sourceStart = cu.getTypes()[0].getSourceRange().getOffset();
				importCompletion.setReplaceRange(sourceStart, sourceStart);

				cp.setRequiredProposals(new CompletionProposal[] { importCompletion });
			}
		}
		return cp;
	}

	private char[] toGenericSignature(IMethod method) throws JavaModelException {
		return Signature.createMethodSignature(method.getParameterTypes(), method.getReturnType()).toCharArray();
	}
	private char[][] toCharArray(String[] parameterNames) {
		var result = new char[parameterNames.length][];
		for (int i = 0; i < parameterNames.length; i++) {
			result[i] = parameterNames[i].toCharArray();
		}
		return result;
	}

	private String createInsertText(final Chain chain, final int expectedDimension) throws JavaModelException {
		final AtomicInteger counter = new AtomicInteger(1);
		StringBuilder sb = new StringBuilder(64);
		for (final ChainElement edge : chain.getElements()) {
			switch (edge.getElementType()) {
				case FIELD:
				case TYPE:
				case LOCAL_VARIABLE:
					appendVariableString(edge, sb);
					break;
				case METHOD:
					final IMethod method = (IMethod) edge.getElement();
					sb.append(method.getElementName());
					appendParameters(sb, method, counter);
					break;
				default:
			}
			appendArrayDimensions(sb, edge.getReturnTypeDimension(), expectedDimension, snippetStringSupported, counter);
			sb.append(".");
		}
		deleteLastChar(sb);
		return sb.toString();
	}

	private static void appendVariableString(final ChainElement edge, final StringBuilder sb) {
		if (edge.requiresThisForQualification() && sb.length() == 0) {
			sb.append("this.");
		}
		sb.append((edge.getElement()).getElementName());
	}

	private void appendParameters(final StringBuilder sb, final IMethod method, final AtomicInteger counter) throws JavaModelException {
		sb.append("(");
		if (snippetStringSupported) {
			String[] parameterNames = method.getParameterNames();
			if (parameterNames == null) {
				parameterNames = Stream.of(method.getParameterTypes()).map(ts -> Signature.getSignatureSimpleName(Signature.getElementType(ts))).map(n -> n.substring(0, 1).toLowerCase() + n.substring(1)).map(n -> {
					int index = n.indexOf('<');
					return (index > -1) ? n.substring(0, index) : n;
				}).toArray(String[]::new);
			}
			sb.append(Stream.of(parameterNames).collect(Collectors.joining(", ")));
		}
		sb.append(")");
	}

	private void appendArrayDimensions(final StringBuilder sb, final int dimension, final int expectedDimension, final boolean appendVariables, final AtomicInteger counter) {
		for (int i = dimension; i-- > expectedDimension;) {
			sb.append("[");
			if (appendVariables) {
				sb.append("${").append(dimension).append(":i}");
			}
			sb.append("]");
		}
	}

	private static StringBuilder deleteLastChar(final StringBuilder sb) {
		return sb.deleteCharAt(sb.length() - 1);
	}

	private List<ChainElement> computeContextEntrypoint(List<ChainType> expectedTypes, IJavaProject project) throws JavaModelException {
		final List<ChainElement> results = new ArrayList<>();
		for (ChainType chainType : expectedTypes) {
			if (chainType.getType() == null) {
				continue;
			}

			if ("java.util.List".equals(chainType.getType().getFullyQualifiedName()) || "java.util.Set".equals(chainType.getType().getFullyQualifiedName()) || "java.util.Map".equals(chainType.getType().getFullyQualifiedName())) {
				IType type = project.findType("java.util.Collections");
				if (type != null) {
					results.add(new ChainElement(type, false));
				}
			}

			if (JavaModelUtil.is1d8OrHigher(project)) {
				if ("java.util.stream.Collector".equals(chainType.getType().getFullyQualifiedName())) {
					IType type = project.findType("java.util.stream.Collectors");
					if (type != null) {
						results.add(new ChainElement(type, false));
					}
				}
			}
		}
		return results;
	}


	// The following needs to move to jdt ui core manipulation
	public static List<ChainType> resolveBindingsForExpectedTypes(final IJavaProject proj, final CompletionContext ctx) {
		final List<ChainType> types = new LinkedList<>();
		final IType[] expectedTypeSigs = getExpectedType(proj, ctx);
		if (expectedTypeSigs == null) {
			final char[][] expectedTypes = ctx.getExpectedTypesSignatures();
			for (int i = 0; i < expectedTypes.length; i++) {
				String typeSig = new String(expectedTypes[i]);
				int dim = getArrayDimension(expectedTypes[i]);
				ChainType type = new ChainType(typeSig, dim);
				types.add(type);
			}
		} else {
			for (int i = 0; i < expectedTypeSigs.length; i++) {
				ChainType type = new ChainType(expectedTypeSigs[i]);
				types.add(type);
			}
		}
		return types;
	}

	private static int getArrayDimension(final char[] expectedTypesSignatures) {
		if (expectedTypesSignatures != null && expectedTypesSignatures.length > 0) {
			return Signature.getArrayCount(new String(expectedTypesSignatures));
		}

		return 0;
	}

	public static IType[] getExpectedType(final IJavaProject proj, final CompletionContext ctx) {
		IType[] expected = null;
		String[] fqExpectedTypes = getExpectedFullyQualifiedTypeNames(ctx);
		if (fqExpectedTypes != null && fqExpectedTypes.length > 0) {
			expected = new IType[fqExpectedTypes.length];
			for (int i = 0; i < fqExpectedTypes.length; i++) {
				try {
					expected[i] = proj.findType(fqExpectedTypes[i]);
				} catch (JavaModelException e) {
					// do nothing
				}

			}
		}
		return expected;
	}

	public static String[] getExpectedFullyQualifiedTypeNames(final CompletionContext ctx) {
		String[] fqExpectedTypes = null;
		final char[][] expectedTypes = ctx.getExpectedTypesSignatures();
		if (expectedTypes != null && expectedTypes.length > 0) {
			fqExpectedTypes = new String[expectedTypes.length];
			for (int i = 0; i < expectedTypes.length; i++) {
				fqExpectedTypes[i] = SignatureUtil.stripSignatureToFQN(new String(expectedTypes[i]));
			}
		}
		return fqExpectedTypes;
	}

}
