/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.StandardPreferenceManager;
import org.junit.Test;

/**
 * @author siarhei_leanavets1
 *
 */
public class StandardProjectManagerTest {

	private class StandardProjectsManagerDummy extends StandardProjectsManager {
		/**
		 * @param preferenceManager
		 */
		public StandardProjectsManagerDummy(PreferenceManager preferenceManager) {
			super(preferenceManager);
		}

		@Override
		public Stream<IBuildSupport> buildSupports() {
			return super.buildSupports();
		}
	}

	@Test
	public void testCheckBuildSupportOrder() {
		PreferenceManager preferenceManager = mock(StandardPreferenceManager.class);
		StandardProjectsManagerDummy projectsManagerDummy = new StandardProjectsManagerDummy(preferenceManager);
		List<Class<? extends IBuildSupport>> expectedList = Arrays.asList(GradleBuildSupport.class, MavenBuildSupport.class, InvisibleProjectBuildSupport.class, DefaultProjectBuildSupport.class, EclipseBuildSupport.class);
		List<IBuildSupport> actualList = projectsManagerDummy.buildSupports().collect(Collectors.toList());
		for (int i = 0; i < expectedList.size(); i++) {
			assertEquals(expectedList.get(i), actualList.get(i).getClass());
		}
	}

}
