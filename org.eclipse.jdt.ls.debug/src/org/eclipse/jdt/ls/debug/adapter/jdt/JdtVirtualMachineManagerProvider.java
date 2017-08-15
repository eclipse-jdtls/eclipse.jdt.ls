package org.eclipse.jdt.ls.debug.adapter.jdt;

import java.util.Map;

import org.eclipse.jdi.Bootstrap;
import org.eclipse.jdt.ls.debug.adapter.IVirtualMachineManagerProvider;

import com.sun.jdi.VirtualMachineManager;

public class JdtVirtualMachineManagerProvider implements IVirtualMachineManagerProvider {

    @Override
    public VirtualMachineManager getVirtualMachineManager() {
        return Bootstrap.virtualMachineManager();
    }

    @Override
    public void initialize(Map<String, Object> props) {
    }
}
