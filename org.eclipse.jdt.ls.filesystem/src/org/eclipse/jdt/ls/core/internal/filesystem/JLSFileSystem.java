/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.filesystem;

import java.net.URI;
import java.nio.file.Paths;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.filesystem.local.LocalFileSystem;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * JDT.LS's own implementation of file system to handle the 'file' scheme uri.
 * The purpose of this implementation is to allow the project metadata files (.project, .classpath, .settings/)
 * can be persisted out of the project root.
 */
public class JLSFileSystem extends LocalFileSystem {

    @Override
    public IFileStore getStore(IPath path) {
        if (JLSFsUtils.shouldStoreInMetadataArea(path)) {
            IPath containerPath = JLSFsUtils.getContainerPath(path);
            String projectName = JLSFsUtils.getProjectNameIfLocationIsProjectRoot(containerPath);
            if (projectName == null) {
                return new JLSFile(path.toFile());
            }
            IPath redirectedPath = JLSFsUtils.getMetaDataFilePath(projectName, path);
            if (redirectedPath != null) {
                return new JLSFile(redirectedPath.toFile()); 
            }
        }
        return new JLSFile(path.toFile());
    }

    @Override
    public IFileStore getStore(URI uri) {
        IPath path = filePathFromURI(uri.toString());
        return path == null ? super.getStore(uri) : getStore(path);
    }

	public static IPath filePathFromURI(String uriStr) {
		URI uri = URI.create(uriStr);
		return "file".equals(uri.getScheme()) ?
			Path.fromOSString(Paths.get(uri).toString()) :
			null;
	}

}
