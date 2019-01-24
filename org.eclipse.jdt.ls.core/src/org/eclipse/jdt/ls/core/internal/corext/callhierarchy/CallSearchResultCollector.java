/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;

class CallSearchResultCollector {
    /**
     * A map from handle identifier ({@link String}) to {@link MethodCall}.
     */
    private Map<String, MethodCall> fCalledMembers;

    public CallSearchResultCollector() {
        this.fCalledMembers = createCalledMethodsData();
    }

    /**
     * @return a map from handle identifier ({@link String}) to {@link MethodCall}
     */
    public Map<String, MethodCall> getCallers() {
        return fCalledMembers;
    }

    protected void addMember(IMember member, IMember calledMember, int start, int end) {
        addMember(member, calledMember, start, end, CallLocation.UNKNOWN_LINE_NUMBER);
    }

    protected void addMember(IMember member, IMember calledMember, int start, int end, int lineNumber) {
        if ((member != null) && (calledMember != null)) {
            if (!isIgnored(calledMember)) {
                MethodCall methodCall = fCalledMembers.get(calledMember.getHandleIdentifier());

                if (methodCall == null) {
                    methodCall = new MethodCall(calledMember);
                    fCalledMembers.put(calledMember.getHandleIdentifier(), methodCall);
                }

                methodCall.addCallLocation(new CallLocation(member, calledMember, start,
                        end, lineNumber));
            }
        }
    }

    protected Map<String, MethodCall> createCalledMethodsData() {
        return new HashMap<>();
    }

    /**
     * Method isIgnored.
     * @param enclosingElement
     * @return boolean
     */
    private boolean isIgnored(IMember enclosingElement) {
        String fullyQualifiedName = getTypeOfElement(enclosingElement)
                                        .getFullyQualifiedName();

        return CallHierarchy.getDefault().isIgnored(fullyQualifiedName);
    }

    private IType getTypeOfElement(IMember element) {
        if (element.getElementType() == IJavaElement.TYPE) {
            return (IType) element;
        }

        return element.getDeclaringType();
    }
}
