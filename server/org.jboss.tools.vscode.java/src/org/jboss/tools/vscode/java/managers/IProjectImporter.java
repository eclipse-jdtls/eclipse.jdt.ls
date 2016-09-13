package org.jboss.tools.vscode.java.managers;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public interface IProjectImporter {

	void initialize(File rootFolder);
	
	boolean applies(IProgressMonitor monitor) throws InterruptedException, CoreException;

	List<IProject> importToWorkspace(IProgressMonitor monitor) throws InterruptedException, CoreException;

	void reset();
}
