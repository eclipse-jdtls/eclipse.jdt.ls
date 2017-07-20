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
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpointManager;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.FakeJavaLineBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.JavaLineBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

public class DebugUtils {

    /**
     * According to source breakpoint, set breakpoint info to debugee VM.
     */
    public static List<Types.Breakpoint> addBreakpoint(Types.Source source, Types.SourceBreakpoint[] lines,
            boolean sourceModified, IBreakpointManager manager) {
        List<Types.Breakpoint> resBreakpoints = new ArrayList<>();
        List<IBreakpoint> javaBreakpoints = new ArrayList<>();
        Path sourcePath = Paths.get(source.path);
        ICompilationUnit element = JDTUtils.resolveCompilationUnit(sourcePath.toUri());
        
        for (Types.SourceBreakpoint bp : lines) {
            boolean valid = false;
            try {
                String fqn = null;
                int offset = JsonRpcHelpers.toOffset(element.getBuffer(), bp.line, 0);
                IJavaElement javaElement = element.getElementAt(offset);
                if (javaElement instanceof SourceField || javaElement instanceof SourceMethod) {
                    IType type = ((IMember) javaElement).getDeclaringType();
                    fqn = type.getFullyQualifiedName();
                } else if (javaElement instanceof SourceType) {
                    fqn = ((SourceType) javaElement).getFullyQualifiedName();
                }

                if (fqn != null) {
                    javaBreakpoints.add(new JavaLineBreakpoint(fqn, bp.line, -1));
                    valid = true;
                }
            } catch (Exception e) {
                Logger.logException("Add breakpoint exception", e);
            }
            if (!valid) {
                javaBreakpoints.add(new FakeJavaLineBreakpoint(null, bp.line, -1));
            }
        }

        IBreakpoint[] added = manager.addBreakpoints(sourcePath.normalize().toString(), javaBreakpoints.toArray(new IBreakpoint[0]), sourceModified);
        for (IBreakpoint add : added) {
            resBreakpoints.add(new Types.Breakpoint(add.getId(), add.isVerified(), ((JavaLineBreakpoint) add).getLineNumber(), ""));
        }
        
        return resBreakpoints;
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
