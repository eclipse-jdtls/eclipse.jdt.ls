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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.ui.text.Chain;
import org.eclipse.jdt.internal.ui.text.ChainElement;
import org.eclipse.jdt.internal.ui.text.ChainElementAnalyzer;
import org.eclipse.jdt.internal.ui.text.ChainFinder;
import org.eclipse.jdt.internal.ui.text.ChainType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

public class ChainCompletionProposalComputer {

	private List<ChainElement> entrypoints;

	private String[] excludedTypes;

	private ICompilationUnit cu;

	private CompletionProposalRequestor coll;

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

		final List<ChainType> expectedTypes = ChainElementAnalyzer.resolveBindingsForExpectedTypes(cu.getJavaProject(), coll.getContext());
		final ChainFinder finder = new ChainFinder(expectedTypes, Arrays.asList(excludedTypes), invocationType);
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> future = executor.submit(() -> {
				if (findEntrypoints()) {
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

	private boolean findEntrypoints() {
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
					if (ve.getElementName().equals(new String(prop.getCompletion())) && matchesExpectedPrefix(ve) && !ChainFinder.isFromExcludedType(Arrays.asList(excludedTypes), ve)) {
						ChainElement ce = new ChainElement(ve, false);
						if (ce.getElementType() != null) {
							entrypoints.add(ce);
						}
					}
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
		AST ast;
		CompilationUnit cuNode = SharedASTProviderCore.getAST(cu, SharedASTProviderCore.WAIT_NO, null);
		if (cuNode == null) {
			ASTParser p = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
			p.setSource(cu);
			p.setResolveBindings(true);
			p.setCompilerOptions(RefactoringASTParser.getCompilerOptions(cu));
			cuNode = (CompilationUnit) p.createAST(null);
		}
		ast = cuNode.getAST();
		return ast.resolveWellKnownType(ChainElementAnalyzer.getExpectedFullyQualifiedTypeName(coll.getContext())) != null || ChainElementAnalyzer.getExpectedType(cu.getJavaProject(), coll.getContext()) != null;
	}

	private boolean matchesExpectedPrefix(final IJavaElement element) {
		String prefix = String.valueOf(coll.getContext().getToken());
		return String.valueOf(element.getElementName()).startsWith(prefix);
	}

	private static CompletionItem create(final Chain chain) {
		final String title = ChainElement.createChainCode(chain, true, 0);
		final String body = ChainElement.createChainCode(chain, false, chain.getExpectedDimensions());
		final CompletionItem ci = new CompletionItem();
		ci.setLabel(title);
		ci.setInsertText(title);
		ci.setKind(CompletionItemKind.Method);
		return ci;
	}

}
