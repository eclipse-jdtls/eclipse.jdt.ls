/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.AbstractVMInstallType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
import org.osgi.framework.Bundle;


public class TestVMType extends AbstractVMInstallType {

	private static final String VMTYPE_ID = "org.eclipse.jdt.ls.core.internal.TestVMType";
	private static final String RTSTUBS18_JAR = "rtstubs18.jar";
	private static final String FAKE_JDK = "/fakejdk";

	public static void setTestJREAsDefault() throws CoreException {
		IVMInstallType vmInstallType = JavaRuntime.getVMInstallType(VMTYPE_ID);
		IVMInstall testVMInstall = vmInstallType.findVMInstall("1.8");

		if (!testVMInstall.equals(JavaRuntime.getDefaultVMInstall())) {
			// set the 1.8 test JRE as the new default JRE
			JavaRuntime.setDefaultVMInstall(testVMInstall, new NullProgressMonitor());
		}
		// update all environments compatible to use the test JRE
		IExecutionEnvironmentsManager manager = JavaRuntime.getExecutionEnvironmentsManager();
		IExecutionEnvironment[] environments = manager.getExecutionEnvironments();
		for (IExecutionEnvironment environment : environments) {
			IVMInstall[] compatibleVMs = environment.getCompatibleVMs();
			for (IVMInstall compatibleVM : compatibleVMs) {
				if (VMTYPE_ID.equals(compatibleVM.getVMInstallType().getId()) && !compatibleVM.equals(environment.getDefaultVM())) {
					environment.setDefaultVM(compatibleVM);
				}
			}
		}
	}

	public TestVMType() {
		createVMInstall("1.8");
	}

	@Override
	public String getName() {
		return "TestVMInstall-" + getId();
	}

	@Override
	public IStatus validateInstallLocation(File installLocation) {
		return Status.OK_STATUS;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMInstallType#detectInstallLocation()
	 */
	@Override
	public File detectInstallLocation() {
		return getInstallLocation();
	}

	protected static File getInstallLocation() {
		Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
		try {
			URL url = FileLocator.toFileURL(bundle.getEntry(FAKE_JDK));
			File file = URIUtil.toFile(URIUtil.toURI(url));
			return file;
		} catch (IOException | URISyntaxException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMInstallType#getDefaultLibraryLocations(java.io.File)
	 */
	@Override
	public LibraryLocation[] getDefaultLibraryLocations(File installLocation) {
		// for now use the same stub JAR for all
		IPath path = Path.fromOSString(new File(installLocation, RTSTUBS18_JAR).getAbsolutePath());
		return new LibraryLocation[] { new LibraryLocation(path, Path.EMPTY, Path.EMPTY) };
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.AbstractVMInstallType#doCreateVMInstall(java.lang.String)
	 */
	@Override
	protected IVMInstall doCreateVMInstall(String id) {
		return new TestVMInstall(this, id);
	}


}

class TestVMInstall extends AbstractVMInstall {

	public TestVMInstall(IVMInstallType type, String id) {
		super(type, id);
		setNotify(false);
		setInstallLocation(TestVMType.getInstallLocation());
	}

	@Override
	public String getJavaVersion() {
		return getId();
	}

}
