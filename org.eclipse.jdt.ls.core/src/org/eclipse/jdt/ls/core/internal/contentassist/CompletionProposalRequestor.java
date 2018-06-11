/*******************************************************************************
 * Copyright (c) 2016-2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponse;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.google.common.collect.ImmutableSet;

public final class CompletionProposalRequestor extends CompletionRequestor {

	private List<CompletionProposal> proposals = new ArrayList<>();
	private final ICompilationUnit unit;
	private CompletionProposalDescriptionProvider descriptionProvider;
	private CompletionResponse response;
	private boolean fIsTestCodeExcluded;
	private CompletionContext context;

	// Update SUPPORTED_KINDS when mapKind changes
	// @formatter:off
	public static final Set<CompletionItemKind> SUPPORTED_KINDS = ImmutableSet.of(CompletionItemKind.Constructor,
																				CompletionItemKind.Class,
																				CompletionItemKind.Module,
																				CompletionItemKind.Field,
																				CompletionItemKind.Keyword,
																				CompletionItemKind.Reference,
																				CompletionItemKind.Variable,
																				CompletionItemKind.Function,
																				CompletionItemKind.Text);
	// @formatter:on

	public CompletionProposalRequestor(ICompilationUnit aUnit, int offset) {
		this.unit = aUnit;
		response = new CompletionResponse();
		response.setOffset(offset);
		fIsTestCodeExcluded = !isTestSource(unit.getJavaProject(), unit);
		setRequireExtendedContext(true);
	}

	private boolean isTestSource(IJavaProject project, ICompilationUnit cu) {
		if (project == null) {
			return true;
		}
		try {
			IClasspathEntry[] resolvedClasspath = project.getResolvedClasspath(true);
			final IPath resourcePath = cu.getResource().getFullPath();
			for (IClasspathEntry e : resolvedClasspath) {
				if (e.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					if (e.isTest()) {
						if (e.getPath().isPrefixOf(resourcePath)) {
							return true;
						}
					}
				}
			}
		} catch (JavaModelException e) {
		}
		return false;
	}

	@Override
	public void accept(CompletionProposal proposal) {
		if (!isIgnored(proposal.getKind())) {
			if (proposal.getKind() == CompletionProposal.POTENTIAL_METHOD_DECLARATION) {
				acceptPotentialMethodDeclaration(proposal);
			} else {
				if (proposal.getKind() == CompletionProposal.PACKAGE_REF && unit.getParent() != null && String.valueOf(proposal.getCompletion()).equals(unit.getParent().getElementName())) {
					// Hacky way to boost relevance of current package, for package completions, until
					// https://bugs.eclipse.org/518140 is fixed
					proposal.setRelevance(proposal.getRelevance() + 1);
				}
				proposals.add(proposal);
			}
		}
	}

	public List<CompletionItem> getCompletionItems() {
		response.setProposals(proposals);
		CompletionResponses.store(response);
		List<CompletionItem> completionItems = new ArrayList<>(proposals.size());
		for (int i = 0; i < proposals.size(); i++) {
			completionItems.add(toCompletionItem(proposals.get(i), i));
		}
		return completionItems;
	}

	public CompletionItem toCompletionItem(CompletionProposal proposal, int index) {
		final CompletionItem $ = new CompletionItem();
		$.setKind(mapKind(proposal.getKind()));
		Map<String, String> data = new HashMap<>();
		// append data field so that resolve request can use it.
		data.put(CompletionResolveHandler.DATA_FIELD_URI, JDTUtils.toURI(unit));
		data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID,String.valueOf(response.getId()));
		data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID,String.valueOf(index));
		$.setData(data);
		this.descriptionProvider.updateDescription(proposal, $);
		$.setSortText(SortTextHelper.computeSortText(proposal));
		return $;
	}

	@Override
	public void acceptContext(CompletionContext context) {
		super.acceptContext(context);
		this.context = context;
		response.setContext(context);
		this.descriptionProvider = new CompletionProposalDescriptionProvider(context);
	}


	private CompletionItemKind mapKind(final int kind) {
		//When a new CompletionItemKind is added, don't forget to update SUPPORTED_KINDS
		switch (kind) {
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			return CompletionItemKind.Constructor;
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.TYPE_REF:
			return CompletionItemKind.Class;
		case CompletionProposal.FIELD_IMPORT:
		case CompletionProposal.METHOD_IMPORT:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.PACKAGE_REF:
		case CompletionProposal.TYPE_IMPORT:
			return CompletionItemKind.Module;
		case CompletionProposal.FIELD_REF:
		case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
			return CompletionItemKind.Field;
		case CompletionProposal.KEYWORD:
			return CompletionItemKind.Keyword;
		case CompletionProposal.LABEL_REF:
			return CompletionItemKind.Reference;
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return CompletionItemKind.Variable;
		case CompletionProposal.METHOD_DECLARATION:
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			return CompletionItemKind.Function;
			//text
		case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
		case CompletionProposal.JAVADOC_BLOCK_TAG:
		case CompletionProposal.JAVADOC_FIELD_REF:
		case CompletionProposal.JAVADOC_INLINE_TAG:
		case CompletionProposal.JAVADOC_METHOD_REF:
		case CompletionProposal.JAVADOC_PARAM_REF:
		case CompletionProposal.JAVADOC_TYPE_REF:
		case CompletionProposal.JAVADOC_VALUE_REF:
		default:
			return CompletionItemKind.Text;
		}
	}

	@Override
	public void setIgnored(int completionProposalKind, boolean ignore) {
		super.setIgnored(completionProposalKind, ignore);
		if (completionProposalKind == CompletionProposal.METHOD_DECLARATION && !ignore) {
			setRequireExtendedContext(true);
		}
	}

	private void acceptPotentialMethodDeclaration(CompletionProposal proposal) {
		try {
			IJavaElement enclosingElement = null;
			if (response.getContext().isExtended()) {
				enclosingElement = response.getContext().getEnclosingElement();
			} else if (unit != null) {
				// kept for backward compatibility: CU is not reconciled at this moment, information is missing (bug 70005)
				enclosingElement = unit.getElementAt(proposal.getCompletionLocation() + 1);
			}
			if (enclosingElement == null) {
				return;
			}
			IType type = (IType) enclosingElement.getAncestor(IJavaElement.TYPE);
			if (type != null) {
				String prefix = String.valueOf(proposal.getName());
				int completionStart = proposal.getReplaceStart();
				int completionEnd = proposal.getReplaceEnd();
				int relevance = proposal.getRelevance() + 6;

				GetterSetterCompletionProposal.evaluateProposals(type, prefix, completionStart, completionEnd - completionStart, relevance, proposals);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Accept potential method declaration failed for completion ", e);
		}
	}

	@Override
	public boolean isTestCodeExcluded() {
		return fIsTestCodeExcluded;
	}

	public CompletionContext getContext() {
		return context;
	}

}