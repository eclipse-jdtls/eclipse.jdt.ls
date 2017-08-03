package org.eclipse.jdt.ls.debug.adapter;

public interface IVirtualMachineManagerProvider extends IProvider {
    com.sun.jdi.VirtualMachineManager getVirtualMachineManager();
}
