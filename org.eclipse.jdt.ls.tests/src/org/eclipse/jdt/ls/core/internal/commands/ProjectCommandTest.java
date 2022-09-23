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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathOptions;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathResult;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Test;

/**
 * ProjectCommandTest
 */
public class ProjectCommandTest extends AbstractInvisibleProjectBasedTest {

    @Test
    public void testGetProjectSettingsForMavenJava7() throws Exception {
        importProjects("maven/salut");
        IProject project = WorkspaceHelper.getProject("salut");
        String uriString = project.getFile("src/main/java/Foo.java").getLocationURI().toString();
        List<String> settingKeys = Arrays.asList("org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.source");
        Map<String, Object> options = ProjectCommand.getProjectSettings(uriString, settingKeys);

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
        Map<String, Object> options = ProjectCommand.getProjectSettings(uriString, settingKeys);

        assertEquals(settingKeys.size(), options.size());
        assertEquals("1.8", options.get("org.eclipse.jdt.core.compiler.compliance"));
        assertEquals("1.8", options.get("org.eclipse.jdt.core.compiler.source"));
    }

    @Test
    public void testGetProjectVMInstallation() throws Exception {
        importProjects("maven/salut2");
        IProject project = WorkspaceHelper.getProject("salut2");
        String uriString = project.getFile("src/main/java/foo/Bar.java").getLocationURI().toString();
        List<String> settingKeys = Arrays.asList(ProjectCommand.VM_LOCATION);
        Map<String, Object> options = ProjectCommand.getProjectSettings(uriString, settingKeys);

        IJavaProject javaProject = ProjectCommand.getJavaProjectFromUri(uriString);
        IVMInstall vmInstall = JavaRuntime.getVMInstall(javaProject);
        assertNotNull(vmInstall);
        File location = vmInstall.getInstallLocation();
        assertNotNull(location);
        assertEquals(location.getAbsolutePath(), options.get(ProjectCommand.VM_LOCATION));
    }

    @Test
    public void testGetProjectSourcePaths() throws Exception {
        IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
        String linkedFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK).getLocationURI().toString();
        List<String> settingKeys = Arrays.asList(ProjectCommand.SOURCE_PATHS);
        Map<String, Object> options = ProjectCommand.getProjectSettings(linkedFolder, settingKeys);
        String[] actualSourcePaths = (String[]) options.get(ProjectCommand.SOURCE_PATHS);
        assertTrue(actualSourcePaths.length == 2);
        assertTrue(Arrays.stream(actualSourcePaths).anyMatch(sourcePath -> {
            return sourcePath.equals(project.getFolder(ProjectUtils.WORKSPACE_LINK).getFolder("src").getLocation().toOSString())
                    || sourcePath.equals(project.getFolder(ProjectUtils.WORKSPACE_LINK).getFolder("test").getLocation().toOSString());
        }));
    }

    @Test
    public void testGetProjectOutputPath() throws Exception {
        IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
        String linkedFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK).getLocationURI().toString();
        List<String> settingKeys = Arrays.asList(ProjectCommand.OUTPUT_PATH);
        Map<String, Object> options = ProjectCommand.getProjectSettings(linkedFolder, settingKeys);
        String actualOutputPath = (String) options.get(ProjectCommand.OUTPUT_PATH);
        String expectedOutputPath = project.getFolder("bin").getLocation().toOSString();
        assertEquals(expectedOutputPath, actualOutputPath);
    }

    @Test
    public void testGetProjectReferencedLibraryPaths() throws Exception {
        IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
        String linkedFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK).getLocationURI().toString();
        List<String> settingKeys = Arrays.asList(ProjectCommand.REFERENCED_LIBRARIES);
        Map<String, Object> options = ProjectCommand.getProjectSettings(linkedFolder, settingKeys);
        String[] actualReferencedLibraryPaths = (String[]) options.get(ProjectCommand.REFERENCED_LIBRARIES);
        String expectedReferencedLibraryPath = project.getFolder(ProjectUtils.WORKSPACE_LINK).getFolder("lib").getFile("mylib.jar").getLocation().toOSString();
        assertTrue(actualReferencedLibraryPaths.length == 1);
        assertEquals(expectedReferencedLibraryPath, actualReferencedLibraryPaths[0]);
    }

    @Test
    public void testGetMavenProjectFromUri() throws Exception {
        importProjects("maven/salut");
        IProject project = WorkspaceHelper.getProject("salut");
        String javaSource = project.getFile("src/main/java/Foo.java").getLocationURI().toString();
        IJavaProject javaProject = ProjectCommand.getJavaProjectFromUri(javaSource);
        assertNotNull("Can get project from java file uri", javaProject);

        String projectUri = project.getLocationURI().toString();
        javaProject = ProjectCommand.getJavaProjectFromUri(projectUri);
        assertNotNull("Can get project from project uri", javaProject);
    }

    @Test
    public void testGetMultiModuleMavenProjectFromUri() throws Exception {
        importProjects("maven/multimodule3");
        IProject project = WorkspaceHelper.getProject("this_is_a_very_long_module_name");
        String javaSource = project.getFile("src/main/org/eclipse/App.java").getLocationURI().toString();
        IJavaProject javaProject = ProjectCommand.getJavaProjectFromUri(javaSource);
        assertEquals("this_is_a_very_long_module_name", javaProject.getElementName());

        String projectUri = project.getLocationURI().toString();
        javaProject = ProjectCommand.getJavaProjectFromUri(projectUri);
        assertEquals("this_is_a_very_long_module_name", javaProject.getElementName());
    }

    @Test
    public void testGetInvisibleProjectFromUri() throws Exception {
        IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
        String linkedFolder = project.getFolder(ProjectUtils.WORKSPACE_LINK).getLocationURI().toString();
        IJavaProject javaProject = ProjectCommand.getJavaProjectFromUri(linkedFolder);
        assertNotNull("Can get project from linked folder uri", javaProject);
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
    public void testGetClasspathsForMavenWhenUpdating() throws Exception {
        importProjects("maven/classpathtest");
        IProject project = WorkspaceHelper.getProject("classpathtest");
        String uriString = project.getFile("src/main/java/main/App.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        options.scope = "test";

        projectsManager.updateProject(project, true);

        for (int i = 0; i < 10; i++) {
            ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
            assertEquals(4, result.classpaths.length);
            assertEquals(0, result.modulepaths.length);
            boolean containsJunit = Arrays.stream(result.classpaths).anyMatch(element -> {
                return element.indexOf("junit") > -1;
            });
            assertTrue(containsJunit);
        }
    }

    @Test
    public void testGetClasspathsForGradle() throws Exception {
        importProjects("gradle/simple-gradle");
        IProject project = WorkspaceHelper.getProject("simple-gradle");
        String uriString = project.getFile("src/main/java/Library.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        options.scope = "runtime";
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(3, result.classpaths.length);
        assertEquals(0, result.modulepaths.length);
        assertTrue(result.classpaths[0].indexOf("junit") == -1);

        options.scope = "test";
        result = ProjectCommand.getClasspaths(uriString, options);
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
    public void testGetClasspathsForEclipse() throws Exception {
        importProjects("eclipse/hello");
        IProject project = WorkspaceHelper.getProject("hello");
        String uriString = project.getFile("src/java/Bar.java").getLocationURI().toString();
        ClasspathOptions options = new ClasspathOptions();
        options.scope = "runtime";
        ClasspathResult result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(1, result.classpaths.length);
        assertEquals(0, result.modulepaths.length);

        options.scope = "test";
        result = ProjectCommand.getClasspaths(uriString, options);
        assertEquals(2, result.classpaths.length);
        assertEquals(0, result.modulepaths.length);
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

    @Test
    public void getAllJavaProject() throws Exception {
        importProjects("maven/multimodule");
        List<URI> projects = ProjectCommand.getAllJavaProjects();
        assertEquals(4, projects.size());
    }

	private static Range START_OF_DOCUMENT = new Range(new Position(0, 0), new Position(0, 0));

	@Test
	public void testResolveClassSymbol() throws Exception {
		importProjects("maven/salut-java11");
		IProject project = WorkspaceHelper.getProject("salut-java11");

		SymbolInformation requestedSymbol = buildClassSymbol(project, "org.apache.commons.lang3.StringUtils");
		SymbolInformation resolvedSymbol = ProjectCommand.resolveWorkspaceSymbol(requestedSymbol);
		assertEquals(new Range(new Position(115, 13), new Position(115, 24)), resolvedSymbol.getLocation().getRange());
	}

	@Test
	public void testResolveNestedClassSymbol() throws Exception {
		importProjects("maven/salut-java11");
		IProject project = WorkspaceHelper.getProject("salut-java11");

		SymbolInformation requestedSymbol = buildClassSymbol(project, "org.sample.Bar");
		requestedSymbol.setName("MyClass");
		SymbolInformation resolvedSymbol = ProjectCommand.resolveWorkspaceSymbol(requestedSymbol);
		assertEquals(resolvedSymbol.getLocation().getRange(), new Range(new Position(17, 21), new Position(17, 28)));
	}

	private SymbolInformation buildClassSymbol(IProject project, String fqClassName) throws Exception {
		String uriString = buildClassfileUri(project, fqClassName);
		SymbolInformation si = new SymbolInformation();
		si.setLocation(new Location(uriString, START_OF_DOCUMENT));
		si.setName(fqClassName.substring(fqClassName.lastIndexOf('.') + 1));
		si.setKind(SymbolKind.Class);
		return si;
	}

	private String buildClassfileUri(IProject project, String fqClassName) throws Exception {
		IJavaProject javaProject = ProjectUtils.getJavaProject(project);
		IType type = javaProject.findType(fqClassName);
		if (type == null) {
			throw new IllegalStateException("Expected to find type " + fqClassName + " in project.");
		}
		if (type.isBinary()) {
			return JDTUtils.toUri(type.getClassFile());
		} else {
			return JDTUtils.toUri((ICompilationUnit) type.getParent());
		}
	}

}