/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.commands;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;

public class VmCommand {
	private VmCommand() {}

	public static final String GET_ALL_INSTALL_COMMAND_ID = "java.vm.getAllInstalls";

	/**
	 * List all available VM installs on the machine.
	 */
	public static final List<VmInstall> getAllVmInstalls() {
		List<VmInstall> vmInstallList = new ArrayList<>();
		IVMInstallType[] vmInstallTypes = JavaRuntime.getVMInstallTypes();
		for (IVMInstallType vmInstallType : vmInstallTypes) {
			IVMInstall[] vmInstalls = vmInstallType.getVMInstalls();
			for (IVMInstall vmInstall : vmInstalls) {
				VmInstall vm = new VmInstall(
						vmInstallType.getName(),
						vmInstall.getName(),
						vmInstall.getInstallLocation().getAbsolutePath()
				);
				if (vmInstall instanceof IVMInstall2) {
					vm.version = ((IVMInstall2) vmInstall).getJavaVersion();
				}
				vmInstallList.add(vm);
			}
		}
		return vmInstallList;
	}

	public static final class VmInstall {
		public String typeName;
		public String name;
		public String path;
		public String version;

		public VmInstall(String typeName, String name, String path) {
			this.typeName = typeName;
			this.name = name;
			this.path = path;
		}
	}
}
