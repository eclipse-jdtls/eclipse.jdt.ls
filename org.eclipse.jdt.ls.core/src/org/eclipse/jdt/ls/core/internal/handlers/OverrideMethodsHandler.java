/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.List;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.codemanipulation.OverrideMethodsOperation;
import org.eclipse.jdt.ls.core.internal.codemanipulation.OverrideMethodsOperation.OverridableMethod;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class OverrideMethodsHandler {

	public static OverridableMethodsResponse listOverridableMethods(CodeActionParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params);
		String typeName = type == null ? "" : type.getTypeQualifiedName();
		List<OverridableMethod> methods = OverrideMethodsOperation.listOverridableMethods(type);
		return new OverridableMethodsResponse(typeName, methods);
	}

	public static WorkspaceEdit addOverridableMethods(AddOverridableMethodParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params.context);
		TextEdit edit = OverrideMethodsOperation.addOverridableMethods(type, params.overridableMethods);
		if (edit == null) {
			return null;
		}

		return SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	public static class OverridableMethodsResponse {
		public String type;
		public List<OverridableMethod> methods;

		public OverridableMethodsResponse() {

		}

		public OverridableMethodsResponse(String typeName, List<OverridableMethod> methods) {
			this.type = typeName;
			this.methods = methods;
		}
	}

	public static class AddOverridableMethodParams {
		public CodeActionParams context;
		public OverridableMethod[] overridableMethods;
	}
}
