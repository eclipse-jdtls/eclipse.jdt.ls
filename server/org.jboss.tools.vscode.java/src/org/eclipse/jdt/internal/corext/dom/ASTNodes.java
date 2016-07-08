/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Dmitry Stalnov (dstalnov@fusionone.com) - contributed fix for
 *       bug "inline method - doesn't handle implicit cast" (see
 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=24941).
 *     Dmitry Stalnov (dstalnov@fusionone.com) - contributed fix for
 *       bug Encapsulate field can fail when two variables in one variable declaration (see
 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=51540).
 *     Stephan Herrmann - Configuration for
 *		 Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.dom;

import javax.script.Bindings;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * JDT-UI-internal helper methods that deal with {@link ASTNode}s:
 * <ul>
 * <li>additional operations on {@link ASTNode}s and subtypes</li>
 * <li>finding related nodes in an AST</li>
 * <li>some methods that deal with bindings (new such methods should go into
 * {@link Bindings})</li>
 * </ul>
 * 
 */
public class ASTNodes {
	public static String asString(ASTNode node) {
		ASTFlattener flattener = new ASTFlattener();
		node.accept(flattener);
		return flattener.getResult();
	}
}
