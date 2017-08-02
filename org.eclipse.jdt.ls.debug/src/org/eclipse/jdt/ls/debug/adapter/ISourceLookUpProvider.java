package org.eclipse.jdt.ls.debug.adapter;

import java.util.Map;

public interface ISourceLookUpProvider {
    void initializeContext(Map<String, Object> props);

    String[] getFullyQualifiedName(String sourceFilePath, int[] lines, int[] columns);

    String getSourceFileURI(String fullyQualifiedName);

    String getSourceContents(String uri);
}
