/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.debug.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AdapterUtils {

    /**
     * Search the absolute path of the java file under the specified source path directory.
     * @param sourcePaths
     *                  the project source directories
     * @param sourceName
     *                  the java file path
     * @return the absolute file path
     */
    public static String sourceLookup(String[] sourcePaths, String sourceName) {
        for (String path : sourcePaths) {
            Path fullpath = Paths.get(path, sourceName);
            if (Files.isRegularFile(fullpath)) {
                return fullpath.toString();
            }
        }
        return null;
    }

}
