package org.eclipse.jdt.ls.debug.adapter;

public interface IVirtualMachineManagerProvider {
    com.sun.jdi.VirtualMachineManager getVirtualMachineManager();
}
