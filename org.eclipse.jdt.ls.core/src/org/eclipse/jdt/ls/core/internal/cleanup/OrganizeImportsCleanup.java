/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.cleanup;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.text.edits.TextEdit;

public class OrganizeImportsCleanup implements ISimpleCleanUp {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.cleanup.ISimpleCleanUp#getIdentifier()
	 */
	@Override
	public Collection<String> getIdentifiers() {
		return List.of("organizeImports");
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.cleanup.ISimpleCleanUp#createFix(org.eclipse.jdt.core.manipulation.CleanUpContextCore)
	 */
	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		TextEdit te = createTextEdit(context, null, new NullProgressMonitor());
		if (te == null) {
			return null;
		}
		return new ICleanUpFix() {
			@Override
			public CompilationUnitChange createChange(IProgressMonitor progressMonitor) throws CoreException {
				CompilationUnitChange change = new CompilationUnitChange("", context.getCompilationUnit());
				change.setEdit(te);
				return change;
			}
		};
	}

	public TextEdit createTextEdit(CleanUpContext context, Map<ICompilationUnit, Set<String>> requiredModules, IProgressMonitor monitor) throws CoreException {
		CompilationUnit unit = context.getAST();
		if (unit == null) {
			return null;
		}
		OrganizeImportsOperation op = new OrganizeImportsOperation(context.getCompilationUnit(), unit,
				false, false, true,
				null,
				false);
		return op.createTextEdit(monitor, requiredModules);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.cleanup.ISimpleCleanUp#getRequiredCompilerMarkers()
	 */
	@Override
	public List<String> getRequiredCompilerMarkers() {
		return Collections.emptyList();
	}

}
