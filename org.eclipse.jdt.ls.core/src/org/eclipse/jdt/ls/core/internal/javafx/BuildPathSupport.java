/*******************************************************************************
 * Copyright (c) 2012-2020 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Tom Schindl<tom.schindl@bestsolution.at> - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javafx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;

/**
 *
 * Copied from
 * https://github.com/eclipse-efx/efxclipse-eclipse/blob/master/bundles/tooling/org.eclipse.fx.ide.jdt.core/src/org/eclipse/fx/ide/jdt/core/internal/BuildPathSupport.java
 *
 */
public class BuildPathSupport {
	public static final String WEB_JAVADOC_LOCATION = "http://docs.oracle.com/javase/8/javafx/api/";

	public static List<IClasspathEntry> getJavaFXLibraryEntry(IJavaProject project) {
		FXVersion version = getFXVersion(project);
		if( version == FXVersion.FX2 || version == FXVersion.FX8) {
			IPath[] paths = getFxJarPath(project);
			List<IClasspathEntry> rv = new ArrayList<>();
			if( paths != null ) {

				IPath jarLocationPath = paths[0];
				IPath javadocLocation = paths[1];
				IPath fxSource = paths[3];

				IClasspathAttribute[] attributes;
				IAccessRule[] accessRules= { };
				if (javadocLocation == null || !javadocLocation.toFile().exists()) {
					attributes= new IClasspathAttribute[] { JavaCore.newClasspathAttribute(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, WEB_JAVADOC_LOCATION) };
				} else {
					attributes= new IClasspathAttribute[] { JavaCore.newClasspathAttribute(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, javadocLocation.toFile().toURI().toString()) };
				}

				if( jarLocationPath.toFile().exists() ) {
					rv.add(JavaCore.newLibraryEntry(jarLocationPath, fxSource, null, accessRules, attributes, false));
				}
			}

			return rv;
		} else if( version == FXVersion.FX11 || version == FXVersion.FX11PLUS ) {
			String sdkPath = InstanceScope.INSTANCE.getNode("org.eclipse.fx.ide.ui").get("javafx-sdk", null);
			List<IClasspathEntry> entries = new ArrayList<>();
			if( sdkPath != null ) {
				java.nio.file.Path path = Paths.get(sdkPath);
				if( Files.exists(path) ) {
					try {

						entries.addAll(Files.list(path).filter( p -> p.getFileName().toString().endsWith(".jar")).map( p -> {
							IClasspathAttribute moduleAttr = JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true"); //$NON-NLS-1$
							return JavaCore.newLibraryEntry(
									new Path(p.toAbsolutePath().toString()),
									new Path(p.getParent().resolve("src.zip").toAbsolutePath().toString()),
									new Path("."),
									new IAccessRule[]{
											JavaCore.newAccessRule(new Path("javafx/*"),IAccessRule.K_ACCESSIBLE),
											JavaCore.newAccessRule(new Path("com/sun/*"),IAccessRule.K_DISCOURAGED),
											JavaCore.newAccessRule(new Path("netscape/javascript/*"),IAccessRule.K_DISCOURAGED)},
									new IClasspathAttribute[] { moduleAttr },
									false);
						}).collect(Collectors.toList()));
					} catch (IOException e) {
						throw new IllegalStateException();
					}
				}
			}
			return entries;
		}
		return Collections.emptyList();
	}

	public static FXVersion getFXVersion(IJavaProject project) {
		try {
			IVMInstall i = JavaRuntime.getVMInstall(project);
			if( i == null ) {
				i = JavaRuntime.getDefaultVMInstall();
			}

			return FXVersionUtil.getFxVersion(i);
		} catch (CoreException e) {
			throw new IllegalStateException(e);
		}
	}

	public static IPath[] getFxJarPath(IJavaProject project) {
		IPath jarLocationPath = null;
		IPath javadocLocation = null;
		IPath antJarLocationPath = null;
		IPath sourceLocationPath = null;

		try {
			IVMInstall i = JavaRuntime.getVMInstall(project);
			if( i == null ) {
				i = JavaRuntime.getDefaultVMInstall();
			}

			if( FXVersionUtil.getFxVersion(i) != FXVersion.FX9 ) {
				return getFxJarPath(i);
			}
			return null;
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new IPath[] { jarLocationPath, javadocLocation, antJarLocationPath, sourceLocationPath };
	}


	public static IPath[] getSwtFxJarPath(IVMInstall i) {
		File installDir = i.getInstallLocation();
		IPath[] checkPaths = {
				// JDK 8
				new Path(installDir.getAbsolutePath()).append("jre").append("lib").append("jfxswt.jar"),
				new Path(installDir.getAbsolutePath()).append("lib").append("jfxswt.jar"), // JRE
				new Path(installDir.getAbsolutePath()).append("lib").append("javafx-swt.jar")
			};

		IPath jarLocationPath = null;
		IPath javadocLocation = null;
		IPath sourceLocationPath = null;

		jarLocationPath = checkPaths[0];

		if( ! jarLocationPath.toFile().exists() ) {
			for( IPath p : checkPaths ) {
				if( p.toFile().exists() ) {
					jarLocationPath = p;
					break;
				}
			}
		}

		if( jarLocationPath.toFile().exists() ) {
			sourceLocationPath = new Path(installDir.getAbsolutePath()).append("javafx-src.zip");

			return new IPath[] { jarLocationPath, javadocLocation, sourceLocationPath };
		}

		return null;
	}

	public static IPath[] getFxJarPath(IVMInstall i) {
		for( LibraryLocation l : JavaRuntime.getLibraryLocations(i) ) {
			if( "jfxrt.jar".equals(l.getSystemLibraryPath().lastSegment()) ) {
				return null;
			}
		}

		IPath jarLocationPath = null;
		IPath javadocLocation = null;
		IPath antJarLocationPath = null;
		IPath sourceLocationPath = null;

		File installDir = i.getInstallLocation();

		IPath[] checkPaths = {
			// Java 7
			new Path(installDir.getAbsolutePath()).append("jre").append("lib").append("jfxrt.jar"),
			new Path(installDir.getAbsolutePath()).append("lib").append("jfxrt.jar") // JRE

		};

		jarLocationPath = checkPaths[0];

		if( ! jarLocationPath.toFile().exists() ) {
			for( IPath p : checkPaths ) {
				if( p.toFile().exists() ) {
					jarLocationPath = p;
					break;
				}
			}
		}

		if( ! jarLocationPath.toFile().exists() ) {
			StringBuilder error = new StringBuilder("Unable to detect JavaFX jar for JRE ").append(i.getName());
			error.append(System.lineSeparator()).append("	JRE: ").append(installDir.getAbsolutePath());
			error.append(System.lineSeparator()).append("	Checked paths:");
			for( IPath p : checkPaths ) {
				error.append(System.lineSeparator()).append("		" + p.toFile().getAbsolutePath());
			}

			return null;
		}

		javadocLocation = new Path(installDir.getParentFile().getAbsolutePath()).append("docs").append("api"); //TODO Not shipped yet
		if( ! javadocLocation.toFile().exists() ) {
			IPath p = new Path(System.getProperty("user.home")).append("javafx-api-"+ i.getName()).append("docs").append("api");
			if( p.toFile().exists() ) {
				javadocLocation = p;
			}
		}

		antJarLocationPath = new Path(installDir.getParent()).append("lib").append("ant-javafx.jar");

		sourceLocationPath = new Path(installDir.getAbsolutePath()).append("javafx-src.zip");

		if( ! sourceLocationPath.toFile().exists() ) {
			sourceLocationPath = null;
		}

		return new IPath[] { jarLocationPath, javadocLocation, antJarLocationPath, sourceLocationPath };
	}
}