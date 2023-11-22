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

import org.eclipse.jdt.ls.core.internal.commands.VmCommand.VmInstall;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class VmCommandTest {
	@Test
	public void testGetAllVmInstalls() {
		List<VmInstall> allVmInstalls = VmCommand.getAllVmInstalls();
		assertTrue(allVmInstalls.size() > 0);

		assertTrue(allVmInstalls.stream()
				.anyMatch(vm -> vm.typeName.contains("org.eclipse.jdt.ls.core.internal.TestVMType"))
		);
	}
}
