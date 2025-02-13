/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.ui.fix.PatternInstanceofToSwitchCleanUpCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

public class PatternInstanceofToSwitchCleanup implements ISimpleCleanUp {

	@Override
	public Collection<String> getIdentifiers() {
		return List.of("useSwitchForInstanceofPattern", CleanUpConstants.USE_SWITCH_FOR_INSTANCEOF_PATTERN);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		CompilationUnit unit = context.getAST();
		if (unit == null) {
			return null;
		}

		Map<String, String> options = new Hashtable<>();
		options.put(CleanUpConstants.USE_SWITCH_FOR_INSTANCEOF_PATTERN, CleanUpOptions.TRUE);
		return new PatternInstanceofToSwitchCleanUpCore(options).createFix(context);
	}

	@Override
	public List<String> getRequiredCompilerMarkers() {
		return Collections.emptyList();
	}

}
