package org.eclipse.jdt.ls.debug.adapter;

import org.eclipse.jdt.ls.debug.DebugException;

public interface ISourceLookUpProvider extends IProvider {

    String[] getFullyQualifiedName(String uri, int[] lines, int[] columns) throws DebugException;

    String getSourceFileURI(String fullyQualifiedName);

    String getSourceContents(String uri);
}
