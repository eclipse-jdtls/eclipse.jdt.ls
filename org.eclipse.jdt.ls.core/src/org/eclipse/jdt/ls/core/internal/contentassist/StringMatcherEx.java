/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.contentassist;

import org.eclipse.jdt.internal.ui.util.StringMatcher;

public class StringMatcherEx extends StringMatcher {

    public StringMatcherEx(String pattern, boolean ignoreCase, boolean ignoreWildCards) {
        super(pattern, ignoreCase, ignoreWildCards);
    }

    public boolean endsWithWildcard() {
        return fPattern.endsWith(".*") || fPattern.endsWith(".?");
    }
}
