package org.eclipse.jdt.ls.debug.adapter;

import java.util.Map;

public interface IProvider {

    void initialize(Map<String, Object> props);
}
