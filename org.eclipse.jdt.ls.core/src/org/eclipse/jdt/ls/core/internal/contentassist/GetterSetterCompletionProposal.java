/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.java.GetterSetterCompletionProposal
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;

/**
 * Generates GetterSetter completion proposals.
 */
public class GetterSetterCompletionProposal extends InternalCompletionProposal {

	private IField fField;

	private boolean fIsGetter;

	public static void evaluateProposals(IType type, String prefix, int offset, int length, int relevance, Collection<CompletionProposal> result) throws JavaModelException {
		if (prefix.length() == 0) {
			relevance--;
		}

		IField[] fields= type.getFields();
		IMethod[] methods= type.getMethods();
		for (IField curr : fields) {
			if (!JdtFlags.isEnum(curr)) {
				String getterName = GetterSetterUtil.getGetterName(curr, null);
				if (Strings.startsWithIgnoreCase(getterName, prefix) && !hasMethod(methods, getterName)) {
					int getterRelevance= relevance;
					if (JdtFlags.isStatic(curr) && JdtFlags.isFinal(curr)) {
						getterRelevance= relevance - 1;
					}
					CompletionProposal proposal = new GetterSetterCompletionProposal(curr, true, offset);
					proposal.setName(getterName.toCharArray());

					String signature = Signature.createMethodSignature(new String[] {}, curr.getTypeSignature());
					proposal.setReplaceRange(offset, offset + prefix.length());
					proposal.setSignature(signature.toCharArray());
					proposal.setCompletion(getterName.toCharArray());
					proposal.setDeclarationSignature(curr.getTypeSignature().toCharArray());
					result.add(proposal);
				}

				if (!JdtFlags.isFinal(curr)) {
					String setterName= GetterSetterUtil.getSetterName(curr, null);
					if (Strings.startsWithIgnoreCase(setterName, prefix) && !hasMethod(methods, setterName)) {
						CompletionProposal proposal = new GetterSetterCompletionProposal(curr, false, offset);
						proposal.setName(setterName.toCharArray());

						String signature = Signature.createMethodSignature(new String[] { curr.getTypeSignature() }, Signature.SIG_VOID);
						proposal.setReplaceRange(offset, offset + prefix.length());
						proposal.setSignature(signature.toCharArray());
						proposal.setParameterNames(new char[][] { curr.getElementName().toCharArray() });
						proposal.setCompletion(getterName.toCharArray());
						proposal.setDeclarationSignature(curr.getTypeSignature().toCharArray());
						result.add(proposal);
					}
				}
			}
		}
	}

	private static boolean hasMethod(IMethod[] methods, String name) {
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].getElementName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public GetterSetterCompletionProposal(IField field, boolean isGetter, int offset) {
		super(CompletionProposal.POTENTIAL_METHOD_DECLARATION, offset);
		fField = field;
		fIsGetter = isGetter;
	}

	/**
	 * @param document
	 * @param offset
	 * @param importRewrite
	 * @param completionSnippetsSupported
	 * @param addComments
	 * @return
	 * @throws CoreException
	 * @throws BadLocationException
	 */
	public String updateReplacementString(IDocument document, int offset, ImportRewrite importRewrite, boolean completionSnippetsSupported, boolean addComments) throws CoreException, BadLocationException {
		int flags= Flags.AccPublic | (fField.getFlags() & Flags.AccStatic);
		String stub;
		if (fIsGetter) {
			String getterName= GetterSetterUtil.getGetterName(fField, null);
			stub = GetterSetterUtil.getGetterStub(fField, getterName, addComments, flags);
		} else {
			String setterName= GetterSetterUtil.getSetterName(fField, null);
			stub = GetterSetterUtil.getSetterStub(fField, setterName, addComments, flags);
		}

		// use the code formatter
		String lineDelim= TextUtilities.getDefaultLineDelimiter(document);
		String replacement = CodeFormatterUtil.format(CodeFormatter.K_CLASS_BODY_DECLARATIONS, stub, 0, lineDelim, fField.getJavaProject());

		if (replacement.endsWith(lineDelim)) {
			replacement = replacement.substring(0, replacement.length() - lineDelim.length());
		}

		return replacement;
	}
}
