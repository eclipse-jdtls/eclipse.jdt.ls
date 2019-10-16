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

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponse;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.jface.text.Region;
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
																				CompletionItemKind.Constant,
																				CompletionItemKind.Interface,
																				CompletionItemKind.Enum,
																				CompletionItemKind.EnumMember,
																				CompletionItemKind.Module,
																				CompletionItemKind.Field,
																				CompletionItemKind.Keyword,
																				CompletionItemKind.Reference,
																				CompletionItemKind.Variable,
																				CompletionItemKind.Method,
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
		if (isFiltered(proposal)) {
			return;
		}
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
		$.setKind(mapKind(proposal));
		Map<String, String> data = new HashMap<>();
		// append data field so that resolve request can use it.
		data.put(CompletionResolveHandler.DATA_FIELD_URI, JDTUtils.toURI(unit));
		data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, String.valueOf(response.getId()));
		data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, String.valueOf(index));
		$.setData(data);
		this.descriptionProvider.updateDescription(proposal, $);
		$.setSortText(SortTextHelper.computeSortText(proposal));
		if (proposal.getKind() == CompletionProposal.FIELD_REF) {
			try {
				IField field = JDTUtils.resolveField(proposal, unit.getJavaProject());
				Region nameRegion = null;
				if (field != null) {
					ITypeRoot typeRoot = field.getTypeRoot();
					ISourceRange nameRange = ((ISourceReference) field).getNameRange();
					if (SourceRange.isAvailable(nameRange)) {
						nameRegion = new Region(nameRange.getOffset(), nameRange.getLength());
					}
					String constantValue = JDTUtils.getConstantValue(field, typeRoot, nameRegion);
					if (constantValue != null) {
						String label = $.getLabel();
						$.setLabel(label + " = " + constantValue);
						data.put(CompletionResolveHandler.DATA_FIELD_CONSTANT_VALUE, constantValue);
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
		if (proposal.getKind() == CompletionProposal.METHOD_REF || proposal.getKind() == CompletionProposal.ANNOTATION_ATTRIBUTE_REF) {
			try {
				IMethod method = JDTUtils.resolveMethod(proposal, unit.getJavaProject());
				Region nameRegion = null;
				if (method != null) {
					ITypeRoot typeRoot = method.getTypeRoot();
					ISourceRange nameRange = ((ISourceReference) method).getNameRange();
					if (SourceRange.isAvailable(nameRange)) {
						nameRegion = new Region(nameRange.getOffset(), nameRange.getLength());
					}
					String defaultValue = JDTUtils.getAnnotationMemberDefaultValue(method, typeRoot, nameRegion);
					if (defaultValue != null) {
						String label = $.getLabel();
						$.setLabel(label + " (Default: " + defaultValue + ")");
						data.put(CompletionResolveHandler.DATA_METHOD_DEFAULT_VALUE, defaultValue);
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
		return $;
	}

	@Override
	public void acceptContext(CompletionContext context) {
		super.acceptContext(context);
		this.context = context;
		response.setContext(context);
		this.descriptionProvider = new CompletionProposalDescriptionProvider(context);
	}


	private CompletionItemKind mapKind(final CompletionProposal proposal) {
		//When a new CompletionItemKind is added, don't forget to update SUPPORTED_KINDS
		int kind = proposal.getKind();
		int flags = proposal.getFlags();
		switch (kind) {
		case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
		case CompletionProposal.CONSTRUCTOR_INVOCATION:
			return CompletionItemKind.Constructor;
		case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
		case CompletionProposal.TYPE_REF:
			if (Flags.isInterface(flags)) {
				return CompletionItemKind.Interface;
			} else if (Flags.isEnum(flags)) {
				return CompletionItemKind.Enum;
			}
			return CompletionItemKind.Class;
		case CompletionProposal.FIELD_IMPORT:
		case CompletionProposal.METHOD_IMPORT:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.PACKAGE_REF:
		case CompletionProposal.TYPE_IMPORT:
		case CompletionProposal.MODULE_DECLARATION:
		case CompletionProposal.MODULE_REF:
			return CompletionItemKind.Module;
		case CompletionProposal.FIELD_REF:
			if (Flags.isEnum(flags)) {
				return CompletionItemKind.EnumMember;
			}
			if (Flags.isStatic(flags) && Flags.isFinal(flags)) {
				return CompletionItemKind.Constant;
			}
			return CompletionItemKind.Field;
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
			return CompletionItemKind.Method;
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

	/**
	 * copied from
	 * org.eclipse.jdt.ui.text.java.CompletionProposalCollector.isFiltered(CompletionProposal)
	 */
	protected boolean isFiltered(CompletionProposal proposal) {
		if (isIgnored(proposal.getKind())) {
			return true;
		}
		// Only filter types and constructors from completion.
		// Methods from already imported types and packages can still be proposed.
		// See https://github.com/eclipse/eclipse.jdt.ls/issues/1212
		switch (proposal.getKind()) {
			case CompletionProposal.CONSTRUCTOR_INVOCATION:
			case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
			case CompletionProposal.JAVADOC_TYPE_REF:
			case CompletionProposal.TYPE_REF: {
				char[] declaringType = getDeclaringType(proposal);
				return declaringType != null && TypeFilter.isFiltered(declaringType);
			}
		}
		return false;
	}

	/**
	 * copied from
	 * org.eclipse.jdt.ui.text.java.CompletionProposalCollector.getDeclaringType(CompletionProposal)
	 */
	protected final char[] getDeclaringType(CompletionProposal proposal) {
		switch (proposal.getKind()) {
			case CompletionProposal.METHOD_DECLARATION:
			case CompletionProposal.METHOD_NAME_REFERENCE:
			case CompletionProposal.JAVADOC_METHOD_REF:
			case CompletionProposal.METHOD_REF:
			case CompletionProposal.CONSTRUCTOR_INVOCATION:
			case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
			case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
			case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
			case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
			case CompletionProposal.FIELD_REF:
			case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
			case CompletionProposal.JAVADOC_FIELD_REF:
			case CompletionProposal.JAVADOC_VALUE_REF:
				char[] declaration = proposal.getDeclarationSignature();
				// special methods may not have a declaring type: methods defined on arrays etc.
				// Currently known: class literals don't have a declaring type - use Object
				if (declaration == null) {
					return "java.lang.Object".toCharArray(); //$NON-NLS-1$
				}
				return Signature.toCharArray(declaration);
			case CompletionProposal.PACKAGE_REF:
			case CompletionProposal.MODULE_REF:
			case CompletionProposal.MODULE_DECLARATION:
				return proposal.getDeclarationSignature();
			case CompletionProposal.JAVADOC_TYPE_REF:
			case CompletionProposal.TYPE_REF:
				return Signature.toCharArray(proposal.getSignature());
			case CompletionProposal.LOCAL_VARIABLE_REF:
			case CompletionProposal.VARIABLE_DECLARATION:
			case CompletionProposal.KEYWORD:
			case CompletionProposal.LABEL_REF:
			case CompletionProposal.JAVADOC_BLOCK_TAG:
			case CompletionProposal.JAVADOC_INLINE_TAG:
			case CompletionProposal.JAVADOC_PARAM_REF:
				return null;
			default:
				Assert.isTrue(false);
				return null;
		}
	}

}