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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
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
        try {
            for (Types.SourceBreakpoint bp : lines) {
                IType type = (IType) element.getElementAt(bp.line);
                if (type != null) {
                    IBreakpoint linebp = new JavaLineBreakpoint(type.getFullyQualifiedName(), bp.line, -1);
                    // TODO Need some logic here to check the delta breakpoints
                    // in BreakpointManager
                    manager.addBreakpoint(linebp);
                }
            }
        } catch (Exception e) {
            Logger.logException("Add breakpoint exception", e);
        }
    }

}
