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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.internal.codeassist.ISearchRequestor;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.core.JavaElementRequestor;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

public class ModelBasedSearchableEnvironment extends SearchableEnvironment {
	public ModelBasedSearchableEnvironment(JavaProject javaProject, WorkingCopyOwner owner, boolean excludeTestCode) throws JavaModelException {
		super(javaProject, owner, excludeTestCode);
	}

	@Override
	public void findTypes(char[] prefix, final boolean findMembers, int matchRule, int searchFor, final ISearchRequestor storage, IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}

		JavaLanguageServerPlugin.logInfo("Search engine disabled, searching directly.");
		// Look for types in the model instead of a search request
		findTypes(new String(prefix), storage, convertSearchFilterToModelFilter(searchFor));
	}

	private static int convertSearchFilterToModelFilter(int searchFilter) {
		switch (searchFilter) {
			case IJavaSearchConstants.CLASS:
				return NameLookup.ACCEPT_CLASSES;
			case IJavaSearchConstants.INTERFACE:
				return NameLookup.ACCEPT_INTERFACES;
			case IJavaSearchConstants.ENUM:
				return NameLookup.ACCEPT_ENUMS;
			case IJavaSearchConstants.ANNOTATION_TYPE:
				return NameLookup.ACCEPT_ANNOTATIONS;
			case IJavaSearchConstants.CLASS_AND_ENUM:
				return NameLookup.ACCEPT_CLASSES | NameLookup.ACCEPT_ENUMS;
			case IJavaSearchConstants.CLASS_AND_INTERFACE:
				return NameLookup.ACCEPT_CLASSES | NameLookup.ACCEPT_INTERFACES;
			default:
				return NameLookup.ACCEPT_ALL;
		}
	}

	/**
	 * Find types in the Java models. Currently it doesn't support searching inner types.
	 */
	public void findTypes(String prefix, ISearchRequestor storage, int type) {
		CompletionResultRequestor requestor = new CompletionResultRequestor(storage, this.unitToSkip, this.project, this.nameLookup);
		int index = prefix.lastIndexOf('.');
		if (index == -1) {
			this.nameLookup.seekTypes(prefix, null, true, type, requestor);
		} else {
			String packageName = prefix.substring(0, index);
			JavaElementRequestor elementRequestor = new JavaElementRequestor();
			this.nameLookup.seekPackageFragments(packageName, false, elementRequestor);
			IPackageFragment[] fragments = elementRequestor.getPackageFragments();
			if (fragments != null) {
				String className = prefix.substring(index + 1);
				for (IPackageFragment fragment : fragments) {
					if (fragment != null) {
						this.nameLookup.seekTypes(className, fragment, true, type, requestor);
					}
				}
			}
		}
	}

	public void setUnitToSkip(ICompilationUnit unitToSkip) {
		super.unitToSkip = unitToSkip;
	}
}
