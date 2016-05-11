package org.jboss.tools.vscode.java.managers;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.JREContainerInitializer;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.java.managers.ProjectsManager.CHANGE_TYPE;

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
			JsonRpcConnection.log("Number of created projects " + projects.size());
			return projects;
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
