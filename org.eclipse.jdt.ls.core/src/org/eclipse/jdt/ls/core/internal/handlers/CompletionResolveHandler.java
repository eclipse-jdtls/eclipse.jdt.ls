/*******************************************************************************
 * Copyright (c) 2016-2021 Red Hat Inc. and others.
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

import static org.eclipse.jdt.internal.corext.template.java.SignatureUtil.fix83600;
import static org.eclipse.jdt.internal.corext.template.java.SignatureUtil.getLowerBound;
import static org.eclipse.jdt.internal.corext.template.java.SignatureUtil.stripSignatureToFQN;

import java.io.Reader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;
import org.eclipse.jdt.internal.codeassist.impl.Engine;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalReplacementProvider;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.jdt.ls.core.internal.contentassist.SnippetCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SnippetUtils;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess2;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.osgi.util.NLS;

import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
/**
 * Adds the completion string and documentation.
 * It checks the client capabilities.
 * If a client supports both SnippetStrings and SignatureHelp
 * SnippetStrings are prioritized for handling parameters.
 *
 *
 */
@SuppressWarnings("restriction")
public class CompletionResolveHandler {

	public static final String EMPTY_STRING = "";
	public static final String DEFAULT = "Default: ";
	private static final String VALUE = "Value: ";
	private final PreferenceManager manager;

	public CompletionResolveHandler(PreferenceManager manager) {
		this.manager = manager;
	}

	public static final String DATA_FIELD_URI = "uri";
	public static final String DATA_FIELD_DECLARATION_SIGNATURE = "decl_signature";
	public static final String DATA_FIELD_SIGNATURE= "signature";
	public static final String DATA_FIELD_NAME = "name";
	public static final String DATA_FIELD_REQUEST_ID = "rid";
	public static final String DATA_FIELD_PROPOSAL_ID = "pid";
	public static final String DATA_FIELD_CONSTANT_VALUE = "constant_value";
	public static final String DATA_METHOD_DEFAULT_VALUE = "default_value";

	public CompletionItem resolve(CompletionItem param, IProgressMonitor monitor) {

		@SuppressWarnings("unchecked")
		Map<String, String> data = JSONUtility.toModel(param.getData(),Map.class);
		// clean resolve data
		param.setData(null);
		if (!CompletionProposalRequestor.SUPPORTED_KINDS.contains(param.getKind()) || data == null || !data.containsKey(DATA_FIELD_URI) || !data.containsKey(DATA_FIELD_REQUEST_ID) || !data.containsKey(DATA_FIELD_PROPOSAL_ID)) {
			return param;
		}
		int proposalId = Integer.parseInt(data.get(DATA_FIELD_PROPOSAL_ID));
		long requestId = Long.parseLong(data.get(DATA_FIELD_REQUEST_ID));
		CompletionResponse completionResponse = CompletionResponses.get(requestId);
		if (completionResponse == null || completionResponse.getProposals().size() <= proposalId) {
			throw new IllegalStateException("Invalid completion proposal");
		}
		String uri = data.get(DATA_FIELD_URI);
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		if (unit == null) {
			throw new IllegalStateException(NLS.bind("Unable to match Compilation Unit from {0} ", uri));
		}
		if (monitor.isCanceled()) {
			param.setData(null);
			return param;
		}

		// generic snippets
		if (param.getKind() == CompletionItemKind.Snippet) {
			try {
				CompletionContext ctx = completionResponse.getContext();
				CompletionProposal proposal = completionResponse.getProposals().get(proposalId);
				Template template = ((SnippetCompletionProposal) proposal).getTemplate();
				String content = SnippetCompletionProposal.evaluateGenericTemplate(unit, ctx, template);

				Range range = JDTUtils.toRange(unit, ctx.getOffset(), 0);
				TextEdit textEdit = new TextEdit(range, content);
				param.setTextEdit(Either.forLeft(textEdit));
				param.setDocumentation(SnippetUtils.beautifyDocument(content));
				param.setData(null);
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
			return param;
		}

		if (manager.getClientPreferences().isResolveAdditionalTextEditsSupport()) {
			CompletionProposalReplacementProvider proposalProvider = new CompletionProposalReplacementProvider(
				unit,
				completionResponse.getContext(),
				completionResponse.getOffset(),
				manager.getPreferences(),
				manager.getClientPreferences(),
				true
			);
			proposalProvider.updateReplacement(completionResponse.getProposals().get(proposalId), param, '\0');
		}
		if (data.containsKey(DATA_FIELD_DECLARATION_SIGNATURE)) {
			String typeName = stripSignatureToFQN(String.valueOf(data.get(DATA_FIELD_DECLARATION_SIGNATURE)));
			try {
				IMember member = null;
				IType type = unit.getJavaProject().findType(typeName);

				CompletionProposal proposal = completionResponse.getProposals().get(proposalId);
				if (type!=null && data.containsKey(DATA_FIELD_NAME)) {
					String name = data.get(DATA_FIELD_NAME);
					String[] paramSigs = CharOperation.NO_STRINGS;
					if(data.containsKey( DATA_FIELD_SIGNATURE)){
						if (proposal instanceof InternalCompletionProposal internalProposal) {
							Binding binding = internalProposal.getBinding();
							if (binding instanceof MethodBinding methodBinding) {
								MethodBinding original = methodBinding.original();
								char[] signature;
								if (original != binding) {
									signature = Engine.getSignature(original);
								} else {
									signature = Engine.getSignature(methodBinding);
								}
								String[] parameters = Signature.getParameterTypes(String.valueOf(fix83600(signature)));
								for (int i = 0; i < parameters.length; i++) {
									parameters[i] = getLowerBound(parameters[i]);
								}
								paramSigs = parameters;
							}
						}
					}
					IMethod method = type.getMethod(name, paramSigs);
					IMethod[] methods = type.findMethods(method);
					if (methods != null && methods.length > 0) {
						method = methods[0];
					}
					if (method.exists()) {
						member = method;
					} else {
						IField field = type.getField(name);
						if (field.exists()) {
							member = field;
						}
					}
				} else {
					member = type;
				}

				if (member != null && member.exists() && !monitor.isCanceled()) {
					String javadoc = null;
					try {
						final IMember curMember = member;
						javadoc = SimpleTimeLimiter.create(JavaLanguageServerPlugin.getExecutorService()).callWithTimeout(() -> {
							Reader reader;
							if (manager.getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
								reader = JavadocContentAccess2.getMarkdownContentReader(curMember);
							} else {
								reader = JavadocContentAccess.getPlainTextContentReader(curMember);
							}
							return reader == null? null:CharStreams.toString(reader);
						}, 500, TimeUnit.MILLISECONDS);
					} catch (UncheckedTimeoutException | TimeoutException tooSlow) {
						//Ignore error for now as it's spamming clients on content assist.
						//TODO cache javadoc resolution results?
						//JavaLanguageServerPlugin.logError("Unable to get documentation under 500ms");
						monitor.setCanceled(true);
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException("Unable to read documentation", e);
						monitor.setCanceled(true);
					}

					String constantValue = null;
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
								constantValue = JDTUtils.getConstantValue(field, typeRoot, nameRegion);
							}
						} catch (JavaModelException e) {
							JavaLanguageServerPlugin.log(e);
						}
					}
					if (constantValue != null) {
						if (manager.getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
							javadoc = (javadoc == null ? EMPTY_STRING : javadoc) + "\n\n" + VALUE + constantValue;
						} else {
							javadoc = (javadoc == null ? EMPTY_STRING : javadoc) + VALUE + constantValue;
						}
					}

					String defaultValue = null;
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
								defaultValue = JDTUtils.getAnnotationMemberDefaultValue(method, typeRoot, nameRegion);
							}
						} catch (JavaModelException e) {
							JavaLanguageServerPlugin.log(e);
						}
					}
					if (defaultValue != null) {
						if (manager.getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
							javadoc = (javadoc == null ? EMPTY_STRING : javadoc) + "\n\n" + DEFAULT + defaultValue;
						} else {
							javadoc = (javadoc == null ? EMPTY_STRING : javadoc) + DEFAULT + defaultValue;
						}
					}
					if (javadoc != null) {
						if (manager.getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
							MarkupContent markupContent = new MarkupContent();
							markupContent.setKind(MarkupKind.MARKDOWN);
							markupContent.setValue(javadoc);
							param.setDocumentation(markupContent);
						} else {
							param.setDocumentation(javadoc);
						}
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Unable to resolve compilation", e);
				monitor.setCanceled(true);
			}
		}
		if (monitor.isCanceled()) {
			param.setData(null);
		}
		return param;
	}

}
