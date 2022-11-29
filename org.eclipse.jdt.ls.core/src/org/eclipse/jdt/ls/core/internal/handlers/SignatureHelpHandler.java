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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.SignatureHelpRequestor;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;

public class SignatureHelpHandler {

	public static SignatureHelpOptions createOptions() {
		return new SignatureHelpOptions(Arrays.asList("(", ","));
	}

	private static final int SEARCH_BOUND = 2000;

	private PreferenceManager preferenceManager;

	public SignatureHelpHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public SignatureHelp signatureHelp(SignatureHelpParams position, IProgressMonitor monitor) {

		SignatureHelp help = new SignatureHelp();

		if (!preferenceManager.getPreferences().isSignatureHelpEnabled()) {
			return help;
		}
		try {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri());
			if (unit == null) {
				return help;
			}
			final int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), position.getPosition().getLine(), position.getPosition().getCharacter());
			SignatureHelp helpFromASTNode = SignatureHelpUtils.getSignatureHelpFromASTNode(unit, offset, monitor);
			if (helpFromASTNode != null) {
				return helpFromASTNode;
			}

			if (monitor.isCanceled()) {
				return help;
			}

			int[] contextInfomation = getContextInfomation(unit.getBuffer(), offset);
			ASTNode node = getNode(unit, contextInfomation, monitor);
			if (node == null) {
				return help;
			}
			IMethod method = getMethod(node);
			String name = method != null ? method.getElementName() : getMethodName(node, unit, contextInfomation);
			SignatureHelpRequestor collector = new SignatureHelpRequestor(unit, name, null);
			if (offset > -1 && !monitor.isCanceled()) {
				int pos = contextInfomation[0] + 1;
				if (method != null) {
					int start;
					if (node instanceof MethodInvocation methodInvocation) {
						start = methodInvocation.getName().getStartPosition();
					} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
						start = classInstanceCreation.getType().getStartPosition();
					} else {
						start = node.getStartPosition();
					}
					pos = start + method.getElementName().length();
				}
				unit.codeComplete(pos, collector, monitor);
				help = collector.getSignatureHelp(monitor);
				if (method != null && method.isConstructor() && help.getSignatures().isEmpty() && (node instanceof ClassInstanceCreation)) {
					SignatureHelpUtils.fix2097(help, node, collector, pos);
				}
				if (!monitor.isCanceled() && help != null) {
					SignatureHelp help2 = null;
					SignatureHelpRequestor collector2 = null;
					if (contextInfomation[0] + 1 != offset) {
						collector2 = new SignatureHelpRequestor(unit, name, null, true);
						unit.codeComplete(offset, collector2, monitor);
						help2 = collector2.getSignatureHelp(monitor);
					}
					int currentParameter = contextInfomation[1];
					int size = currentParameter + 1;
					List<SignatureInformation> infos = help.getSignatures();
					int activeParameter = currentParameter < 0 ? 0 : currentParameter;
					if (node != null) {
						IJavaProject javaProject = unit.getJavaProject();
						if (help2 != null) {
							if (method != null) {
								for (int i = 0; i < infos.size(); i++) {
									if (infos.get(i).getParameters().size() >= size) {
										CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
										IMethod m = JDTUtils.resolveMethod(proposal, javaProject, monitor);
										if (!monitor.isCanceled() && JDTUtils.isSameParameters(m, method)) {
											help.setActiveSignature(i);
											help.setActiveParameter(activeParameter);
											return help;
										}
									}
								}
							}
							if (!monitor.isCanceled() && help.getActiveSignature() == null) {
								for (int i = 0; i < infos.size(); i++) {
									if (infos.get(i).getParameters().size() >= size) {
										CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
										IMethod m = JDTUtils.resolveMethod(proposal, javaProject, monitor);
										if (!monitor.isCanceled() && isSameParameters(m, help2, collector2, javaProject, monitor)) {
											help.setActiveSignature(i);
											help.setActiveParameter(activeParameter);
											return help;
										}
										for (CompletionProposal typeProposal : collector2.getTypeProposals()) {
											if (isSameParameters(m, method, typeProposal)) {
												help.setActiveSignature(i);
												help.setActiveParameter(activeParameter);
												return help;
											}
										}
									}
								}
							}
						}
						if (!monitor.isCanceled() && help.getActiveSignature() == null) {
							if (method != null) {
								for (int i = 0; i < infos.size(); i++) {
									if (infos.get(i).getParameters().size() >= size) {
										CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
										IMethod m = JDTUtils.resolveMethod(proposal, javaProject, monitor);
										if (!monitor.isCanceled() && JDTUtils.isSameParameters(method, m)) {
											help.setActiveSignature(i);
											help.setActiveParameter(activeParameter);
											return help;
										}
									}
								}
							}
						}
						if (!monitor.isCanceled() && help.getActiveSignature() == null) {
							for (int i = 0; i < infos.size(); i++) {
								CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
								if (Flags.isVarargs(proposal.getFlags())) {
									help.setActiveSignature(i);
									char[][] infoTypes = Signature.getParameterTypes(SignatureUtil.fix83600(proposal.getSignature()));
									if (infoTypes.length <= activeParameter) {
										help.setActiveParameter(infoTypes.length - 1);
									} else {
										help.setActiveParameter(activeParameter);
									}
									return help;
								}
							}
						}
						if (!monitor.isCanceled() && help.getActiveSignature() == null && node instanceof Block) {
							String methodName = getMethodName(node, unit, contextInfomation);
							for (int i = 0; i < infos.size(); i++) {
								if (infos.get(i).getParameters().size() >= activeParameter) {
									CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
									IMethod m = JDTUtils.resolveMethod(proposal, javaProject, monitor);
									if (!monitor.isCanceled() && m != null && m.getElementName().equals(methodName)) {
										help.setActiveSignature(i);
										help.setActiveParameter(activeParameter);
										return help;
									}
								}
							}
						}
						if (method != null && !monitor.isCanceled() && help.getActiveSignature() == null) {
							for (int i = 0; i < infos.size(); i++) {
								if (infos.get(i).getParameters().size() >= size) {
									CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
									IMethod m = JDTUtils.resolveMethod(proposal, javaProject, monitor);
									if (!monitor.isCanceled() && isSameParameters(m, method, null)) {
										help.setActiveSignature(i);
										help.setActiveParameter(activeParameter);
										return help;
									}
								}
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

	private boolean isSameParameters(IMethod m, SignatureHelp help, SignatureHelpRequestor collector, IJavaProject javaProject, IProgressMonitor monitor) throws JavaModelException {
		if (m == null || help == null || javaProject == null) {
			return false;
		}
		List<SignatureInformation> infos = help.getSignatures();
		for (int i = 0; i < infos.size(); i++) {
			CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
			IMethod method = JDTUtils.resolveMethod(proposal, javaProject, monitor);
			if (m.getElementName().equals(method.getElementName()) && JDTUtils.isSameParameters(method, m)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSameParameters(IMethod method1, IMethod method2, CompletionProposal typeProposal) {
		if (method1 == null || method2 == null || !method1.getElementName().equals(method2.getElementName())) {
			return false;
		}
		String[] params1 = method1.getParameterTypes();
		String[] params2 = method2.getParameterTypes();
		if (params2.length <= params1.length - 1) {
			for (int i = 0; i < params2.length; i++) {
				String t1 = Signature.getSimpleName(Signature.toString(params2[i]));
				String t2 = Signature.getSimpleName(Signature.toString(params1[i]));
				if (!t1.equals(t2)) {
					return false;
				}
			}
		}
		if (typeProposal != null) {
			String param = params1[params1.length - 1];
			String typeSignature = new String(SignatureUtil.fix83600(typeProposal.getSignature()));
			return param.equals(typeSignature);
		}
		return true;
	}

	private IMethod getMethod(ASTNode node) throws JavaModelException {
		IBinding binding;
		if (node instanceof MethodInvocation methodInvocation) {
			binding = methodInvocation.resolveMethodBinding();
		} else if (node instanceof MethodRef methodRef) {
			binding = methodRef.resolveBinding();
		} else if (node instanceof ClassInstanceCreation classInstanceCreation) {
			binding = classInstanceCreation.resolveConstructorBinding();
		} else {
			binding = null;
		}
		if (binding != null) {
			IJavaElement javaElement = binding.getJavaElement();
			if (javaElement instanceof IMethod method) {
				return method;
			}
		}
		return null;
	}

	private ASTNode getNode(ICompilationUnit unit, int[] contextInfomation, IProgressMonitor monitor) {
		if (contextInfomation[0] != -1) {
			CompilationUnit ast = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
			ASTNode node = NodeFinder.perform(ast, contextInfomation[0], 1);
			if (node instanceof MethodInvocation || node instanceof ClassInstanceCreation || node instanceof MethodRef) {
				return node;
			}
			if (node instanceof Block) {
				String name = getMethodName(node, unit, contextInfomation);
				if (name != null) {
					return node;
				}
			}
			if (node instanceof Expression) {
				node = node.getParent();
				if (node instanceof MethodInvocation || node instanceof ClassInstanceCreation || node instanceof MethodRef) {
					return node;
				}
			}
		}
		return null;
	}

	private String getMethodName(ASTNode node, ICompilationUnit unit, int[] contextInfomation) {
		if (node instanceof Block) {
			try {
				int pos = contextInfomation[0];
				IBuffer buffer = unit.getBuffer();
				while (pos >= 0) {
					char ch = buffer.getChar(pos);
					if (ch == '(' || Character.isWhitespace(ch)) {
						pos--;
					} else {
						break;
					}
				}
				int end = pos + 1;
				while (pos >= 0) {
					char ch = buffer.getChar(pos);
					if (Character.isJavaIdentifierPart(ch)) {
						pos--;
					} else {
						break;
					}
				}
				int start = pos + 1;
				String name = unit.getSource().substring(start, end);
				IStatus status = JavaConventionsUtil.validateMethodName(name, unit);
				if (status.isOK()) {
					return name;
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
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
			if (c == '{' || c == '}') {
				result[0] = result[1] = -1;
				return result;
			}
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
			int i = 1;
			while (result[0] + i < offset) {
				if (!Character.isWhitespace(buffer.getChar(result[0] + i))) {
					result[1]++;
					break;
				}
				i++;
			}
		}
		return result;
	}
}
