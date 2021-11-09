/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
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

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.filesystem.local.LocalFileSystem;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;

/**
 * JDT.LS's own implementation of file system to handle the 'file' scheme uri.
 * The purpose of this implementation is to allow the project metadata files (.project, .classpath, .settings/)
 * can be persisted out of the project root.
 */
public class JdtlsFileSystem extends LocalFileSystem {

    @Override
    public IFileStore getStore(IPath path) {
        if (JdtlsFsUtils.shouldStoreInWorkspaceStorage(path)) {
            IPath realPath = JdtlsFsUtils.getMetaDataFilePath("", path);
            if (realPath != null) {
                return new JdtlsFile(realPath.toFile()); 
            }
        }

        return new JdtlsFile(path.toFile());
    }

    @Override
    public IFileStore getStore(URI uri) {
        IPath path = ResourceUtils.filePathFromURI(uri.toString());
        return getStore(path);
    }
}
