/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/codemanipulation/StubUtility2.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.codemanipulation;

import org.eclipse.jdt.core.dom.ITypeBinding;


/**
 * Utilities for code generation based on AST rewrite.
 * @since 3.1
 */
public final class StubUtility2 {


	public static ITypeBinding replaceWildcardsAndCaptures(ITypeBinding type) {
		while (type.isWildcardType() || type.isCapture() || (type.isArray() && type.getElementType().isCapture())) {
			ITypeBinding bound= type.getBound();
			type= (bound != null) ? bound : type.getErasure();
		}
		return type;
	}
}
