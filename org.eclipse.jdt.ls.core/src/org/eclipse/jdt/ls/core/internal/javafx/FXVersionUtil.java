/*******************************************************************************
 * Copyright (c) 2014-2020 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Tom Schindl<tom.schindl@bestsolution.at> - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javafx;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;

/**
 * Check the java version
 *
 * Copied from
 * https://github.com/eclipse-efx/efxclipse-eclipse/blob/master/bundles/tooling/org.eclipse.fx.ide.jdt.core/src/org/eclipse/fx/ide/jdt/core/FXVersionUtil.java
 */
public class FXVersionUtil {
	/**
	 * Check the version for the given java install
	 *
	 * @param i
	 *            the installation
	 * @return the value
	 */
	public static FXVersion getFxVersion(IVMInstall i) {
		if (i instanceof IVMInstall2 vmInstall) {
			final String javaVersion = vmInstall.getJavaVersion();
			if (javaVersion == null) {
				return FXVersion.UNKNOWN;
			}

			if (javaVersion.startsWith("1.8")) { //$NON-NLS-1$
				return FXVersion.FX8;
			} else if (javaVersion.startsWith("1.7")) { //$NON-NLS-1$
				return FXVersion.FX2;
			} else if( javaVersion.startsWith("9") ) { //$NON-NLS-1$
				return FXVersion.FX9;
			} else if( javaVersion.startsWith("11") ) { //$NON-NLS-1$
				return FXVersion.FX11;
			} else {
				return FXVersion.FX11PLUS;
			}
		}
		return FXVersion.UNKNOWN;
	}

}