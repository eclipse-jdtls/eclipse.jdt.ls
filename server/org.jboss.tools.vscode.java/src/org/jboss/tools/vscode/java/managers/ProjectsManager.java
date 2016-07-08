package org.jboss.tools.vscode.java.managers;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class ProjectsManager {
	
	public enum CHANGE_TYPE { CREATED, CHANGED, DELETED};

	private static final String TMP_PROJECT_NAME = "tmpProject";

	public IProject getCurrentProject() {
		return getWorkspace().getRoot().getProject(TMP_PROJECT_NAME);
	}

	public IStatus createProject(final String projectName, List<IProject> resultingProjects, IProgressMonitor monitor) {
		MavenProjectImporter importer = new MavenProjectImporter(new File(projectName));
		try {
			List<IProject> projects = importer.importToWorkspace(monitor);
			JavaLanguageServerPlugin.logInfo("Number of created projects " + projects.size());
			resultingProjects.addAll(projects);
			return Status.OK_STATUS;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem importing to workspace", e);
			return new Status(IStatus.ERROR, "", "Import failed: " + e.getMessage());
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logInfo("Import cancelled");
			return Status.CANCEL_STATUS;
		}
	}

	private IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	public void fileChanged(String uri, CHANGE_TYPE changeType) {
		try {
			this.getCurrentProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem refreshing workspace", e);
		}
	}

}
