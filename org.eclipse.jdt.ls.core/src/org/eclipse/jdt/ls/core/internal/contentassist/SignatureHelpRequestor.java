/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.eclipse.jdt.internal.corext.template.java.SignatureUtil.getLowerBound;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponse;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

public final class SignatureHelpRequestor extends CompletionRequestor {

	private List<CompletionProposal> proposals = new ArrayList<>();
	private final ICompilationUnit unit;
	private CompletionProposalDescriptionProvider descriptionProvider;
	private CompletionResponse response;

	public SignatureHelpRequestor(ICompilationUnit aUnit, int offset) {
		this.unit = aUnit;
		response = new CompletionResponse();
		response.setOffset(offset);
		setRequireExtendedContext(true);
	}

	public SignatureHelp getSignatureHelp(IProgressMonitor monitor) {
		SignatureHelp signatureHelp = new SignatureHelp();
		response.setProposals(proposals);
		CompletionResponses.store(response);

		List<SignatureInformation> infos = new ArrayList<>();
		for (int i = 0; i < proposals.size(); i++) {
			if (!monitor.isCanceled()) {
				infos.add(this.toSignatureInformation(proposals.get(i)));
			} else {
				return signatureHelp;
			}
		}
		infos.sort((SignatureInformation a, SignatureInformation b) -> a.getParameters().size() - b.getParameters().size());
		signatureHelp.getSignatures().addAll(infos);

		return signatureHelp;
	}

	@Override
	public boolean isIgnored(int completionProposalKind) {
		return completionProposalKind != CompletionProposal.METHOD_REF;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.CompletionRequestor#accept(org.eclipse.jdt.core.CompletionProposal)
	 */
	@Override
	public void accept(CompletionProposal proposal) {
		if (!isIgnored(proposal.getKind())) {
			if (proposal.getKind() == CompletionProposal.PACKAGE_REF && unit.getParent() != null && String.valueOf(proposal.getCompletion()).equals(unit.getParent().getElementName())) {
				// Hacky way to boost relevance of current package, for package completions, until
				// https://bugs.eclipse.org/518140 is fixed
				proposal.setRelevance(proposal.getRelevance() + 1);
			}
			proposals.add(proposal);
		}
	}

	@Override
	public void acceptContext(CompletionContext context) {
		super.acceptContext(context);
		response.setContext(context);
		this.descriptionProvider = new CompletionProposalDescriptionProvider(context);
	}

	public SignatureInformation toSignatureInformation(CompletionProposal methodProposal) {
		SignatureInformation $ = new SignatureInformation();
		StringBuilder desription = descriptionProvider.createMethodProposalDescription(methodProposal);
		$.setLabel(desription.toString());
		$.setDocumentation(this.computeJavaDoc(methodProposal));

		char[] signature = SignatureUtil.fix83600(methodProposal.getSignature());
		char[][] parameterNames = methodProposal.findParameterNames(null);
		char[][] parameterTypes = Signature.getParameterTypes(signature);

		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypes[i] = Signature.getSimpleName(Signature.toCharArray(SignatureUtil.getLowerBound(parameterTypes[i])));
		}

		if (Flags.isVarargs(methodProposal.getFlags())) {
			int index = parameterTypes.length - 1;
			parameterTypes[index] = convertToVararg(parameterTypes[index]);
		}

		List<ParameterInformation> parameterInfos = new LinkedList<>();
		for (int i = 0; i < parameterTypes.length; i++) {
			StringBuilder builder = new StringBuilder();
			builder.append(parameterTypes[i]);
			builder.append(' ');
			builder.append(parameterNames[i]);

			parameterInfos.add(new ParameterInformation(builder.toString(), null));
		}

		$.setParameters(parameterInfos);

		return $;
	}

	private char[] convertToVararg(char[] typeName) {
		if (typeName == null) {
			return typeName;
		}
		final int len = typeName.length;
		if (len < 2) {
			return typeName;
		}

		if (typeName[len - 1] != ']') {
			return typeName;
		}
		if (typeName[len - 2] != '[') {
			return typeName;
		}

		char[] vararg = new char[len + 1];
		System.arraycopy(typeName, 0, vararg, 0, len - 2);
		vararg[len - 2] = '.';
		vararg[len - 1] = '.';
		vararg[len] = '.';
		return vararg;
	}

	public String computeJavaDoc(CompletionProposal proposal) {
		try {
			IType type = unit.getJavaProject().findType(SignatureUtil.stripSignatureToFQN(String.valueOf(proposal.getDeclarationSignature())));
			if (type != null) {
				String[] parameters= Signature.getParameterTypes(String.valueOf(SignatureUtil.fix83600(proposal.getSignature())));
				for (int i= 0; i < parameters.length; i++) {
					parameters[i]= getLowerBound(parameters[i]);
				}

				IMethod method = JavaModelUtil.findMethod(String.valueOf(proposal.getName()), parameters, proposal.isConstructor(), type);

				if (method != null && method.exists()) {
					ICompilationUnit unit = type.getCompilationUnit();
					if (unit != null) {
						unit.reconcile(ICompilationUnit.NO_AST, false, null, null);
					}

					String javadoc = null;
					try {
						javadoc = new SimpleTimeLimiter().callWithTimeout(() -> {
							Reader reader = JavadocContentAccess.getPlainTextContentReader(method);
							return reader == null? null:CharStreams.toString(reader);
						}, 500, TimeUnit.MILLISECONDS, true);
					} catch (UncheckedTimeoutException tooSlow) {
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException("Unable to read documentation", e);
					}
					return javadoc;
				}
			}

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Unable to resolve signaturehelp javadoc", e);
		}
		return null;
	}

	/**
	 * Gets the reader content as a String
	 *
	 * @param reader
	 *            the reader
	 * @return the reader content as string
	 */
	private static String getString(Reader reader) {
		try {
			return CharStreams.toString(reader);
		} catch (IOException ignored) {
			//meh
		}
		return null;
	}
}