/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.ExceptionInfo;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;

public class ChangeSignatureHandler {

	public static String CHANGE_SIGNATURE_ANNOTATION_ID = "java.refactor.ChangeSignature";

	public static class MethodParameter {
		public String type;
		public String name;
		public String defaultValue;
		public int originalIndex;

		public MethodParameter(String type, String name, String defaultValue, int originalIndex) {
			this.type = type;
			this.name = name;
			this.defaultValue = defaultValue;
			this.originalIndex = originalIndex;
		}
	}

	public static class MethodException {
		public String type;
		public String typeHandleIdentifier;

		public MethodException(String type, String typeHandleIdentifier) {
			this.type = type;
			this.typeHandleIdentifier = typeHandleIdentifier;
		}
	}

	public static Refactoring getChangeSignatureRefactoring(CodeActionParams params, IMethod method, boolean isDelegate, String methodName, String modifier, String returnType, List<MethodParameter> parameters,
			List<MethodException> exceptions) {
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (cu == null) {
			return null;
		}
		IType primaryType = cu.findPrimaryType();
		if (primaryType == null) {
			return null;
		}
		try {
			ChangeSignatureProcessor processor = new ChangeSignatureProcessor(method);
			processor.setNewMethodName(methodName);
			processor.setVisibility(JdtFlags.getVisibilityCode(modifier));
			processor.setNewReturnTypeName(returnType);
			processor.setDelegateUpdating(isDelegate);
			RefactoringStatus status = processor.checkInitialConditions(new NullProgressMonitor());
			if (status.hasFatalError()) {
				if (status.hasFatalError()) {
					logFatalError(status);
					return null;
				}
			}
			List<ParameterInfo> parameterInfos = processor.getParameterInfos();
			List<ParameterInfo> newParameterInfos = new ArrayList<>();
			for (MethodParameter param : parameters) {
				if (param.originalIndex != ParameterInfo.INDEX_FOR_ADDED && param.originalIndex < parameterInfos.size()) {
					ParameterInfo info = parameterInfos.get(param.originalIndex);
					info.setNewTypeName(param.type);
					info.setNewName(param.name);
					newParameterInfos.add(info);
				} else {
					newParameterInfos.add(ParameterInfo.createInfoForAddedParameter(param.type, param.name, param.defaultValue));
				}
			}
			parameterInfos.clear();
			parameterInfos.addAll(newParameterInfos);
			List<ExceptionInfo> exceptionInfos = processor.getExceptionInfos();
			List<ExceptionInfo> newExceptionInfos = new ArrayList<>();
			for (MethodException exception : exceptions) {
				if (exception.typeHandleIdentifier != null) {
					IJavaElement element = JavaCore.create(exception.typeHandleIdentifier);
					if (element instanceof IType type) {
						newExceptionInfos.add(ExceptionInfo.createInfoForAddedException(type));
					}
				} else {
					IType type = null;
					if (exception.type.equals("Exception")) {
						// special handling for java.lang.Exception
						type = cu.getJavaProject().findType("java.lang.Exception");
					}
					if (type == null) {
						// find possible types with the fully qualified name in the project
						type = cu.getJavaProject().findType(exception.type);
					}
					if (type == null) {
						// find possible match types in existing import declarations
						for (IImportDeclaration importDeclaration : cu.getImports()) {
							String importName = importDeclaration.getElementName();
							int dotIndex = importName.lastIndexOf(".");
							if (dotIndex != -1 && dotIndex < importName.length() - 1) {
								String typeName = importName.substring(dotIndex + 1);
								if (typeName.equals(exception.type)) {
									type = cu.getJavaProject().findType(importName);
									break;
								}
							}
						}
					}
					if (type == null) {
						// exception.type is not FQN, or it should be found via IJavaProject.findType()
						SearchEngine engine = new SearchEngine();
						IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[] { cu.getJavaProject() }, true);
						int qualIndex = exception.type.lastIndexOf('.');
						if (qualIndex == -1) {
							List<IType> foundTypes = new ArrayList<>();
							engine.searchAllTypeNames(null, SearchPattern.R_FULL_MATCH, exception.type.toCharArray(), SearchPattern.R_FULL_MATCH, IJavaSearchConstants.TYPE, scope, new TypeNameMatchRequestor() {
								@Override
								public void acceptTypeNameMatch(TypeNameMatch match) {
									IType type = match.getType();
									try {
										if (type.exists() && JdtFlags.isPublic(type)) {
											foundTypes.add(type);
										}
									} catch (JavaModelException e) {
										JavaLanguageServerPlugin.log(e);
									}
								}
							}, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, new NullProgressMonitor());
							if (foundTypes.size() == 1) {
								type = foundTypes.get(0);
							} else {
								JavaLanguageServerPlugin.getProjectsManager().getConnection().showMessage(new MessageParams(MessageType.Error, "Ambigious exception types are found for " + exception.type + ", please use fully qualified name instead."));
								return null;
							}
						}
					}
					if (type != null) {
						newExceptionInfos.add(ExceptionInfo.createInfoForAddedException(type));
					}
				}
			}
			exceptionInfos.clear();
			exceptionInfos.addAll(newExceptionInfos);
			Refactoring refactoring = new ProcessorBasedRefactoring(processor);
			refactoring.checkInitialConditions(new NullProgressMonitor());
			status = refactoring.checkFinalConditions(new NullProgressMonitor());
			if (status.hasFatalError()) {
				logFatalError(status);
				return null;
			}
			return refactoring;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return null;
	}

	private static void logFatalError(RefactoringStatus status) {
		String message = status.getMessageMatchingSeverity(RefactoringStatus.FATAL);
		if (message == null) {
			message = status.getMessageMatchingSeverity(RefactoringStatus.ERROR);
		}
		JavaLanguageServerPlugin.getProjectsManager().getConnection().showMessage(new MessageParams(MessageType.Error, message));
		JavaLanguageServerPlugin.logError(status.toString());
	}
}
