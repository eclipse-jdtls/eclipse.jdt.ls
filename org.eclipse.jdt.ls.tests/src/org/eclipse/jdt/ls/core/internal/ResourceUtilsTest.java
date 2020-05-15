/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import static org.eclipse.jdt.ls.core.internal.ResourceUtils.toGlobPattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.Test;

public class ResourceUtilsTest {

	@Test
	public void testToGlobPattern() {
		assertNull(toGlobPattern(null));
		assertEquals("/foo/bar/**", toGlobPattern(Path.forPosix("/foo/bar")));
		assertEquals("/foo/bar/**", toGlobPattern(Path.forPosix("/foo/bar/")));
		assertEquals("**/foo/bar/**", toGlobPattern(Path.forWindows("c:/foo/bar/")));
		assertEquals("**/foo/bar/**", toGlobPattern(Path.forWindows("c:\\foo\\bar")));
		assertEquals("/foo/bar/foo.jar", toGlobPattern(Path.forPosix("/foo/bar/foo.jar")));
	}

	@Test
	public void testGetLongestCommonPath() {
		assertNull(ResourceUtils.getLongestCommonPath(new IPath[0]));
		IPath[] input = new IPath[] {
			new Path("C:", "/test/test1.java"),
			new Path("D:", "/test/test2.java")
		};
		assertNull(ResourceUtils.getLongestCommonPath(input));

		input = new IPath[] {
			new Path("C:", "/work/src/org/eclipse/test1.java"),
			new Path("C:", "/work/src/org/eclipse/test2.java"),
			new Path("C:", "/work/src/org/eclipse/test3.java"),
		};
		IPath commonPath = ResourceUtils.getLongestCommonPath(input);
		assertNotNull(commonPath);
		assertEquals("C:/work/src/org/eclipse", commonPath.toPortableString());

		input = new IPath[] {
			new Path("/work/src/org/eclipse/jdt/ls/test1.java"),
			new Path("/work/src/org/eclipse/jdt/core/test2.java"),
			new Path("/work/src/org/eclipse/test3.java"),
		};
		commonPath = ResourceUtils.getLongestCommonPath(input);
		assertNotNull(commonPath);
		assertEquals("/work/src/org/eclipse", commonPath.toPortableString());
	}
}
