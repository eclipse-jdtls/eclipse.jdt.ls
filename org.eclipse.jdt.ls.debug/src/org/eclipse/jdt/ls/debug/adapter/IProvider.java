package org.eclipse.jdt.ls.debug.adapter;

import java.util.HashMap;
import java.util.Map;

public interface IProvider {
    /**
     * Initialize this provider.
     * @param options the options
     */
    default void initialize(Map<String, Object> options) {

    }

    /**
     * Get the default options for this provider.
     *
     * @return The default options.
     */
    default Map<String, Object> getDefaultOptions() {
        return new HashMap<>();
    }
}
