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

import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchRequestor;

class MethodReferencesSearchRequestor extends SearchRequestor {
    private CallSearchResultCollector fSearchResults;
    private boolean fRequireExactMatch = true;

    MethodReferencesSearchRequestor() {
        fSearchResults = new CallSearchResultCollector();
    }

    public Map<String, MethodCall> getCallers() {
        return fSearchResults.getCallers();
    }

    @Override
	public void acceptSearchMatch(SearchMatch match) {
        if (fRequireExactMatch && (match.getAccuracy() != SearchMatch.A_ACCURATE)) {
            return;
        }

        if (match.isInsideDocComment()) {
            return;
        }

        if (match.getElement() != null && match.getElement() instanceof IMember) {
            IMember member= (IMember) match.getElement();
            switch (member.getElementType()) {
                case IJavaElement.METHOD:
                case IJavaElement.TYPE:
                case IJavaElement.FIELD:
                case IJavaElement.INITIALIZER:
                    fSearchResults.addMember(member, member, match.getOffset(), match.getOffset()+match.getLength());
                    break;
            }
        }
    }
}
