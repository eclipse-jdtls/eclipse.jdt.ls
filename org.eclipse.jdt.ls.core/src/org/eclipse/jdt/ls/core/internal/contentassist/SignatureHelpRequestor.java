/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.eclipse.jdt.internal.corext.template.java.SignatureUtil.fix83600;
import static org.eclipse.jdt.internal.corext.template.java.SignatureUtil.getLowerBound;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;
import org.eclipse.jdt.internal.codeassist.impl.Engine;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.SignatureHelpUtils;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

public final class SignatureHelpRequestor extends CompletionRequestor {

	private List<CompletionProposal> proposals = new ArrayList<>();
	private List<CompletionProposal> typeProposals = new ArrayList<>();
	private final ICompilationUnit unit;
	private CompletionProposalDescriptionProvider descriptionProvider;
	private Map<SignatureInformation, CompletionProposal> infoProposals;
	private boolean acceptType = false;
	private String methodName;
	private boolean isDescriptionEnabled;
	private List<String> declaringTypeNames;

	public SignatureHelpRequestor(ICompilationUnit aUnit, String methodName, List<String> declaringTypeName) {
		this(aUnit, methodName, declaringTypeName, false);
	}

	public SignatureHelpRequestor(ICompilationUnit aUnit, String methodName, List<String> declaringTypeName, boolean acceptType) {
		this.unit = aUnit;
		setRequireExtendedContext(true);
		infoProposals = new HashMap<>();
		this.acceptType = acceptType;
		this.methodName = methodName;
		this.isDescriptionEnabled = isDescriptionEnabled();
		this.declaringTypeNames = declaringTypeName;
	}

	public SignatureHelp getSignatureHelp(IProgressMonitor monitor) {
		SignatureHelp signatureHelp = new SignatureHelp();

		List<SignatureInformation> infos = new ArrayList<>();
		for (int i = 0; i < proposals.size(); i++) {
			if (!monitor.isCanceled()) {
				CompletionProposal proposal = proposals.get(i);
				if (proposal.getKind() != CompletionProposal.METHOD_REF) {
					typeProposals.add(proposal);
					continue;
				}
				SignatureInformation signatureInformation = this.toSignatureInformation(proposal);
				infoProposals.put(signatureInformation, proposal);
				infos.add(signatureInformation);
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
		if (acceptType) {
			return completionProposalKind != CompletionProposal.METHOD_REF && completionProposalKind != CompletionProposal.TYPE_REF;
		} else {
			return completionProposalKind != CompletionProposal.METHOD_REF;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.CompletionRequestor#accept(org.eclipse.jdt.core.CompletionProposal)
	 */
	@Override
	public void accept(CompletionProposal proposal) {
		if (!isIgnored(proposal.getKind())) {
			if (proposal.getKind() == CompletionProposal.METHOD_REF && !Objects.equals(proposal.getName() == null ? null : new String(proposal.getName()), methodName)) {
				return;
			}
			if (this.declaringTypeNames != null) {
				char[] declarationSignature = proposal.getDeclarationSignature();
				if (declarationSignature != null) {
					String proposalTypeSimpleName = SignatureHelpUtils.getSimpleTypeName(String.valueOf(declarationSignature));
					for (String typeName : this.declaringTypeNames) {
						String declaringTypeSimpleName = Signature.getSimpleName(typeName);
						if (Objects.equals(proposalTypeSimpleName, declaringTypeSimpleName)) {
							proposals.add(proposal);
							return;
						}
					}
					return;
				}
			}
			proposals.add(proposal);
		}
	}

	@Override
	public void acceptContext(CompletionContext context) {
		super.acceptContext(context);
		this.descriptionProvider = new CompletionProposalDescriptionProvider(unit, context);
	}

	public SignatureInformation toSignatureInformation(CompletionProposal methodProposal) {
		SignatureInformation $ = new SignatureInformation();
		StringBuilder description = descriptionProvider.createMethodProposalDescription(methodProposal);
		$.setLabel(description.toString());
		if (isDescriptionEnabled) {
			$.setDocumentation(this.computeJavaDoc(methodProposal));
		}

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

			parameterInfos.add(new ParameterInformation(builder.toString()));
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
			String fullyQualifiedName = SignatureUtil.stripSignatureToFQN(String.valueOf(proposal.getDeclarationSignature()));
			IType type = unit.getJavaProject().findType(fullyQualifiedName);
			if (type == null) {
				// find secondary types if primary type search is missed.
				type = unit.getJavaProject().findType(fullyQualifiedName, new NullProgressMonitor());
			}
			if (type != null) {
				if (proposal instanceof InternalCompletionProposal internalCompletionProposal) {
					Binding binding = internalCompletionProposal.getBinding();
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
						IMethod method = JavaModelUtil.findMethod(String.valueOf(proposal.getName()), parameters, proposal.isConstructor(), type);
						if (method != null && method.exists()) {
							ICompilationUnit unit = type.getCompilationUnit();
							if (unit != null) {
								unit.reconcile(ICompilationUnit.NO_AST, false, null, null);
							}
							String javadoc = null;
							try {
								javadoc = SimpleTimeLimiter.create(JavaLanguageServerPlugin.getExecutorService()).callWithTimeout(() -> {
									Reader reader = JavadocContentAccess.getPlainTextContentReader(method);
									return reader == null ? null : CharStreams.toString(reader);
								}, 500, TimeUnit.MILLISECONDS);
							} catch (UncheckedTimeoutException tooSlow) {
							} catch (Exception e) {
								JavaLanguageServerPlugin.logException("Unable to read documentation", e);
							}
							return javadoc;
						}
					}
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Unable to resolve signaturehelp javadoc", e);
		}
		return null;
	}

	public Map<SignatureInformation, CompletionProposal> getInfoProposals() {
		return infoProposals;
	}

	public List<CompletionProposal> getTypeProposals() {
		return typeProposals;
	}

	private boolean isDescriptionEnabled() {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null) {
			return false;
		}

		Preferences preferences = preferencesManager.getPreferences();
		if (preferences == null) {
			return false;
		}

		return preferences.isSignatureHelpDescriptionEnabled();
	}

}
