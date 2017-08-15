package org.eclipse.jdt.ls.debug.adapter;

import java.util.HashMap;
import java.util.Map;

public class ProviderContext implements IProviderContext {

    private Map<Class<? extends IProvider>, IProvider> providerMap;
    
    public ProviderContext() {
        providerMap = new HashMap<>();  
    }
    
    /**
     * Get the registered provider with the interface type, <code>IllegalArgumentException</code> 
     * will raise if the provider is absent. The returned object is type-safe to be assigned to T since
     * registerProvider will check the compatibility, so suppress unchecked rule.  
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends IProvider> T getProvider(Class<T> clazz) {
        if (!providerMap.containsKey(clazz)) {
            throw new IllegalArgumentException(String.format("%s has not been registered.", clazz.getName()));
        }
        return (T) providerMap.get(clazz);
    }
    
    @Override
    public void registerProvider(Class<? extends IProvider> clazz, IProvider provider) {
        if (providerMap.containsKey(clazz)) {
            throw new IllegalArgumentException(String.format("%s has already been registered.", clazz.getName()));
        }
        if (!clazz.isInstance(provider)) {
            throw new IllegalArgumentException(String.format("The provider doesn't implement interface %s.", clazz.getName()));
        }
        providerMap.put(clazz, provider);
    }

    @Override
    public ISourceLookUpProvider getSourceLookUpProvider() {
        return getProvider(ISourceLookUpProvider.class);
    }

    @Override
    public IVirtualMachineManagerProvider getVirtualMachineManagerProvider() {
        return getProvider(IVirtualMachineManagerProvider.class);
    }
}
