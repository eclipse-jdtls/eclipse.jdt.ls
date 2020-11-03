/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.Test;

public class ResolveSourceMappingHandlerTest extends AbstractProjectsManagerBasedTest {

    @Before
	public void setup() throws Exception {
		importProjects("maven/quickstart2");
    }
    
    @Test
    public void testResolveSourceUri() {
        String uri = ResolveSourceMappingHandler.resolveStackTraceLocation("at quickstart.AppTest.shouldAnswerWithTrue(AppTest.java:10)", Arrays.asList("quickstart2"));
        assertTrue(uri.startsWith("file://"));
        assertTrue(uri.contains("quickstart2/src/test/java/quickstart/AppTest.java"));
    }

    @Test
    public void testResolveDependencyUri() {
        String uri = ResolveSourceMappingHandler.resolveStackTraceLocation("at org.junit.Assert.assertEquals(Assert.java:117)", Arrays.asList("quickstart2"));
        assertTrue(uri.startsWith("jdt://contents/junit-4.13.jar/org.junit/Assert.class"));
        assertTrue(uri.contains("junit%5C/junit%5C/4.13%5C/junit-4.13.jar=/maven.pomderived=/true=/=/maven.pomderived=/true=/=/test=/true=/=/maven.groupId=/junit=/=/maven.artifactId=/junit=/=/maven.version=/4.13=/=/maven.scope=/test=/%3Corg.junit(Assert.class"));
    }

    @Test
    public void testResolveDependencyUriWithoutGivingProjectNames() {
        String uri = ResolveSourceMappingHandler.resolveStackTraceLocation("at org.junit.Assert.assertEquals(Assert.java:117)", null);
        assertTrue(uri.startsWith("jdt://contents/junit-4.13.jar/org.junit/Assert.class"));
        assertTrue(uri.contains("junit%5C/junit%5C/4.13%5C/junit-4.13.jar=/maven.pomderived=/true=/=/maven.pomderived=/true=/=/test=/true=/=/maven.groupId=/junit=/=/maven.artifactId=/junit=/=/maven.version=/4.13=/=/maven.scope=/test=/%3Corg.junit(Assert.class"));
    }
}
