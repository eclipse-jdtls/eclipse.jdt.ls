package org.eclipse.jdt.ls.debug.adapter;

import java.util.Map;

public interface IHasContext {

    void initializeContext(Map<String, Object> props);
}
