/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation
 * 			(report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.callhierarchy;

import java.util.Collection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;

public interface IImplementorFinder {

    /**
     * Find implementors of the specified IType instance.
     */
    public abstract Collection<IType> findImplementingTypes(IType type,
        IProgressMonitor progressMonitor);

    /**
     * Find interfaces which are implemented by the specified IType instance.
     */
    public abstract Collection<IType> findInterfaces(IType type, IProgressMonitor progressMonitor);
}
