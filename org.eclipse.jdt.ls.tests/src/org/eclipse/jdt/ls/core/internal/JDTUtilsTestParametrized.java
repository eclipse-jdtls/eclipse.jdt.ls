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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author siarhei_leanavets1
 *
 */
public class JDTUtilsTestParametrized {

	private static List<String> patterns = Arrays.asList(
		"**/some/excluded/path/**",
		"**/*.excluded"
	);

	static Stream<Arguments> data() {
		return Stream.of(
			Arguments.of("file:///home/some/excluded/path/build.gradle", true),
			Arguments.of("file:///home/some/path/build.excluded", true),
			Arguments.of("file:///home/some/path/build.gradle", false),
			Arguments.of("file:///C:/abc/.excluded", true),
			Arguments.of("file:///C:/abc/.included", false)
		);
	}

	@ParameterizedTest
	@MethodSource("data")
	void testIsExcludedFile(String filePath, boolean isExcluded) {
		assertEquals(JDTUtils.isExcludedFile(patterns, filePath), isExcluded);
	}
}
