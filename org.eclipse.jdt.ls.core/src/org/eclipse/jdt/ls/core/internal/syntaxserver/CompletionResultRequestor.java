/*******************************************************************************
* Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.syntaxserver;

import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.codeassist.ISearchRequestor;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.core.JavaElementRequestor;
import org.eclipse.jdt.internal.core.NameLookup;

class CompletionResultRequestor extends JavaElementRequestor {
	protected ISearchRequestor requestor;
	protected Object unitToSkip;
	protected IJavaProject project;
	protected NameLookup nameLookup;
	protected boolean checkAccessRestrictions;

	public CompletionResultRequestor(ISearchRequestor requestor) {
		this.requestor = requestor;
		this.unitToSkip= null;
		this.project= null;
		this.nameLookup= null;
		this.checkAccessRestrictions = false;
	}

	public CompletionResultRequestor(ISearchRequestor requestor, ICompilationUnit unitToSkip, IJavaProject project, NameLookup nameLookup) {
		this.requestor = requestor;
		this.unitToSkip= unitToSkip;
		this.project= project;
		this.nameLookup = nameLookup;
		this.checkAccessRestrictions =
			!JavaCore.IGNORE.equals(project.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, true))
			|| !JavaCore.IGNORE.equals(project.getOption(JavaCore.COMPILER_PB_DISCOURAGED_REFERENCE, true));
	}

	@Override
	public void acceptInitializer(IInitializer initializer) {
		// implements interface method
	}

	@Override
	public void acceptPackageFragment(IPackageFragment packageFragment) {
		this.requestor.acceptPackage(packageFragment.getElementName().toCharArray());
	}

	@Override
	public void acceptModule(IModuleDescription module) {
		this.requestor.acceptModule(module.getElementName().toCharArray());
	}

	@Override
	public void acceptType(IType type) {
		try {
			if (this.unitToSkip != null && this.unitToSkip.equals(type.getCompilationUnit())) {
				return;
			}

			char[] packageName = type.getPackageFragment().getElementName().toCharArray();
			if (type.isBinary()) {
				// Tradeoff: type.getFlags() needs load the binary class into memory, which is
				// a little expensive for the code completion feature. So make a compromise
				// here and simply return the type modifier as public class. This will lose a
				// bit of precision, like type might be interface, enum, etc.
				this.requestor.acceptType(packageName, type.getElementName().toCharArray(), null, ClassFileConstants.AccPublic, null);
			} else {
				this.requestor.acceptType(packageName, type.getElementName().toCharArray(), null, type.getFlags(), null);
			}
		} catch (JavaModelException jme) {
			// ignore
		}
	}
}
