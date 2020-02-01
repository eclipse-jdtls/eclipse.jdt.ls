/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathOptions;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathResult;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;

/**
 * ProjectCommandTest
 */
public class ProjectCommandTest extends AbstractProjectsManagerBasedTest {

    @Test
    public void testGetProjectSettingsForMavenJava7() throws Exception {
        importProjects("maven/salut");
        IProject project = WorkspaceHelper.getProject("salut");
        String uriString = project.getFile("src/main/java/Foo.java").getLocationURI().toString();
        List<String> settingKeys = Arrays.asList("org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.source");
        Map<String, String> options = ProjectCommand.getProjectSettings(uriString, settingKeys);

        assertEquals(settingKeys.size(), options.size());
        assertEquals("1.7", options.get("org.eclipse.jdt.core.compiler.compliance"));
        assertEquals("1.7", options.get("org.eclipse.jdt.core.compiler.source"));
    }

    @Test
    public void testGetProjectSettingsForMavenJava8() throws Exception {
        importProjects("maven/salut2");
        IProject project = WorkspaceHelper.getProject("salut2");
        String uriString = project.getFile("src/main/java/foo/Bar.java").getLocationURI().toString();
        List<String> settingKeys = Arrays.asList("org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.source");
        Map<String, String> options = ProjectCommand.getProjectSettings(uriString, settingKeys);

        assertEquals(settingKeys.size(), options.size());
        assertEquals("1.8", options.get("org.eclipse.jdt.core.compiler.compliance"));
        assertEquals("1.8", options.get("org.eclipse.jdt.core.compiler.source"));
    }

    @Test
    public void testGetClasspathsForMaven() throws Exception {
        importProjects("maven/classpathtest");
        IProject project = WorkspaceHelper.getProject("classpathtest");
        String uriString = project.getFile("src/main/java/main/App.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        options.scope = "runtime";
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(1, result.classpaths.length);
        assertEquals(0, result.modulepaths.length);
        assertTrue(result.classpaths[0].indexOf("junit") == -1);

        options.scope = "test";
        result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(4, result.classpaths.length);
        assertEquals(0, result.modulepaths.length);
        boolean containsJunit = Arrays.stream(result.classpaths).anyMatch(element -> {
            return element.indexOf("junit") > -1;
        });
        assertTrue(containsJunit);
    }

    @Test
    public void testGetClasspathsForGradle() throws Exception {
        importProjects("gradle/simple-gradle");
        IProject project = WorkspaceHelper.getProject("simple-gradle");
        String uriString = project.getFile("src/main/java/Library.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        // Gradle project will always return classpath containing test dependencies.
        // So we only test `scope = "test"` scenario.
        options.scope = "test";
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(5, result.classpaths.length);
        assertEquals(0, result.modulepaths.length);
        boolean containsJunit = Arrays.stream(result.classpaths).anyMatch(element -> {
            return element.indexOf("junit") > -1;
        });
        assertTrue(containsJunit);
    }

    @Test
    public void testGetClasspathsForMavenModular() throws Exception {
        importProjects("maven/modular-project");
        IProject project = WorkspaceHelper.getProject("modular-project");
        String uriString = project.getFile("src/main/java/modular/Main.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        options.scope = "test";
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(0, result.classpaths.length);
        assertEquals(1, result.modulepaths.length);
    }

    @Test
    public void testIsTestFileForMaven() throws Exception {
        importProjects("maven/classpathtest");
        IProject project = WorkspaceHelper.getProject("classpathtest");
        String srcUri = project.getFile("src/main/java/main/App.java").getLocationURI().toString();
        String testUri = project.getFile("src/test/java/test/AppTest.java").getLocationURI().toString();
        assertFalse(ProjectCommand.isTestFile(srcUri));
        assertTrue(ProjectCommand.isTestFile(testUri));
    }

    @Test
    public void testIsTestFileForGradle() throws Exception {
        importProjects("gradle/simple-gradle");
        IProject project = WorkspaceHelper.getProject("simple-gradle");
        String srcUri = project.getFile("src/main/java/Library.java").getLocationURI().toString();
        String testUri = project.getFile("src/test/java/LibraryTest.java").getLocationURI().toString();
        assertFalse(ProjectCommand.isTestFile(srcUri));
        assertTrue(ProjectCommand.isTestFile(testUri));
    }
}