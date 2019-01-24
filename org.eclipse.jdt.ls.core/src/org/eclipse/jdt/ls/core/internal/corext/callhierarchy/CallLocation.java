/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 *          (report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.callhierarchy;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

public class CallLocation implements IAdaptable {
    public static final int UNKNOWN_LINE_NUMBER= -1;
    private IMember fMember;
    private IMember fCalledMember;
    private int fStart;
    private int fEnd;

    private String fCallText;
    private int fLineNumber;

    public CallLocation(IMember member, IMember calledMember, int start, int end, int lineNumber) {
        this.fMember = member;
        this.fCalledMember = calledMember;
        this.fStart = start;
        this.fEnd = end;
        this.fLineNumber= lineNumber;
    }

    /**
     * @return IMethod
     */
    public IMember getCalledMember() {
        return fCalledMember;
    }

    /**
     *
     */
    public int getEnd() {
        return fEnd;
    }

    public IMember getMember() {
        return fMember;
    }

    /**
     *
     */
    public int getStart() {
        return fStart;
    }

    public int getLineNumber() {
    	initCallTextAndLineNumber();
        return fLineNumber;
    }

    public String getCallText() {
    	initCallTextAndLineNumber();
        return fCallText;
    }

    private void initCallTextAndLineNumber() {
    	if (fCallText != null) {
			return;
		}

        IBuffer buffer= getBufferForMember();
        if (buffer == null || buffer.getLength() < fEnd) { //binary, without source attachment || buffer contents out of sync (bug 121900)
        	fCallText= ""; //$NON-NLS-1$
        	fLineNumber= UNKNOWN_LINE_NUMBER;
        	return;
        }

        fCallText= buffer.getText(fStart, (fEnd - fStart));

        if (fLineNumber == UNKNOWN_LINE_NUMBER) {
            Document document= new Document(buffer.getContents());
            try {
                fLineNumber= document.getLineOfOffset(fStart) + 1;
            } catch (BadLocationException e) {
                JavaPlugin.log(e);
            }
        }
    }

    /**
     * Returns the IBuffer for the IMember represented by this CallLocation.
     *
     * @return IBuffer for the IMember or null if the member doesn't have a buffer (for
     *          example if it is a binary file without source attachment).
     */
    private IBuffer getBufferForMember() {
        IBuffer buffer = null;
        try {
            IOpenable openable = fMember.getOpenable();
            if (openable != null && fMember.exists()) {
                buffer = openable.getBuffer();
            }
        } catch (JavaModelException e) {
            JavaPlugin.log(e);
        }
        return buffer;
    }

    @Override
	public String toString() {
        return getCallText();
    }

    @Override
	@SuppressWarnings("unchecked")
	public <T> T getAdapter(Class<T> adapter) {
        if (IJavaElement.class.isAssignableFrom(adapter)) {
            return (T) getMember();
        }

        return null;
    }
}
