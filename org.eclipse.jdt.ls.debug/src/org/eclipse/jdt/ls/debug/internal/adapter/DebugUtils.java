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

package org.eclipse.jdt.ls.debug.internal.adapter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpointManager;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.JavaLineBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

public class DebugUtils {

    /**
     * Converts VSCode source breakpoint to java breakpoint.
     */
    public static void addBreakpoint(Types.Source source, Types.SourceBreakpoint[] lines, IBreakpointManager manager) {
        Path sourcePath = Paths.get(source.path);
        ICompilationUnit element = JDTUtils.resolveCompilationUnit(sourcePath.toUri());
        List<IBreakpoint> javaBreakpoints = new ArrayList<>();
        try {
            for (Types.SourceBreakpoint bp : lines) {
                String fqn = null;
                int offset = JsonRpcHelpers.toOffset(element.getBuffer(), bp.line, 0);
                IJavaElement javaElement = element.getElementAt(offset);
                if (javaElement instanceof IMember) {
                    IType type = ((IMember) javaElement).getDeclaringType();
                    fqn = type.getFullyQualifiedName();
                } 
                if (fqn != null) {
                    IBreakpoint linebp = new JavaLineBreakpoint(fqn, bp.line, -1);
                    javaBreakpoints.add(linebp);
                }
            }
        } catch (Exception e) {
            Logger.logException("Add breakpoint exception", e);
        }
        
        // TODO Need some logic here to check the delta breakpoints
        // in BreakpointManager
        manager.addBreakpoints(sourcePath.normalize().toString(), javaBreakpoints.toArray(new IBreakpoint[0]), false);
    }

    /**
     * Search the absolute path of the java file under the specified source path directory.
     * @param sourcePath
     *                  the project source path directories
     * @param sourceName
     *                  the java file path
     * @return the absolute file path
     */
    public static String sourceLookup(String[] sourcePath, String sourceName) {
        for (String path : sourcePath) {
            String fullpath = Paths.get(path, sourceName).toString();
            if (new File(fullpath).isFile()) {
                return fullpath;
            }
        }
        return null;
    }
    
}
