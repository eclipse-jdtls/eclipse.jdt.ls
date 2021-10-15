/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author siarhei_leanavets1
 *
 */
@RunWith(Parameterized.class)
public class JDTUtilsTestParametrized {

	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
			{ "file:///home/some/excluded/path/build.gradle", true },
			{ "file:///home/some/path/build.excluded", true },
			{ "file:///home/some/path/build.gradle", false },
			{ "file:///C:/abc/.excluded", true },
			{ "file:///C:/abc/.included", false },
		});
	}

	private static List<String> patterns = Arrays.asList(
		"**/some/excluded/path/**",
		"**/*.excluded"
	);

	private String filePath;
	private boolean isExcluded;

	public JDTUtilsTestParametrized(String filePath, boolean isExcluded) {
		this.filePath = filePath;
		this.isExcluded = isExcluded;
	}

	@Test
	public void testIsExcludedFile() {
		assertEquals(JDTUtils.isExcludedFile(patterns, filePath), isExcluded);
	}
}
