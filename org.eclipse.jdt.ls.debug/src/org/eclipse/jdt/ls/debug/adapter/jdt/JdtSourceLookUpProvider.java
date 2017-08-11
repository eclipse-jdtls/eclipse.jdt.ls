package org.eclipse.jdt.ls.debug.adapter.jdt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.BinaryMember;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.debug.DebugException;
import org.eclipse.jdt.ls.debug.adapter.ISourceLookUpProvider;
import org.eclipse.jdt.ls.debug.internal.JavaDebuggerServerPlugin;
import org.eclipse.jdt.ls.debug.internal.Logger;

public class JdtSourceLookUpProvider implements ISourceLookUpProvider {
    private HashMap<String, Object> context = new HashMap<String, Object>();

    @Override
    public void initialize(Map<String, Object> props) {
        if (props == null) {
            throw new IllegalArgumentException("argument is null");
        }
        context.putAll(props);
    }

    @Override
    public String[] getFullyQualifiedName(String uri, int[] lines, int[] columns) throws DebugException {
        if (uri == null) {
            throw new IllegalArgumentException("sourceFilePath is null");
        }
        if (lines == null) {
            throw new IllegalArgumentException("lines is null");
        }
        if (columns == null) {
            columns = new int[lines.length];
        } else if (lines.length != columns.length) {
            throw new IllegalArgumentException("the count of lines and columns don't match!");
        }

        String[] fqns = new String[lines.length];
        ITypeRoot typeRoot = JDTUtils.resolveCompilationUnit(uri);
        if (typeRoot == null) {
            typeRoot = JDTUtils.resolveClassFile(uri);
        }

        for (int i = 0; i < lines.length; i++) {
            String fqn = null;
            if (typeRoot != null) {
                try {
                    int offset = JsonRpcHelpers.toOffset(typeRoot.getBuffer(), lines[i], columns[i]);
                    IJavaElement javaElement = typeRoot.getElementAt(offset);
                    if (javaElement instanceof SourceField || javaElement instanceof SourceMethod
                            || javaElement instanceof BinaryMember) {
                        IType type = ((IMember) javaElement).getDeclaringType();
                        fqn = type.getFullyQualifiedName();
                    } else if (javaElement instanceof SourceType) {
                        fqn = ((SourceType) javaElement).getFullyQualifiedName();
                    }
                } catch (JavaModelException e) {
                    Logger.logException("Failed to parse the java element at line " + lines[i], e);
                    throw new DebugException(
                            String.format("Failed to parse the java element at line %d. Reason: %s", lines[i], e.getMessage()),
                            e);
                }
            }
            fqns[i] = fqn;
        }
        return fqns;
    }

    @Override
    public String getSourceFileURI(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            throw new IllegalArgumentException("fullyQualifiedName is null");
        }
        String projectName = (String)context.get(Constants.PROJECTNAME);
        try {
            IJavaSearchScope searchScope = projectName != null
                    ? JDTUtils.createSearchScope(getJavaProjectFromName(projectName))
                    : SearchEngine.createWorkspaceScope();
            SearchPattern pattern = SearchPattern.createPattern(
                    fullyQualifiedName,
                    IJavaSearchConstants.TYPE,
                    IJavaSearchConstants.DECLARATIONS,
                    SearchPattern.R_EXACT_MATCH);
            ArrayList<String> uris = new ArrayList<String>();
            SearchRequestor requestor = new SearchRequestor() {
                @Override
                public void acceptSearchMatch(SearchMatch match) {
                    Object element = match.getElement();
                    if (element instanceof IType) {
                        IType type = (IType)element;
                        uris.add(type.isBinary() ? JDTUtils.getFileURI(type.getClassFile()) : JDTUtils.getFileURI(type.getResource()));
                    }
                }
            };
            SearchEngine searchEngine = new SearchEngine();
            searchEngine.search(
                    pattern,
                    new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                    searchScope,
                    requestor,
                    null /* progress monitor */);
            return uris.size() == 0 ? null : uris.get(0);
        } catch (CoreException e) {
            Logger.logException("Failed to parse java project", e);
        }
        return null;
    }

    @Override
    public String getSourceContents(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri is null");
        }
        IClassFile cf = JDTUtils.resolveClassFile(uri);
        return getContents(cf);
    }

    private String getContents(IClassFile cf) {
        String source = null;
        if (cf != null) {
            try {
                IBuffer buffer = cf.getBuffer();
                if (buffer != null) {
                    source = buffer.getContents();
                }
                if (source == null) {
                    source = JDTUtils.disassemble(cf);
                }
            } catch (JavaModelException e) {
                Logger.logException("Failed to parse the source contents of the class file", e);
            }
            if (source == null) {
                source = "";
            }
        }
        return source;
    }

    private IJavaProject getJavaProjectFromName(String projectName) throws CoreException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (!project.exists()) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID, "Not an existed project."));
        }
        if (!project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID, "Not a project with java nature."));
        }
        IJavaProject javaProject = JavaCore.create(project);
        return javaProject;
    }
}
