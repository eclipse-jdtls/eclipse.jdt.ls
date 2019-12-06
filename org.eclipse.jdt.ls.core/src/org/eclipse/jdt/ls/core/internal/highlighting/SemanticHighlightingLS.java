/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightingCore;
import org.eclipse.jdt.internal.ui.javaeditor.SemanticToken;

/**
 * @author jjohnstn
 *
 */
public class SemanticHighlightingLS extends SemanticHighlightingCore {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightingCore#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return this.getClass().getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightingCore#consumes(org.eclipse.jdt.internal.ui.javaeditor.SemanticToken)
	 */
	@Override
	public boolean consumes(SemanticToken token) {
		return false;
	}

	public List<String> getScopes() {
		return Collections.emptyList();
	}

}
