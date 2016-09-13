package org.jboss.tools.vscode.java.managers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;

public class MavenProjectImporter extends AbstractProjectImporter {

	private static final String POM_FILE = "pom.xml";

	private Set<MavenProjectInfo> projectInfos = null;
	
	@Override
	public boolean applies(IProgressMonitor monitor) throws InterruptedException, CoreException {
		Set<MavenProjectInfo> files = getMavenProjectInfo(monitor);
		return files != null && !files.isEmpty();
	}
	
	synchronized Set<MavenProjectInfo> getMavenProjectInfo(IProgressMonitor monitor) throws InterruptedException {
		if (projectInfos == null) {
			projectInfos = collectMavenProjectInfo(monitor);
		}
		return projectInfos;
	}
	
	Set<MavenProjectInfo> collectMavenProjectInfo(IProgressMonitor monitor) throws InterruptedException {
		MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
		return getMavenProjects(getProjectDirectory(), modelManager, monitor);
	}
	
	@Override
	public void reset() {
		projectInfos = null;
	}
	
	@Override
	@SuppressWarnings("restriction")
	public List<IProject> importToWorkspace(IProgressMonitor monitor) throws CoreException, InterruptedException {
		MavenConfigurationImpl configurationImpl = (MavenConfigurationImpl)MavenPlugin.getMavenConfiguration();
		configurationImpl.setDownloadSources(true);
		IProjectConfigurationManager configurationManager = MavenPlugin.getProjectConfigurationManager();
		Set<MavenProjectInfo> files = getMavenProjectInfo(monitor); 
		ProjectImportConfiguration projectImportConfiguration = new ProjectImportConfiguration();
		List<IMavenProjectImportResult> importResults =
				configurationManager.importProjects(files, projectImportConfiguration, monitor);
		
		return toProjects(importResults);
	}

	private File getProjectDirectory() {
		return rootFolder;
	}


	private List<IProject> toProjects(List<IMavenProjectImportResult> importResults) {
		List<IProject> projects = new ArrayList<>();
		for (IMavenProjectImportResult importResult : importResults) {
			IProject project = importResult.getProject();
			if (project != null) {
				projects.add(importResult.getProject());
			}
		}

		return projects;
	}

	private Set<MavenProjectInfo> getMavenProjects(File directory, MavenModelManager modelManager, IProgressMonitor monitor) throws InterruptedException {
		LocalProjectScanner scanner = new LocalProjectScanner(directory.getParentFile(), directory.toString(), false, modelManager);
		scanner.run(monitor);
		return collectProjects(scanner.getProjects());
	}

	public boolean isMavenProject() {
		return  isMavenProject(getProjectDirectory());
	}
	
	private boolean isMavenProject(File dir) {
		if (!isReadable(dir)
				|| !dir.isDirectory()) {
			return false;
		}
		return isReadable(new File(dir, POM_FILE));
	}
	
	private boolean isReadable(File destination) {
		return destination != null
				&& destination.canRead();
	}
	
	public Set<MavenProjectInfo> collectProjects(
			Collection<MavenProjectInfo> projects) {
		return new LinkedHashSet<MavenProjectInfo>() {
			private static final long serialVersionUID = 1L;

			public Set<MavenProjectInfo> collectProjects(
					Collection<MavenProjectInfo> projects) {
				for (MavenProjectInfo projectInfo : projects) {
					add(projectInfo);
					collectProjects(projectInfo.getProjects());
				}
				return this;
			}
		}.collectProjects(projects);
	}

}
