/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

import java.io.IOException;
import java.io.StringReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.buildship.core.internal.launch.GradleClasspathProvider;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.internal.core.LaunchConfiguration;
import org.eclipse.debug.internal.core.LaunchConfigurationInfo;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.m2e.jdt.internal.launch.MavenRuntimeClasspathProvider;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ProjectCommand {

    /**
     * Get the project settings.
     * @param uri Uri of the source/class file needs to be queried.
     * @param settingKeys the settings want to query, for example: ["org.eclipse.jdt.core.compiler.compliance"]
     * @return A Map<string, string> with all the setting keys and their values.
     * @throws CoreException
     */
    public static Map<String, String> getProjectSettings(String uri, List<String> settingKeys) throws CoreException {
        IJavaProject javaProject = getJavaProjectFromUri(uri);
        Map<String, String> settings = new HashMap<>();
        for (String key : settingKeys) {
            settings.putIfAbsent(key, javaProject.getOption(key, true));
        }
        return settings;
    }

    public static ClasspathResult getClasspaths(String uri, ClasspathOptions options) throws CoreException {
        IJavaProject javaProject = getJavaProjectFromUri(uri);
        ILaunchConfiguration launchConfig = new JavaApplicationLaunchConfiguration(javaProject.getProject(), options.excludingTests);
        JavaLaunchDelegate delegate = new JavaLaunchDelegate();
        String[][] paths = delegate.getClasspathAndModulepath(launchConfig);
        return new ClasspathResult(paths[0], paths[1]);
    }

    private static IJavaProject getJavaProjectFromUri(String uri) throws CoreException {
        ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
        if (typeRoot == null) {
            throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to an existing TypeRoot"));
        }
        IJavaProject javaProject = typeRoot.getJavaProject();
        if (javaProject == null) {
            throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Given URI does not belong to an existing Java project"));
        }
        return javaProject;
    }
    
    private static class JavaApplicationLaunchConfiguration extends LaunchConfiguration {
        public static final String JAVA_APPLICATION_LAUNCH = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<launchConfiguration type=\"%s\">\n"
                + "<listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_PATHS\">\n"
                + "</listAttribute>\n"
                + "<listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_TYPES\">\n"
                + "</listAttribute>\n"
                + "</launchConfiguration>";
        private IProject project;
        private boolean excludeTestCode;
        private String classpathProvider;
        private String sourcepathProvider;
        private LaunchConfigurationInfo launchInfo;

        protected JavaApplicationLaunchConfiguration(IProject project, boolean excludeTestCode) throws CoreException {
            super(String.valueOf(new Date().getTime()), null, false);
            this.project = project;
            this.excludeTestCode = excludeTestCode;
            if (ProjectUtils.isMavenProject(project)) {
                classpathProvider = MavenRuntimeClasspathProvider.MAVEN_CLASSPATH_PROVIDER;
                sourcepathProvider = MavenRuntimeClasspathProvider.MAVEN_SOURCEPATH_PROVIDER;
            } else if (ProjectUtils.isGradleProject(project)) {
                classpathProvider = GradleClasspathProvider.ID;
            }
            // Since MavenRuntimeClasspathProvider will only encluding test entries when:
            // 1. Launch configuration is JUnit/TestNG type
            // 2. Mapped resource is in test path.
            // That's why we use JUnit launch configuration here to make sure the result is right when excludeTestCode is false.
            // See: {@link org.eclipse.m2e.jdt.internal.launch.MavenRuntimeClasspathProvider#getArtifactScope(ILaunchConfiguration)}
            this.launchInfo = new JavaLaunchConfigurationInfo(String.format(JAVA_APPLICATION_LAUNCH, excludeTestCode ? 
                    MavenRuntimeClasspathProvider.JDT_JAVA_APPLICATION : MavenRuntimeClasspathProvider.JDT_JUNIT_TEST));
        }

        @Override
        public boolean getAttribute(String attributeName, boolean defaultValue) throws CoreException {
            if (IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE.equalsIgnoreCase(attributeName)) {
                return excludeTestCode;
            }

            return super.getAttribute(attributeName, defaultValue);
        }

        @Override
        public String getAttribute(String attributeName, String defaultValue) throws CoreException {
            if (IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME.equalsIgnoreCase(attributeName)) {
                return project.getName();
            } else if (IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER.equalsIgnoreCase(attributeName)) {
                return classpathProvider;
            } else if (IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER.equalsIgnoreCase(attributeName)) {
                return sourcepathProvider;
            }

            return super.getAttribute(attributeName, defaultValue);
        }

        @Override
        protected LaunchConfigurationInfo getInfo() throws CoreException {
            return this.launchInfo;
        }
    }

    private static class JavaLaunchConfigurationInfo extends LaunchConfigurationInfo {
        public JavaLaunchConfigurationInfo(String launchXml) {
            super();
            try {
                DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                parser.setErrorHandler(new DefaultHandler());
                StringReader reader = new StringReader(launchXml);
                InputSource source = new InputSource(reader);
                Element root = parser.parse(source).getDocumentElement();
                initializeFromXML(root);
            } catch (ParserConfigurationException | SAXException | IOException | CoreException e) {
                // do nothing
            }
        }
    }

    public static class ClasspathOptions {
        public boolean excludingTests;
    }

    static class ClasspathResult {
        String[] classpaths;
        String[] modulepaths;

        public ClasspathResult(String[] classpaths, String[] modulepaths) {
            this.classpaths = classpaths;
            this.modulepaths = modulepaths;
        }
    }
}
