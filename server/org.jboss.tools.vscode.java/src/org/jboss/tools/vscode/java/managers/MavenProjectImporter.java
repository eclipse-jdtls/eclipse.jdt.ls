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
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.osgi.util.NLS;

public class MavenProjectImporter {

	private static final String POM_FILE = "pom.xml";
	private final File rootFolder;

	public MavenProjectImporter(File projectFolder) {
		this.rootFolder = projectFolder;
	}

	
	public List<IProject> importToWorkspace()
			throws CoreException, InterruptedException {
		MavenPluginActivator mavenPlugin = MavenPluginActivator.getDefault();
		MavenConfigurationImpl configurationImpl = (MavenConfigurationImpl)mavenPlugin.getMavenConfiguration();
		configurationImpl.setDownloadSources(true);
		IProjectConfigurationManager configurationManager = mavenPlugin.getProjectConfigurationManager();
		
		MavenModelManager modelManager = mavenPlugin.getMavenModelManager();
		Set<MavenProjectInfo> projectInfos = getMavenProjects(getProjectDirectory(), modelManager);
		ProjectImportConfiguration projectImportConfiguration = new ProjectImportConfiguration();
		List<IMavenProjectImportResult> importResults =
				configurationManager.importProjects(projectInfos, projectImportConfiguration, new NullProgressMonitor());
		
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

	private Set<MavenProjectInfo> getMavenProjects(File directory, MavenModelManager modelManager) throws InterruptedException {
		LocalProjectScanner scanner = new LocalProjectScanner(directory.getParentFile(), directory.toString(), false,
				modelManager);
		scanner.run(new NullProgressMonitor());
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
