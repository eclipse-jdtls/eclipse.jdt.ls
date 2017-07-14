/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.importer.pde.internal;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.AbstractProjectImporter;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetHandle;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.core.target.LoadTargetDefinitionJob;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class PDEProjectImporter extends AbstractProjectImporter {

	public static final String CONFIG_FILENAME = "javaConfig.json";

	private File getPluginWorkspaceFile() {
		return new File(rootFolder, CONFIG_FILENAME);
	}

	@Override
	public int applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (getPluginWorkspaceFile().exists()) {
			return 1000;
		}
		return 0;
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		try {
			String targetPlatform = null;
			List<String> projects = new ArrayList<>();

			FileReader fileReader = new FileReader(getPluginWorkspaceFile());
			try {
				JsonReader reader = new Gson().newJsonReader(fileReader);
				reader.beginObject();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (name.equals("targetPlatform")) {
						targetPlatform = reader.nextString();
					} else if (name.equals("projects")) {
						reader.beginArray();
						while (reader.hasNext()) {
							projects.add(reader.nextString());
						}
						reader.endArray();
					} else {
						reader.skipValue();
					}
				}
			} finally {
				fileReader.close();
			}
			initializeProjects(targetPlatform, projects, monitor);

		} catch (IOException e) {
			IStatus status = new Status(IStatus.ERROR, PDEImporterActivator.PLUGIN_ID, "Problems reading " + CONFIG_FILENAME, e);
			throw new CoreException(status);
		}

	}

	private void initializeProjects(String targetPlatform, List<String> projects, IProgressMonitor m) throws CoreException {
		SubMonitor monitor = SubMonitor.convert(m, projects.size() + 50);

		// set target platform
		ITargetPlatformService service = PDEImporterActivator.acquireService(ITargetPlatformService.class);
		if (targetPlatform != null) {
			monitor.setTaskName("Loading target platform...");

			// increase the connection timeouts for slow connections
			ensureMimimalTimeout("sun.net.client.defaultConnectTimeout", 10000);
			ensureMimimalTimeout("sun.net.client.defaultReadTimeout", 600000);

			URI projectFolderURI = new File(rootFolder, targetPlatform).toURI();
			ITargetHandle targetHandle = service.getTarget(projectFolderURI);
			ITargetDefinition targetDefinition = targetHandle.getTargetDefinition();
			new LoadTargetDefinitionJob(targetDefinition).runInWorkspace(monitor.split(50));
		}

		// import projects
		EclipseProjectImporter importer = new EclipseProjectImporter();
		for (String project : projects) {
			File projectFolder = new File(rootFolder, project);
			if (projectFolder.exists()) {
				importer.importDir(projectFolder.toPath(), monitor.split(1));
			} else {
				PDEImporterActivator.logInfo("Project " + projectFolder.toPath() + " does not exist. Ignoring.");
			}
		}
	}

	private void ensureMimimalTimeout(String property, int min) {
		String current = System.getProperty(property);
		if (parseInt(current, 0) < min) {
			System.setProperty(property, String.valueOf(min));
		}
	}

	private int parseInt(String value, int dflt) {
		if (value != null) {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return dflt;
	}

	@Override
	public void reset() {
	}

}
