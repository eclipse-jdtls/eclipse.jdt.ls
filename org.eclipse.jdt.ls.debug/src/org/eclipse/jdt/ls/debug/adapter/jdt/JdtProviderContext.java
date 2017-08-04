package org.eclipse.jdt.ls.debug.adapter.jdt;

import org.eclipse.jdt.ls.debug.adapter.IProviderContext;
import org.eclipse.jdt.ls.debug.adapter.ISourceLookUpProvider;
import org.eclipse.jdt.ls.debug.adapter.IVirtualMachineManagerProvider;

public class JdtProviderContext implements IProviderContext {

    private ISourceLookUpProvider sourceLookUpProvider;
    private IVirtualMachineManagerProvider virtualMachineManagerProvider;
    
    @Override
    public ISourceLookUpProvider getSourceLookUpProvider() {
        return this.sourceLookUpProvider;
    }

    @Override
    public IVirtualMachineManagerProvider getVirtualMachineManagerProvider() {
        return this.virtualMachineManagerProvider;
    }

    public JdtProviderContext() {
        this.sourceLookUpProvider = new JdtSourceLookUpProvider();
        this.virtualMachineManagerProvider = new JdtVirtualMachineManagerProvider();
    }
}
