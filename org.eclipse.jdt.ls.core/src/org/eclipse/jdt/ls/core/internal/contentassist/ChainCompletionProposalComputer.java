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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
import org.eclipse.lsp4j.TextEdit;

public class ChainCompletionProposalComputer {

	private List<ChainElement> entrypoints;

	private String[] excludedTypes;

	private ICompilationUnit cu;

	private CompletionProposalRequestor coll;

	private Map<String, List<TextEdit>> additionalEdits = new HashMap<>();

	public ChainCompletionProposalComputer(ICompilationUnit cu, CompletionProposalRequestor coll) {
		this.cu = cu;
		this.coll = coll;
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

		excludedTypes = JavaManipulation.getPreference("recommenders.chain.ignore_types", cu.getJavaProject()).split("\\|"); //$NON-NLS-1$
		for (int i = 0; i < excludedTypes.length; ++i) {
			excludedTypes[i] = "L" + excludedTypes[i].replace('.', '/'); //$NON-NLS-1$
		}

		final IType invocationType = cu.findPrimaryType();

		final List<ChainType> expectedTypes = resolveBindingsForExpectedTypes(cu.getJavaProject(), coll.getContext());
		final ChainFinder finder = new ChainFinder(expectedTypes, Arrays.asList(excludedTypes), invocationType);
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> future = executor.submit(() -> {
				if (findEntrypoints(expectedTypes, cu.getJavaProject())) {
					finder.startChainSearch(entrypoints, maxChains, minDepth, maxDepth);
				}
			});
			long timeout = Long.parseLong(JavaManipulation.getPreference("recommenders.chain.timeout", cu.getJavaProject()));
			future.get(timeout, TimeUnit.SECONDS);
		} catch (final Exception e) {
			finder.cancel();
			executor.shutdownNow();
		}

		return buildCompletionProposals(finder.getChains());
	}

	private List<CompletionItem> buildCompletionProposals(final List<Chain> chains) {
		final List<CompletionItem> proposals = new LinkedList<>();

		for (final Chain chain : chains) {
			proposals.add(create(chain));
		}
		return proposals;
	}

	private boolean findEntrypoints(List<ChainType> expectedTypes, IJavaProject project) {
		entrypoints = new LinkedList<>();
		for (CompletionProposal prop : coll.getProposals()) {
			IJavaElement javaElement = resolveJavaElement(prop, cu.getJavaProject());
			if (javaElement != null) {
				IJavaElement e = javaElement;
				if (matchesExpectedPrefix(e) && !ChainFinder.isFromExcludedType(Arrays.asList(excludedTypes), e)) {
					ChainElement ce = new ChainElement(e, false);
					if (ce.getElementType() != null) {
						entrypoints.add(ce);
					}
				}
			} else {
				IJavaElement[] visibleElements = coll.getContext().getVisibleElements(null);
				for (IJavaElement ve : visibleElements) {
					if (matchesExpectedPrefix(ve) && !ChainFinder.isFromExcludedType(Arrays.asList(excludedTypes), ve)) {
						ChainElement ce = new ChainElement(ve, false);
						if (ce.getElementType() != null) {
							entrypoints.add(ce);
						}
					}
				}
			}
		}
		try {
			entrypoints.addAll(computeContextEntrypoints(expectedTypes, project));
		} catch (JavaModelException e) {
			// continue
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
		CompilationUnit cuNode = getASTRoot();
		AST ast = cuNode.getAST();
		return ast.resolveWellKnownType(ChainElementAnalyzer.getExpectedFullyQualifiedTypeName(coll.getContext())) != null || ChainElementAnalyzer.getExpectedType(cu.getJavaProject(), coll.getContext()) != null;
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

	private CompletionItem create(final Chain chain) {
		final String label = ChainElement.createChainCode(chain, true, 0);
		final String insert = ChainElement.createChainCode(chain, false, chain.getExpectedDimensions());
		final CompletionItem ci = new CompletionItem();
		ci.setLabel(label);
		ci.setInsertText(insert);
		ci.setKind(CompletionItemKind.Method);

		ChainElement root = chain.getElements().get(0);
		if (root.getElementType() == ElementType.TYPE) {
			ci.setAdditionalTextEdits(addImport(((IType) root.getElement()).getFullyQualifiedName()));
		}
		return ci;
	}

	private List<ChainElement> computeContextEntrypoints(List<ChainType> expectedTypes, IJavaProject project) throws JavaModelException {
		final List<ChainElement> results = new ArrayList<>();
		for (ChainType chainType : expectedTypes) {
			if (chainType.getType() == null) {
				continue;
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
