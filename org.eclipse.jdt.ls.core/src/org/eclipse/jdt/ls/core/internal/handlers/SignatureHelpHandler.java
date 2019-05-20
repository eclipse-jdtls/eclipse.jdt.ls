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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.SignatureHelpRequestor;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class SignatureHelpHandler {

	public static SignatureHelpOptions createOptions() {
		return new SignatureHelpOptions(Arrays.asList("("));
	}

	private static final int SEARCH_BOUND = 2000;

	private PreferenceManager preferenceManager;

	public SignatureHelpHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public SignatureHelp signatureHelp(TextDocumentPositionParams position, IProgressMonitor monitor) {

		SignatureHelp help = new SignatureHelp();

		if (!preferenceManager.getPreferences(null).isSignatureHelpEnabled()) {
			return help;
		}

		try {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri());
			final int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), position.getPosition().getLine(), position.getPosition().getCharacter());
			int[] contextInfomation = getContextInfomation(unit.getBuffer(), offset);
			ASTNode node = getNode(unit, contextInfomation, monitor);
			if (node == null) {
				return help;
			}
			SignatureHelpRequestor collector = new SignatureHelpRequestor(unit, contextInfomation[0] + 1);
			if (offset > -1 && !monitor.isCanceled()) {
				unit.codeComplete(contextInfomation[0] + 1, collector, monitor);
				help = collector.getSignatureHelp(monitor);
				if (help != null && help.getSignatures().size() > 0) {
					int size = getArgumentsSize(node);
					int currentParameter = contextInfomation[1];
					char[] signature = getSignature(node);
					size = Math.max(currentParameter + 1, size);
					List<SignatureInformation> infos = help.getSignatures();
					if (signature != null) {
						for (int i = 0; i < infos.size(); i++) {
							if (infos.get(i).getParameters().size() >= size) {
								CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
								char[][] signatureTypes = Signature.getParameterTypes(signature);
								char[][] infoTypes = Signature.getParameterTypes(SignatureUtil.fix83600(proposal.getSignature()));
								if (Arrays.deepEquals(signatureTypes, infoTypes)) {
									help.setActiveSignature(i);
									help.setActiveParameter(currentParameter < 0 ? 0 : currentParameter);
								}
								if (size > 0) {
									if (infoTypes.length - 1 == signatureTypes.length) {
										infoTypes = arrayClone(infoTypes, infoTypes.length - 1);
									}
									if (Arrays.deepEquals(signatureTypes, infoTypes)) {
										help.setActiveSignature(i);
										help.setActiveParameter(currentParameter < 0 ? 0 : currentParameter);
										break;
									}
								}
							}
						}
					} else {
						for (int i = 0; i < infos.size(); i++) {
							if (infos.get(i).getParameters().size() >= size) {
								help.setActiveSignature(i);
								help.setActiveParameter(currentParameter < 0 ? 0 : currentParameter);
								break;
							}
						}
					}
				}
			}
		} catch (CoreException ex) {
			JavaLanguageServerPlugin.logException("Find signatureHelp failure ", ex);
		}
		return help;
	}

	private static char[][] arrayClone(char[][] src, int length) {
		if (src.length < 1) {
			return src;
		}
		char[][] dest = new char[src.length - 1][];
		for (int i = 0; i < src.length - 1; i++) {
			dest[i] = src[i].clone();
		}
		return dest;
	}

	private char[] getSignature(ASTNode node) throws JavaModelException {
		IBinding binding;
		if (node instanceof MethodInvocation) {
			binding = ((MethodInvocation) node).resolveMethodBinding();
		} else if (node instanceof MethodRef) {
			binding = ((MethodRef) node).resolveBinding();
		} else if (node instanceof ClassInstanceCreation) {
			binding = ((ClassInstanceCreation) node).resolveConstructorBinding();
		} else {
			binding = null;
		}
		if (binding != null) {
			IJavaElement javaElement = binding.getJavaElement();
			if (javaElement instanceof IMethod) {
				IMethod method = (IMethod) javaElement;
				String signature = resolveMethodSignature(method);
				if (signature != null) {
					return signature.replaceAll("/", ".").toCharArray();
				} else {
					return method.getSignature().replaceAll("/", ".").toCharArray();
				}
			}
		}
		return null;
	}

	// Code copied from org.eclipse.jdt.internal.debug.ui.actions.ToggleBreakpointAdapter.resolveMethodSignature(IMethod)
	private static String resolveMethodSignature(IMethod method) throws JavaModelException {
		String signature = method.getSignature();
		String[] parameterTypes = Signature.getParameterTypes(signature);
		int length = parameterTypes.length;
		String[] resolvedParameterTypes = new String[length];
		for (int i = 0; i < length; i++) {
			resolvedParameterTypes[i] = resolveTypeSignature(method, parameterTypes[i]);
			if (resolvedParameterTypes[i] == null) {
				resolvedParameterTypes[i] = resolveTypeSignature(method, parameterTypes[i].replaceAll("/", "."));
				if (resolvedParameterTypes[i] == null) {
					return null;
				}
			}
		}
		String resolvedReturnType = resolveTypeSignature(method, Signature.getReturnType(signature));
		if (resolvedReturnType == null) {
			return null;
		}
		return Signature.createMethodSignature(resolvedParameterTypes, resolvedReturnType);
	}

	// Code copied from org.eclipse.jdt.internal.debug.ui.actions.ToggleBreakpointAdapter.resolveTypeSignature(IMethod, String)
	private static String resolveTypeSignature(IMethod method, String typeSignature) throws JavaModelException {
		int count = Signature.getArrayCount(typeSignature);
		String elementTypeSignature = Signature.getElementType(typeSignature);
		if (elementTypeSignature.length() == 1) {
			// no need to resolve primitive types
			return typeSignature;
		}
		String elementTypeName = Signature.toString(elementTypeSignature);
		IType type = method.getDeclaringType();
		String[][] resolvedElementTypeNames = type.resolveType(elementTypeName);
		if (resolvedElementTypeNames == null || resolvedElementTypeNames.length != 1) {
			// check if type parameter
			ITypeParameter typeParameter = method.getTypeParameter(elementTypeName);
			if (!typeParameter.exists()) {
				typeParameter = type.getTypeParameter(elementTypeName);
			}
			if (typeParameter.exists()) {
				String[] bounds = typeParameter.getBounds();
				if (bounds.length == 0) {
					return "Ljava/lang/Object;"; //$NON-NLS-1$
				}
				String bound = Signature.createTypeSignature(bounds[0], false);
				return Signature.createArraySignature(resolveTypeSignature(method, bound), count);
			}
			// the type name cannot be resolved
			return null;
		}

		String[] types = resolvedElementTypeNames[0];
		types[1] = types[1].replace('.', '$');

		String resolvedElementTypeName = Signature.toQualifiedName(types);
		String resolvedElementTypeSignature = "";
		if (types[0].equals("")) {
			resolvedElementTypeName = resolvedElementTypeName.substring(1);
			resolvedElementTypeSignature = Signature.createTypeSignature(resolvedElementTypeName, true);
		} else {
			resolvedElementTypeSignature = Signature.createTypeSignature(resolvedElementTypeName, true).replace('.', '/');
		}

		return Signature.createArraySignature(resolvedElementTypeSignature, count);
	}
	private int getArgumentsSize(ASTNode node) {
		if (node instanceof MethodInvocation) {
			return ((MethodInvocation) node).arguments().size();
		} else if (node instanceof MethodRef) {
			return ((MethodRef) node).parameters().size();
		} else if (node instanceof ClassInstanceCreation) {
			return ((ClassInstanceCreation) node).arguments().size();
		}
		return -1;
	}

	private ASTNode getNode(ICompilationUnit unit, int[] contextInfomation, IProgressMonitor monitor) {
		if (contextInfomation[0] != -1) {
			CompilationUnit ast = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
			ASTNode node = NodeFinder.perform(ast, contextInfomation[0], 1);
			if (node instanceof MethodInvocation || node instanceof ClassInstanceCreation || node instanceof MethodRef || (contextInfomation[1] > 0 && node instanceof Block)) {
				return node;
			}
		}
		return null;
	}

	/*
	 * Calculate the heuristic information about the start offset of method and current parameter position. The parameter position is 0-based.
	 * If cannot find the methods start offset after max search bound, -1 will be return.
	 *
	 * @return array of 2 integer2: the first one is starting offset of method and the second one is the current parameter position.
	 */
	private int[] getContextInfomation(IBuffer buffer, int offset) {
		int[] result = new int[2];
		result[0] = result[1] = -1;
		int depth = 1;

		for (int i = offset - 1; i >= 0 && ((offset - i) < SEARCH_BOUND); i--) {
			char c = buffer.getChar(i);
			if (c == ')') {
				depth++;
			}
			if (c == '(') {
				depth--;
			}
			if (c == ',' && depth == 1) {
				result[1]++;
			}
			if (depth == 0) {
				result[0] = i;
				break;
			}
		}
		// Assuming user are typing current parameter:
		if (result[0] + 1 != offset) {
			result[1]++;
		}
		return result;
	}
}
