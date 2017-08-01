package org.eclipse.jdt.ls.debug.adapter;

public interface ILanguageServerInfoProvider {
    String getFullyQualifiedName(String sourceFilePath, int line, int column);

    String getSourceFilePath(String fullyQualifiedName, String projectName);

    String getURI(String fullyQualifiedName, String projectName);

    String getSourceContents(String uri);

    String[] getClassPath(String mainClassFullyQualifiedName, String projectName);

    com.sun.jdi.VirtualMachineManager getVirtualMachineManager();
}
