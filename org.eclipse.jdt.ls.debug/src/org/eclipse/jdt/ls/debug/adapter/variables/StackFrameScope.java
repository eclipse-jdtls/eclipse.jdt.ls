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

package org.eclipse.jdt.ls.debug.adapter.variables;

import java.util.Objects;

import com.sun.jdi.StackFrame;

public class StackFrameScope {
    private final StackFrame stackFrame;
    private final String scope;
    private int hashCode;
    
    
    /**
     * Create a stack frame scope with the related stack frame.
     * 
     * @param stackFrame the JDI stack frame
     * @param scope the scope
     */
    public StackFrameScope(StackFrame stackFrame, String scope) {
        this.stackFrame = stackFrame;
        this.scope = scope;
        this.hashCode = this.getStackFrame().hashCode() & this.getScope().hashCode();

    }
    
    @Override
    public String toString() {
        return String.format("%s %s", String.valueOf(getStackFrame()), getScope());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StackFrameScope)) {
            return false;
        }
        final StackFrameScope other = (StackFrameScope) o;
        return (Objects.equals(this.getStackFrame(), other.getStackFrame()) && Objects.equals(this.getScope(), other.getScope()));
    }

    public StackFrame getStackFrame() {
        return stackFrame;
    }

    public String getScope() {
        return scope;
    }
}
