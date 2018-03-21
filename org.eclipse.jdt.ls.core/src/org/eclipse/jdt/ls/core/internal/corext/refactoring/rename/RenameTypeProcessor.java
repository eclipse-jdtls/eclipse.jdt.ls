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

package org.eclipse.jdt.ls.core.internal.corext.refactoring.rename;

import java.util.Arrays;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.ResourceChange;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class RenameTypeProcessor extends RenameProcessor {

	public RenameTypeProcessor(IJavaElement selectedElement) {
		super(selectedElement);
	}

	@Override
	public void renameOccurrences(WorkspaceEdit edit, String newName, IProgressMonitor monitor) throws CoreException {
		super.renameOccurrences(edit, newName, monitor);

		IType t = (IType) fElement;
		IMethod[] methods = t.getMethods();

		for (IMethod method : methods) {
			if (method.isConstructor()) {
				TextEdit replaceEdit = new ReplaceEdit(method.getNameRange().getOffset(), method.getNameRange().getLength(), newName);
				convert(edit, t.getCompilationUnit(), replaceEdit);
			}
		}

		final ClientPreferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences();

		if (preferences.isWorkspaceEditResourceChangesSupported() &&  isPrimaryType(t) && fElement.getResource() != null ){
			// Resource change is needed and supported by client
			ResourceChange rc = new ResourceChange();
			String newCUName = getNewCompilationUnit(t, newName).getElementName();
			IPath currentPath = t.getCompilationUnit().getResource().getLocation();
			rc.setCurrent(ResourceUtils.fixURI(t.getCompilationUnit().getResource().getRawLocationURI()));
			IPath newPath = currentPath.removeLastSegments(1).append(newCUName);
			rc.setNewUri(ResourceUtils.fixURI(newPath.toFile().toURI()));
			edit.getResourceChanges().add(Either.forLeft(rc));
		}
	}

	private boolean isPrimaryType(IType type) {
		String cuName = type.getCompilationUnit().getElementName();
		String typeName = type.getElementName();
		return type.getDeclaringType() == null && JavaCore.removeJavaLikeExtension(cuName).equals(typeName);
	}

	private ICompilationUnit getNewCompilationUnit(IType type,String newName ) {
		ICompilationUnit cu= type.getCompilationUnit();
		if (isPrimaryType(type)) {
			IPackageFragment parent= type.getPackageFragment();
			String renamedCUName= JavaModelUtil.getRenamedCUName(cu, newName);
			return parent.getCompilationUnit(renamedCUName);
		} else {
			return cu;
		}
	}

}
