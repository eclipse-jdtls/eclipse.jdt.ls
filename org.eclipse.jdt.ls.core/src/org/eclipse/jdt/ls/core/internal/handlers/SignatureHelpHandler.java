/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
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
				IMethod method = getMethod(node);
				if (help.getSignatures().isEmpty()) {
					int pos = offset;
					if (method != null) {
						int start = node.getStartPosition();
						pos = start + method.getElementName().length();
					}
					unit.codeComplete(pos, collector, monitor);
					help = collector.getSignatureHelp(monitor);
				}
				if (!monitor.isCanceled() && help != null) {
					SignatureHelp help2 = null;
					SignatureHelpRequestor collector2 = null;
					if (contextInfomation[0] + 1 != offset) {
						collector2 = new SignatureHelpRequestor(unit, offset);
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
										IMethod m = JDTUtils.resolveMethod(proposal, javaProject);
										if (JDTUtils.isSameParameters(m, method)) {
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
										IMethod m = JDTUtils.resolveMethod(proposal, javaProject);
										if (isSameParameters(m, help2, collector2, javaProject)) {
											help.setActiveSignature(i);
											help.setActiveParameter(activeParameter);
											return help;
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
										IMethod m = JDTUtils.resolveMethod(proposal, javaProject);
										if (JDTUtils.isSameParameters(method, m)) {
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
							for (int i = 0; i < infos.size(); i++) {
								if (infos.get(i).getParameters().size() >= activeParameter) {
									help.setActiveSignature(i);
									help.setActiveParameter(activeParameter);
									return help;
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

	private boolean isSameParameters(IMethod m, SignatureHelp help, SignatureHelpRequestor collector, IJavaProject javaProject) throws JavaModelException {
		if (m == null || help == null || javaProject == null) {
			return false;
		}
		List<SignatureInformation> infos = help.getSignatures();
		for (int i = 0; i < infos.size(); i++) {
			CompletionProposal proposal = collector.getInfoProposals().get(infos.get(i));
			IMethod method = JDTUtils.resolveMethod(proposal, javaProject);
			if (JDTUtils.isSameParameters(method, m)) {
				return true;
			}
		}
		return false;
	}

	private IMethod getMethod(ASTNode node) throws JavaModelException {
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
						return node;
					}
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
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
