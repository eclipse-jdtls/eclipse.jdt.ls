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

        assertEquals(options.size(), settingKeys.size());
        assertEquals(options.get("org.eclipse.jdt.core.compiler.compliance"), "1.7");
        assertEquals(options.get("org.eclipse.jdt.core.compiler.source"), "1.7");
    }

    @Test
    public void testGetProjectSettingsForMavenJava8() throws Exception {
        importProjects("maven/salut2");
        IProject project = WorkspaceHelper.getProject("salut2");
        String uriString = project.getFile("src/main/java/foo/Bar.java").getLocationURI().toString();
        List<String> settingKeys = Arrays.asList("org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.source");
        Map<String, String> options = ProjectCommand.getProjectSettings(uriString, settingKeys);

        assertEquals(options.size(), settingKeys.size());
        assertEquals(options.get("org.eclipse.jdt.core.compiler.compliance"), "1.8");
        assertEquals(options.get("org.eclipse.jdt.core.compiler.source"), "1.8");
    }

    @Test
    public void testGetClasspathsForMaven() throws Exception {
        importProjects("maven/classpathtest");
        IProject project = WorkspaceHelper.getProject("classpathtest");
        String uriString = project.getFile("src/main/java/main/App.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        options.excludingTests = true;
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(result.classpaths.length, 1);
        assertEquals(result.modulepaths.length, 0);
        assertTrue(result.classpaths[0].indexOf("junit") == -1);

        options.excludingTests = false;
        result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(result.classpaths.length, 4);
        assertEquals(result.modulepaths.length, 0);
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
        // So we only test `excludingTests = false` scenario.
        options.excludingTests = false;
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(result.classpaths.length, 5);
        assertEquals(result.modulepaths.length, 0);
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
        options.excludingTests = false;
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(result.classpaths.length, 0);
        assertEquals(result.modulepaths.length, 1);
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