/*******************************************************************************
 * Copyright (c) 2016-2024 Red Hat Inc. and others.
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.refactoring.ExceptionInfo;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTesterCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.ChangeSignatureHandler.MethodException;
import org.eclipse.jdt.ls.core.internal.handlers.ChangeSignatureHandler.MethodParameter;
import org.eclipse.jdt.ls.core.internal.text.correction.ChangeSignatureInfo;
import org.eclipse.jdt.ls.core.internal.text.correction.CodeActionUtility;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class ChangeSignatureInfoHandler {

	private static final String CANNOT_CHANGE_SIGNATURE = "Cannot change signature.";

	public static ChangeSignatureInfo getChangeSignatureInfo(CodeActionParams params, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return null;
		}
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null || monitor.isCanceled()) {
			return null;
		}
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		if (astRoot == null) {
			return null;
		}
		IInvocationContext context = CodeActionHandler.getContext(unit, astRoot, params.getRange());
		ASTNode methodNode = CodeActionUtility.inferASTNode(context.getCoveringNode(), MethodDeclaration.class);
		if (methodNode == null) {
			return null;
		}
		IMethodBinding methodBinding = ((MethodDeclaration) methodNode).resolveBinding();
		if (methodBinding == null) {
			return null;
		}
		IJavaElement element = methodBinding.getJavaElement();
		if (element instanceof IMethod method) {
			try {
				ChangeSignatureProcessor processor = new ChangeSignatureProcessor(method);
				if (RefactoringAvailabilityTesterCore.isChangeSignatureAvailable(method)) {
					RefactoringStatus status = processor.checkInitialConditions(new NullProgressMonitor());
					if (status.isOK()) {
						List<MethodParameter> parameters = new ArrayList<>();
						for (ParameterInfo info : processor.getParameterInfos()) {
							parameters.add(new MethodParameter(info.getOldTypeName(), info.getOldName(), info.getDefaultValue() == null ? "null" : info.getDefaultValue(), info.getOldIndex()));
						}
						List<MethodException> exceptions = new ArrayList<>();
						for (ExceptionInfo info : processor.getExceptionInfos()) {
							exceptions.add(new MethodException(info.getFullyQualifiedName(), info.getElement().getHandleIdentifier()));
						}
						return new ChangeSignatureInfo(method.getHandleIdentifier(), JdtFlags.getVisibilityString(processor.getVisibility()), processor.getReturnTypeString(), method.getElementName(),
								parameters.toArray(MethodParameter[]::new), exceptions.toArray(MethodException[]::new));
					} else {
						return new ChangeSignatureInfo(CANNOT_CHANGE_SIGNATURE + status.getMessageMatchingSeverity(status.getSeverity()));
					}
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		return new ChangeSignatureInfo(CANNOT_CHANGE_SIGNATURE);
	}

}
