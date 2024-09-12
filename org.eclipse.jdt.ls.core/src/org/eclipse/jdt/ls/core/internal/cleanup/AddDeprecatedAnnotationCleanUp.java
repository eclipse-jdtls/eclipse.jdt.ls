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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.Java50FixCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

/**
 * A cleanup that adds the deprecated annotation to classes/fields/methods that
 * are marked deprecated in the javadoc.
 */
public class AddDeprecatedAnnotationCleanUp implements ISimpleCleanUp {

	private static final List<String> COMPILER_OPTS = Arrays.asList(JavaCore.COMPILER_PB_MISSING_DEPRECATED_ANNOTATION);

	@Override
	public Collection<String> getIdentifiers() {
		return List.of("addDeprecated", CleanUpConstants.ADD_MISSING_ANNOTATIONS_DEPRECATED);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) {
		return Java50FixCore.createCleanUp(context.getAST(), false, false, true, false);
	}

	@Override
	public List<String> getRequiredCompilerMarkers() {
		return COMPILER_OPTS;
	}

}
