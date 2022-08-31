/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.SignatureHelpRequestor;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

public class SignatureHelpUtils {
	private SignatureHelpUtils() {}

	/**
	 * Try to get signature help from the AST node. According to how the user code looks like,
	 * there is possibility that we can not get a valid AST node. In that case, <code>null</code>
	 * will returned.
	 * @param unit compilation unit
	 * @param triggerOffset offset where signature help is triggered
	 * @param monitor the progress monitor
	 */
	public static SignatureHelp getSignatureHelpFromASTNode(ICompilationUnit unit, int triggerOffset, IProgressMonitor monitor) {
		try {
			SignatureHelpContext context = new SignatureHelpContext();
			context.resolve(triggerOffset, unit, monitor);
			SignatureHelp help = new SignatureHelp();
			ASTNode targetNode = context.targetNode();
			if (context.targetNode() == null) {
				return null;
			}

			if (context.arguments() != null && context.arguments().isEmpty()) {
				int nodeEnd = targetNode.getStartPosition() + targetNode.getLength();
				if (unit.getBuffer().getChar(nodeEnd - 1) == ')' && nodeEnd <= triggerOffset) {
					return help;
				}
			} else if (context.argumentRanges() != null && context.argumentRanges().size() > 0) {
				// we use argument ranges (parsed from user's code) to check the offset, because
				// for code like 'foo(1, );', the AST parsed from JDT might think it only has one
				// argument.
				int[] lastRange = context.argumentRanges().get(context.argumentRanges().size() - 1);
				if (lastRange[1] < triggerOffset) {
					return help;
				}
			}

			SignatureHelpRequestor collector = new SignatureHelpRequestor(unit, context.methodName(), context.declaringTypeNames());
			unit.codeComplete(context.completionOffset(), collector, monitor);
			help = collector.getSignatureHelp(monitor);
			if (help.getSignatures().isEmpty() && context.secondaryCompletionOffset() > -1) {
				unit.codeComplete(context.secondaryCompletionOffset(), collector, monitor);
				help = collector.getSignatureHelp(monitor);
			}
			if (help.getSignatures().isEmpty() && (context.targetNode() instanceof ClassInstanceCreation)) {
				fix2097(help, context.targetNode(), collector, context.completionOffset());
			}
			List<SignatureInformation> infos = help.getSignatures();
			if (infos.isEmpty()) {
				return help;
			}
			for (int i = 0; i < infos.size(); i++) {
				SignatureInformation signatureInformation = infos.get(i);
				CompletionProposal proposal = collector.getInfoProposals().get(signatureInformation);
				boolean isMatched = isMatched(proposal, signatureInformation, context);
				if (isMatched) {
					help.setActiveSignature(i);
					int activeParameter = getActiveParameter(triggerOffset, proposal , context);
					help.setActiveParameter(activeParameter);
					break;
				}
			}
			return help;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return null;
	}

	/**
	 * Check if the completion proposal matches the user's actual code.
	 * @param proposal
	 * @param information
	 * @param context
	 */
	private static boolean isMatched(CompletionProposal proposal, SignatureInformation information, SignatureHelpContext context) {
		boolean isVarargs = Flags.isVarargs(proposal.getFlags());
		if (information.getParameters().size() < context.argumentRanges().size() && !isVarargs) {
			return false;
		}
		String[] parameterTypes = Signature.getParameterTypes(String.valueOf(proposal.getSignature()));
		
		// since the signature information are sorted by the parameter numbers, if the user's code does not
		// contain argument right now, we can say this is a match.
		if (context.arguments().size() == 0) {
			return true;
		}

		int paramNum = 0;
		if (context.parameterTypes() != null) {
			paramNum = context.parameterTypes().length;
		} else if (context.parameterTypesFromBinding() != null) {
			paramNum = context.parameterTypesFromBinding().length;
		}
		int matchedNumber = 0;
		int startIndex = 0;
		for (int i = 0; i < context.arguments().size() && i < paramNum; i++) {
			int j = startIndex;
			// find out the current resolved argument belongs to which parameter. For example, a code written as
			// 'foo(, bar)' will only contains one argument from the AST, but the variable 'bar' should be compared
			// with the second parameter.
			for (; j < context.argumentRanges().size(); j++) {
				int startPosition = context.arguments().get(i).getStartPosition();
				if (startPosition >= context.argumentRanges().get(j)[0] && startPosition <= context.argumentRanges().get(j)[1]) {
					startIndex = j + 1;
					break;
				}
			}

			if (j >= parameterTypes.length) {
				break;
			}

			String proposedTypeSimpleName = getSimpleTypeName(parameterTypes[j]);
			if (context.parameterTypes() != null) {
				if (Objects.equals(proposedTypeSimpleName, context.parameterTypes()[i])) {
					matchedNumber++;
					continue;
				}
			}

			if (context.parameterTypesFromBinding() != null) {
				if (Objects.equals(proposedTypeSimpleName, context.parameterTypesFromBinding()[i])) {
					matchedNumber++;
					continue;
				}
			}
		}
		// if the matched number equals to the resolved parameters, then we say this is a match signature.
		return matchedNumber == Math.min(paramNum, context.arguments().size());
	}

	/**
	 * Try to find the active parameter index from the input signature.
	 * @param triggerOffset offset where the signature help is triggered
	 * @param proposal completion proposal
	 * @param context signature help context
	 */
	private static int getActiveParameter(int triggerOffset, CompletionProposal proposal, SignatureHelpContext context) {
		if (triggerOffset >= context.completionOffset()) {
			boolean isVarargs = Flags.isVarargs(proposal.getFlags());
			String[] parameterTypes = Signature.getParameterTypes(String.valueOf(proposal.getSignature()));
			// when no argument is written yet but the method has at least one parameter,
			// return 0 as the active parameter index.
			if (parameterTypes.length > 0 && context.argumentRanges().size() == 0) {
				return 0;
			}
			for (int i = 0; i < context.argumentRanges().size(); i++) {
				int[] range = context.argumentRanges().get(i);
				if (range[0] <= triggerOffset && range[1] >= triggerOffset) {
					if (i >= parameterTypes.length && isVarargs) {
						return parameterTypes.length - 1;
					}
					return i;
				}
			}
		}

		return -1;
	}

	/**
	 * Return the simple type name of the given type signature, which the generic
	 * type information will be removed if it has.
	 * @param signature the signature string.
	 */
	public static String getSimpleTypeName(String signature) {
		String res = Signature.getSimpleName(Signature.toString(signature));
		return res.replaceAll("<.*>", "").replace(";", "");
	}

	// https://github.com/redhat-developer/vscode-java/issues/2097
	public static void fix2097(SignatureHelp help, ASTNode node, SignatureHelpRequestor collector, int pos) throws JavaModelException {
		IMethodBinding binding = ((ClassInstanceCreation) node).resolveConstructorBinding();
		if (binding == null) {
			return;
		}

		ITypeBinding typeBinding = binding.getDeclaringClass();
		if (typeBinding.isAnonymous()) {
			return;
		}

		if (binding.isDefaultConstructor()) {
			InternalCompletionProposal proposal = new ConstructorProposal(CompletionProposal.METHOD_REF, pos);
			proposal.setName(binding.getName().toCharArray());
			String signature = "()V";
			proposal.setSignature(signature.toCharArray());
			proposal.setParameterNames(new char[0][]);
			char[] result = Signature.createTypeSignature(typeBinding.getQualifiedName(), false).toCharArray();
			proposal.setDeclarationSignature(result);
			SignatureInformation info = collector.toSignatureInformation(proposal);
			collector.getInfoProposals().put(info, proposal);
			help.getSignatures().add(info);
			return;
		}

		IType type = (IType) typeBinding.getJavaElement();
		if (type == null) {
			return;
		}
		
		IMethod[] methods = type.getMethods();
		List<SignatureInformation> infos = new ArrayList<>();
		for (IMethod method : methods) {
			try {
				if (method.isConstructor()) {
					InternalCompletionProposal proposal = new ConstructorProposal(CompletionProposal.METHOD_REF, pos);
					proposal.setName(method.getElementName().toCharArray());
					String signature = method.getSignature().replace("/", ".");
					proposal.setSignature(signature.toCharArray());
					char[][] parameterNames = new char[method.getParameterNames().length][];
					for (int i = 0; i < method.getParameterNames().length; i++) {
						parameterNames[i] = method.getParameterNames()[i].toCharArray();
					}
					proposal.setParameterNames(parameterNames);
					char[] result = Signature.createTypeSignature(typeBinding.getQualifiedName(), false).toCharArray();
					proposal.setDeclarationSignature(result);
					SignatureInformation info = collector.toSignatureInformation(proposal);
					infos.add(info);
					collector.getInfoProposals().put(info, proposal);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		infos.sort((SignatureInformation a, SignatureInformation b) -> a.getParameters().size() - b.getParameters().size());
		help.getSignatures().addAll(infos);
	}

	static class ConstructorProposal extends InternalCompletionProposal {

		public ConstructorProposal(int kind, int completionLocation) {
			super(kind, completionLocation);
			setIsContructor(true);
		}
	}
}
