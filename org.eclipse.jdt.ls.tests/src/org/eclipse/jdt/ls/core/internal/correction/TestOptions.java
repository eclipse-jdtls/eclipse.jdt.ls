/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

public class TestOptions {
	public static Hashtable<String, String> getDefaultOptions() {
		Hashtable<String, String> result = JavaCore.getDefaultOptions();
		result.put(JavaCore.COMPILER_PB_LOCAL_VARIABLE_HIDING, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_FIELD_HIDING, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_RAW_TYPE_REFERENCE, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_UNUSED_WARNING_TOKEN, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_DEAD_CODE, JavaCore.IGNORE);
		result.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.ERROR);

		result.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		result.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");

		JavaCore.setComplianceOptions("1.8", result);

		return result;
	}

}
