/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.InvertEqualsFixCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

/**
 * Represents a clean up that inverts calls to Object.equals(Object) and
 * String.equalsIgnoreCase(String) to avoid useless null pointer exception.
 */
public class InvertEqualsCleanUp implements ISimpleCleanUp {

	@Override
	public List<String> getIdentifiers() {
		return List.of("invertEquals", CleanUpConstants.INVERT_EQUALS);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		CompilationUnit unit = context.getAST();
		if (unit == null) {
			return null;
		}
		return InvertEqualsFixCore.createCleanUp(unit);
	}

	@Override
	public List<String> getRequiredCompilerMarkers() {
		return Collections.emptyList();
	}
}
