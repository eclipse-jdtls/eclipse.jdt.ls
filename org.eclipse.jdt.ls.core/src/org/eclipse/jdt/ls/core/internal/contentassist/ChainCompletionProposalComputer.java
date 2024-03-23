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
import java.util.Collections;
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
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
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
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
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
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

	public List<CompletionItem> computeCompletionProposals() {
		if (!shouldPerformCompletionOnExpectedType()) {
			return Collections.emptyList();
		}
		return executeCallChainSearch();
	}

	private List<CompletionItem> executeCallChainSearch() {
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
		return buildCompletionProposals(found);
	}

	private List<CompletionItem> buildCompletionProposals(final List<Chain> chains) {
		final List<CompletionItem> proposals = new LinkedList<>();

		for (final Chain chain : chains) {
			try {
				proposals.add(create(chain));
			} catch (JavaModelException e) {
				// ignore
			}
		}
		return proposals;
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

	private CompletionItem create(final Chain chain) throws JavaModelException {
		final String insert = createInsertText(chain, chain.getExpectedDimensions());
		final CompletionItem ci = new CompletionItem();

		ci.setTextEditText(insert);
		ci.setInsertText(getQualifiedMethodName(insert));
		ci.setInsertTextFormat(snippetStringSupported ? InsertTextFormat.Snippet : InsertTextFormat.PlainText);
		ci.setKind(CompletionItemKind.Method);
		setLabelDetails(chain, ci);

		ChainElement root = chain.getElements().get(0);
		if (root.getElementType() == ElementType.TYPE) {
			ci.setAdditionalTextEdits(addImport(((IType) root.getElement()).getFullyQualifiedName()));
		}
		return ci;
	}

	// given Collection.emptyList()
	// return Collection.emptyList
	private String getQualifiedMethodName(String name) {
		int index = name.indexOf('(');
		if (index > 0) {
			return name.substring(0, index);
		}
		return name;
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

	private void setLabelDetails(final Chain chain, final CompletionItem item) throws JavaModelException {
		final CompletionItemLabelDetails details = new CompletionItemLabelDetails();

		ChainElement last = chain.getElements().get(chain.getElements().size() - 1);
		String lastDetails = "";
		switch (last.getElementType()) {
			case FIELD:
			case TYPE:
			case LOCAL_VARIABLE:
				item.setLabel(last.getElement().getElementName());
				details.setDescription(last.getReturnType().toString());
				break;
			case METHOD:
				final IMethod method = (IMethod) last.getElement();
				final String returnTypeSig = method.getReturnType();
				final String signatureQualifier = Signature.getSignatureQualifier(returnTypeSig);
				String[] signatureComps = null;
				if (signatureQualifier != null && !signatureQualifier.isBlank()) {
					signatureComps = new String[2];
					signatureComps[0] = signatureQualifier;
					signatureComps[1] = Signature.getSignatureSimpleName(returnTypeSig);
				} else {
					signatureComps = new String[1];
					signatureComps[0] = Signature.getSignatureSimpleName(returnTypeSig);
				}

				details.setDescription(Signature.toQualifiedName(signatureComps));
				lastDetails = "(%s)".formatted(Stream.of(method.getParameterNames()).collect(Collectors.joining(",")));
				break;
			default:
		}

		List<ChainElement> receivers = chain.getElements().subList(0, chain.getElements().size() - 1);
		StringBuilder receiversString = new StringBuilder(64);
		receiversString.append(" - ");
		for (final ChainElement edge : receivers) {
			switch (edge.getElementType()) {
				case FIELD:
				case TYPE:
				case LOCAL_VARIABLE:
					appendVariableString(edge, receiversString);
					break;
				case METHOD:
					final IMethod method = (IMethod) edge.getElement();
					receiversString.append(method.getElementName());
					receiversString.append("(%s)".formatted(Stream.of(method.getParameterNames()).collect(Collectors.joining(","))));
					break;
				default:
			}
			receiversString.append(".");
		}
		details.setDetail(receiversString.append(last.getElement().getElementName()).append(lastDetails).toString());
		item.setLabelDetails(details);
		item.setLabel(last.getElement().getElementName().concat(lastDetails));
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
			sb.append(Stream.of(parameterNames).map(n -> "${%s:%s}".formatted(counter.getAndIncrement(), n)).collect(Collectors.joining(", ")));
		}
		sb.append(")");
	}

	private void appendArrayDimensions(final StringBuilder sb, final int dimension, final int expectedDimension, final boolean appendVariables, final AtomicInteger counter) {
		for (int i = dimension; i-- > expectedDimension;) {
			sb.append("[");
			if (appendVariables) {
				sb.append("${%s:%s}".formatted(counter.getAndIncrement(), "i"));
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

	private List<TextEdit> addImport(String type) {
		if (additionalEdits.containsKey(type)) {
			return additionalEdits.get(type);
		}

		try {
			boolean qualified = type.indexOf('.') != -1;
			if (!qualified) {
				return Collections.emptyList();
			}

			CompilationUnit root = getASTRoot();
			ImportRewrite importRewrite;
			if (root == null) {
				importRewrite = StubUtility.createImportRewrite(cu, true);
			} else {
				importRewrite = StubUtility.createImportRewrite(root, true);
			}

			ImportRewriteContext context;
			if (root == null) {
				context = null;
			} else {
				context = new ContextSensitiveImportRewriteContext(root, coll.getContext().getOffset(), importRewrite);
			}

			importRewrite.addImport(type, context);
			List<TextEdit> edits = this.additionalEdits.getOrDefault(type, new ArrayList<>());
			TextEditConverter converter = new TextEditConverter(cu, importRewrite.rewriteImports(new NullProgressMonitor()));
			edits.addAll(converter.convert());
			this.additionalEdits.put(type, edits);
			return edits;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
			return Collections.emptyList();
		}
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
