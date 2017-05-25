/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalReplacementProvider;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CompletionItem;
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

	public CompletionItem resolve(CompletionItem param, IProgressMonitor monitor) {

		@SuppressWarnings("unchecked")
		Map<String, String> data = (Map<String, String>) param.getData();
		// clean resolve data
		param.setData(null);

		if (!data.containsKey(DATA_FIELD_URI) || !data.containsKey(DATA_FIELD_REQUEST_ID) || !data.containsKey(DATA_FIELD_PROPOSAL_ID)) {
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
		CompletionProposalReplacementProvider proposalProvider = new CompletionProposalReplacementProvider(unit,
				completionResponse.getContext(),
				completionResponse.getOffset(),
				this.manager.getClientPreferences());
		proposalProvider.updateReplacement(completionResponse.getProposals().get(proposalId), param, '\0');

		if (data.containsKey(DATA_FIELD_DECLARATION_SIGNATURE)) {
			String typeName = stripSignatureToFQN(String.valueOf(data.get(DATA_FIELD_DECLARATION_SIGNATURE)));
			try {
				IMember member = null;
				IType type = unit.getJavaProject().findType(typeName);

				if (type!=null && data.containsKey(DATA_FIELD_NAME)) {
					String name = data.get(DATA_FIELD_NAME);
					String[] paramSigs = CharOperation.NO_STRINGS;
					if(data.containsKey( DATA_FIELD_SIGNATURE)){
						String[] parameters= Signature.getParameterTypes(String.valueOf(fix83600(data.get(DATA_FIELD_SIGNATURE).toCharArray())));
						for (int i= 0; i < parameters.length; i++) {
							parameters[i]= getLowerBound(parameters[i]);
						}
						paramSigs = parameters;
					}
					IMethod method = type.getMethod(name, paramSigs);
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
						javadoc = new SimpleTimeLimiter().callWithTimeout(() -> {
							Reader reader = JavadocContentAccess.getPlainTextContentReader(curMember);
							return reader == null? null:CharStreams.toString(reader);
						}, 500, TimeUnit.MILLISECONDS, true);
					} catch (UncheckedTimeoutException tooSlow) {
						//Ignore error for now as it's spamming clients on content assist.
						//TODO cache javadoc resolution results?
						//JavaLanguageServerPlugin.logError("Unable to get documentation under 500ms");
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException("Unable to read documentation", e);
					}
					param.setDocumentation(javadoc);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Unable to resolve compilation", e);
			}
		}
		return param;
	}
}
