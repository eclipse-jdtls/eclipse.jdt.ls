/*******************************************************************************
 * Copyright (c) 2013-2020 BestSolution.at and others.
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
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logError;

import java.io.File;
import java.net.URL;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.launching.ILibraryLocationResolver;

/**
 *
 * Copied from
 * https://github.com/eclipse-efx/efxclipse-eclipse/blob/master/bundles/tooling/org.eclipse.fx.ide.jdt.core/src/org/eclipse/fx/ide/jdt/core/internal/FXLibraryLocationResolver.java
 *
 */
public class FXLibraryLocationResolver implements ILibraryLocationResolver {

	public FXLibraryLocationResolver() {
	}

	@Override
	public IPath getPackageRoot(IPath libraryPath) {
		return Path.EMPTY;
	}

	@Override
	public IPath getSourcePath(IPath libraryPath) {
		if( libraryPath.lastSegment().endsWith("jfxrt.jar") ) {
			File jarLocation = libraryPath.toFile();
			File jdkHome = jarLocation.getParentFile().getParentFile().getParentFile().getParentFile();
			IPath srcPath = new Path(jdkHome.getAbsolutePath()).append("javafx-src.zip");
			if( srcPath.toFile().exists() ) {
				return srcPath;
			}
		}
		return Path.EMPTY;
	}

	@Override
	public URL getJavadocLocation(IPath libraryPath) {
		if( libraryPath.lastSegment().endsWith("jfxrt.jar") ) {
			try {
				File jarLocation = libraryPath.toFile();
				File jdkHome = jarLocation.getParentFile().getParentFile().getParentFile().getParentFile();
				File javaDoc = new Path(jdkHome.getAbsolutePath()).append("docs").append("api").toFile();
				if( javaDoc.exists() ) {
					return javaDoc.toURI().toURL();
				}

				javaDoc = new Path(System.getProperty("user.home")).append("javafx8-api").append("docs").append("api").toFile();
				if( javaDoc.exists() ) {
					return javaDoc.toURI().toURL();
				}

				return new URL(BuildPathSupport.WEB_JAVADOC_LOCATION);
			} catch (Exception e) {
				logError("Failure while trying to detect JavaFX8 JavaDoc: " + e.getMessage());
			}
		}

		return null;
	}

	@Override
	public URL getIndexLocation(IPath libraryPath) {
		return null;
	}

}
