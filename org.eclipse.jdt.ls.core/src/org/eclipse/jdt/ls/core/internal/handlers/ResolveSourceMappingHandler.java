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

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class ResolveSourceMappingHandler {
    private static final Pattern SOURCE_PATTERN = Pattern.compile("([\\w$\\.]+\\/)?(([\\w$]+\\.)+[<\\w$>]+)\\(([\\w-$]+\\.java:\\d+)\\)");
    private static final JdtSourceLookUpProvider sourceProvider = new JdtSourceLookUpProvider();

    /**
     * Given a line of stacktrace, resolve the uri of the source file or class file.
     * 
     * @param lineText
     *              the line of the stacktrace.
     * @param projectNames
     *              A list of the project names that needs to search in. If the given list is empty,
     *              All the projects in the workspace will be searched.
     * 
     * @return the uri of the associated source file or class file.
     */
    public static String resolveStackTraceLocation(String lineText, List<String> projectNames) {
        if (lineText == null) {
            return null;
        }

        Matcher matcher = SOURCE_PATTERN.matcher(lineText);
        if (matcher.find()) {
            String methodField = matcher.group(2);
            String locationField = matcher.group(matcher.groupCount());
            String fullyQualifiedName = methodField.substring(0, methodField.lastIndexOf("."));
            String packageName = fullyQualifiedName.lastIndexOf(".") > -1 ? fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf(".")) : "";
            String[] locations = locationField.split(":");
            String sourceName = locations[0];
            String sourcePath = StringUtils.isBlank(packageName) ? sourceName
                    : packageName.replace('.', File.separatorChar) + File.separatorChar + sourceName;
            return sourceProvider.getSourceFileURI(fullyQualifiedName, sourcePath, projectNames);
        }

        return null;
    }
}
