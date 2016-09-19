/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
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
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/
package copied.org.eclipse.jdt.internal.corext.dom;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IBinding;

/**
 * JDT-UI-internal helper methods that deal with {@link IBinding}s:
 * <ul>
 * <li>additional operations on {@link IBinding}s and subtypes</li>
 * <li>finding corresponding elements in the type hierarchy</li>
 * <li>resolve bindings from a family of {@link ASTNode} types</li>
 * </ul>
 * 
 */
public class Bindings {
	/**
	 * Checks if the two bindings are equals. Also works across binding
	 * environments.
	 * 
	 * @param b1
	 *            first binding treated as <code>this</code>. So it must not be
	 *            <code>null</code>
	 * @param b2
	 *            the second binding.
	 * @return boolean
	 */
	public static boolean equals(IBinding b1, IBinding b2) {
		return b1.isEqualTo(b2);
	}


	/**
	 * Checks if the two arrays of bindings have the same length and their
	 * elements are equal. Uses <code>Bindings.equals(IBinding, IBinding)</code>
	 * to compare.
	 * 
	 * @param b1
	 *            the first array of bindings. Must not be <code>null</code>.
	 * @param b2
	 *            the second array of bindings.
	 * @return boolean
	 */
	public static boolean equals(IBinding[] b1, IBinding[] b2) {
		Assert.isNotNull(b1);
		if (b1 == b2)
			return true;
		if (b2 == null)
			return false;
		if (b1.length != b2.length)
			return false;
		for (int i = 0; i < b1.length; i++) {
			if (!Bindings.equals(b1[i], b2[i]))
				return false;
		}
		return true;
	}
}
