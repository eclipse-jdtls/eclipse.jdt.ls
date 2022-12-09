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

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.structure.ExtractInterfaceProcessorLS;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveDestinationsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.PackageNode;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;

public class ExtractInterfaceHandler {

	public static CheckExtractInterfaceResponse checkExtractInterfaceStatus(CodeActionParams params) {
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (cu == null) {
			return null;
		}
		IType type = cu.findPrimaryType();
		if (type == null) {
			return null;
		}
		CodeGenerationSettings settings = PreferenceManager.getCodeGenerationSettings(cu);
		ExtractInterfaceProcessorLS processor = new ExtractInterfaceProcessorLS(type, settings);
		IMember[] extractableMembers;
		try {
			extractableMembers = processor.getExtractableMembers();
		} catch (JavaModelException e) {
			return null;
		}
		Member[] members = Arrays.stream(extractableMembers).map(member -> {
			try {
				if (member instanceof SourceMethod) {
					SourceMethod method = ((SourceMethod) member);
					return new Member(method.getElementName(), Signature.getSignatureSimpleName(method.getReturnType()),
							Arrays.stream(method.getParameterTypes()).map(parameterType -> Signature.getSignatureSimpleName(parameterType)).toArray(String[]::new), member.getHandleIdentifier());
				} else if (member instanceof SourceField) {
					SourceField field = ((SourceField) member);
					return new Member(field.getElementName(), Signature.getSignatureSimpleName(field.getTypeSignature()), field.getHandleIdentifier());
				} else {
					return null;
				}
			} catch (JavaModelException e) {
				return null;
			}
		}).filter(member -> member != null).toArray(Member[]::new);
		MoveDestinationsResponse destinationResponse = MoveHandler.getPackageDestinations(new String[]{ params.getTextDocument().getUri() });
		return new CheckExtractInterfaceResponse(members, type.getElementName(), destinationResponse);
	}

	public static Refactoring getExtractInterfaceRefactoring(CodeActionParams params, List<String> handleIdentifiers, String interfaceName, PackageNode packageNode) {
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (cu == null) {
			return null;
		}
		IType type = cu.findPrimaryType();
		if (type == null) {
			return null;
		}
		CodeGenerationSettings settings = PreferenceManager.getCodeGenerationSettings(cu);
		ExtractInterfaceProcessorLS processor = new ExtractInterfaceProcessorLS(type, settings);
		try {
			IMember[] selectedMembers = Arrays.stream(processor.getExtractableMembers()).filter((member) -> {
				return handleIdentifiers.contains(member.getHandleIdentifier());
			}).toArray(IMember[]::new);
			IPackageFragment fragment = JDTUtils.resolvePackage(packageNode.uri);
			if (fragment != null) {
				processor.setPackageFragment(fragment);
			}
			processor.setExtractedMembers(selectedMembers);
			processor.setTypeName(interfaceName);
			processor.setReplace(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getExtractInterfaceReplaceEnabled());
			processor.setAnnotations(true);
			Refactoring refactoring = new ProcessorBasedRefactoring(processor);
			RefactoringStatus status = refactoring.checkFinalConditions(new NullProgressMonitor());
			if (status.isOK()) {
				return refactoring;
			} else {
				if (status.hasError()) {
					String message = status.getMessageMatchingSeverity(RefactoringStatus.FATAL);
					if (message == null) {
						message = status.getMessageMatchingSeverity(RefactoringStatus.ERROR);
					}
					JavaLanguageServerPlugin.getProjectsManager().getConnection().showMessage(new MessageParams(MessageType.Error, message));
				}
				JavaLanguageServerPlugin.logError(status.toString());
			}
		} catch (CoreException e) {
			// Do nothing
		}
		return null;
	}

	public static class Member {
		public String name;
		public String typeName;
		public String[] parameters;
		public String handleIdentifier;

		public Member(String name, String typeName, String handleIdentifier) {
			this.name = name;
			this.typeName = typeName;
			this.handleIdentifier = handleIdentifier;
		}

		public Member(String name, String typeName, String[] parameters, String handleIdentifier) {
			this.name = name;
			this.typeName = typeName;
			this.parameters = parameters;
			this.handleIdentifier = handleIdentifier;
		}
	}

	public static class CheckExtractInterfaceResponse {
		public Member[] members;
		public String subTypeName;
		public MoveDestinationsResponse destinationResponse;

		public CheckExtractInterfaceResponse(Member[] members, String subTypeName, MoveDestinationsResponse destinationResponse) {
			this.members = members;
			this.subTypeName = subTypeName;
			this.destinationResponse = destinationResponse;
		}
	}
}
