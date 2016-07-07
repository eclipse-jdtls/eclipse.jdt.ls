package org.jboss.tools.vscode.java.managers;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class ProjectsManager {
	
	public enum CHANGE_TYPE { CREATED, CHANGED, DELETED};

	private static final String TMP_PROJECT_NAME = "tmpProject";

	public IProject getCurrentProject() {
		return getWorkspace().getRoot().getProject(TMP_PROJECT_NAME);
	}

	public List<IProject> createProject(final String projectName) {
		MavenProjectImporter importer = new MavenProjectImporter(new File(projectName));
		List<IProject> projects;
		try {
			projects = importer.importToWorkspace();
			JavaLanguageServerPlugin.logInfo("Number of created projects " + projects.size());
			return projects;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem importing to workspace", e);
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logInfo("Import cancelled");
		}
		return Collections.emptyList();
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
