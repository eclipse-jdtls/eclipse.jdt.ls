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

package org.eclipse.jdt.ls.debug.adapter.jdt;

import org.eclipse.jdt.ls.debug.adapter.IProviderContext;
import org.eclipse.jdt.ls.debug.adapter.ISourceLookUpProvider;
import org.eclipse.jdt.ls.debug.adapter.IVirtualMachineManagerProvider;
import org.eclipse.jdt.ls.debug.adapter.ProviderContext;

/**
 * <code>IProviderContext</code> creator using language server.
 * 
 */
public abstract class JdtProviderContextFactory {
    /**
     * Create an <code>IProviderContext</code>.
     * @return the <code>IProviderContext</code> instance.
     */
    public static IProviderContext createProviderContext() {
        IProviderContext context = new ProviderContext();
        context.registerProvider(ISourceLookUpProvider.class, new JdtSourceLookUpProvider());
        context.registerProvider(IVirtualMachineManagerProvider.class, new JdtVirtualMachineManagerProvider());
        return context;
    }
}
