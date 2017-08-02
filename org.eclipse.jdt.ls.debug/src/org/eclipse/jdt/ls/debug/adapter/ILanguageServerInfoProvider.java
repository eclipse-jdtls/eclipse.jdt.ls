package org.eclipse.jdt.ls.debug.adapter;

import org.eclipse.jdt.ls.debug.internal.DebugSession;

public interface ILanguageServerInfoProvider {
    String getFullyQualifiedName(String sourceFilePath, int line, int column);

    String getSourceFileURI(String fullyQualifiedName, DebugSession debugSession);

    String getSourceContents(String uri);

    com.sun.jdi.VirtualMachineManager getVirtualMachineManager();
}
