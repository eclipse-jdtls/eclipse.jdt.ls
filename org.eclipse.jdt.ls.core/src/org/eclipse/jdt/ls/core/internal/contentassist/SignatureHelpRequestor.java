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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

public final class SignatureHelpRequestor extends CompletionProposalRequestor {

	public SignatureHelpRequestor(ICompilationUnit aUnit, int offset) {
		super(aUnit, offset);
		setRequireExtendedContext(true);
	}

	public SignatureHelp getSignatureHelp() {
		SignatureHelp signatureHelp = new SignatureHelp();
		response.setProposals(proposals);
		CompletionResponses.store(response);

		for (int i = 0; i < proposals.size(); i++) {
			CompletionItem item = this.toCompletionItem(proposals.get(i), i);
			signatureHelp.getSignatures().add(this.toSignatureInformation(item, proposals.get(i)));
		}

		signatureHelp.getSignatures().sort((SignatureInformation a, SignatureInformation b) -> a.getParameters().size() - b.getParameters().size());

		return signatureHelp;
	}

	public SignatureInformation toSignatureInformation(CompletionItem item, CompletionProposal methodProposal) {
		SignatureInformation $ = new SignatureInformation();
		$.setLabel(item.getLabel());
		$.setDocumentation(item.getDocumentation());

		char[] signature = SignatureUtil.fix83600(methodProposal.getSignature());
		char[][] parameterNames = methodProposal.findParameterNames(null);
		char[][] parameterTypes = Signature.getParameterTypes(signature);

		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypes[i] = createTypeDisplayName(SignatureUtil.getLowerBound(parameterTypes[i]));
		}

		if (Flags.isVarargs(methodProposal.getFlags())) {
			int index = parameterTypes.length - 1;
			parameterTypes[index] = convertToVararg(parameterTypes[index]);
		}

		List<ParameterInformation> parameterInfos = new LinkedList<>();
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterInfos.add(new ParameterInformation(new String(parameterNames[i]), new String(parameterTypes[i])));
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

	private char[] createTypeDisplayName(char[] typeSignature) throws IllegalArgumentException {
		char[] displayName = Signature.getSimpleName(Signature.toCharArray(typeSignature));

		// XXX see https://bugs.eclipse.org/bugs/show_bug.cgi?id=84675
		boolean useShortGenerics = false;
		if (useShortGenerics) {
			StringBuilder buf = new StringBuilder();
			buf.append(displayName);
			int pos;
			do {
				pos = buf.indexOf("? extends "); //$NON-NLS-1$
				if (pos >= 0) {
					buf.replace(pos, pos + 10, "+"); //$NON-NLS-1$
				} else {
					pos = buf.indexOf("? super "); //$NON-NLS-1$
					if (pos >= 0) {
						buf.replace(pos, pos + 8, "-"); //$NON-NLS-1$
					}
				}
			} while (pos >= 0);
			return buf.toString().toCharArray();
		}
		return displayName;
	}

	@Override
	public boolean isIgnored(int completionProposalKind) {
		return completionProposalKind != CompletionProposal.METHOD_REF;
	}
}