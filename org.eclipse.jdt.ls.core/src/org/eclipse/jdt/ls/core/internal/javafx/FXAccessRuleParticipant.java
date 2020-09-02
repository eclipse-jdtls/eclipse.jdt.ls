/*******************************************************************************
 * Copyright (c) 2013-2020 BestSolution.at and others.
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

import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.launching.environments.IAccessRuleParticipant;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;

/**
 *
 * Copied from
 * https://github.com/eclipse-efx/efxclipse-eclipse/blob/master/bundles/tooling/org.eclipse.fx.ide.jdt.core/src/org/eclipse/fx/ide/jdt/core/internal/FXAccessRuleParticipant.java
 *
 */
public class FXAccessRuleParticipant implements IAccessRuleParticipant {

	@Override
	public IAccessRule[][] getAccessRules(IExecutionEnvironment environment, IVMInstall vm, LibraryLocation[] libraries, IJavaProject project) {
		boolean fxOnExtPath = false;
		for( LibraryLocation loc : libraries ) {
			if( "jfxrt.jar".equals(loc.getSystemLibraryPath().lastSegment()) ) {
				fxOnExtPath = true;
				break;
			}
		}

		if( fxOnExtPath ) {
			IAccessRule[] rules = new IAccessRule[6];
			rules[0] = JavaCore.newAccessRule(new Path("javafx/**"), IAccessRule.K_ACCESSIBLE);
			rules[1] = JavaCore.newAccessRule(new Path("netscape/javascript/**"), IAccessRule.K_ACCESSIBLE);
			rules[2] = JavaCore.newAccessRule(new Path("com/sun/javafx/**"), IAccessRule.K_DISCOURAGED);
			rules[3] = JavaCore.newAccessRule(new Path("com/sun/glass/**"), IAccessRule.K_DISCOURAGED);
			rules[4] = JavaCore.newAccessRule(new Path("com/sun/media/jfxmedia/**"), IAccessRule.K_DISCOURAGED);
			rules[5] = JavaCore.newAccessRule(new Path("com/sun/prism/**"), IAccessRule.K_DISCOURAGED);

			IAccessRule[][] rv = new IAccessRule[libraries.length][];

			for( int i = 0; i < rv.length; i++ ) {
				rv[i] = rules;
			}

			return rv;
		} else if( FXVersionUtil.getFxVersion(vm) == FXVersion.FX9 ) {
			IAccessRule[] rules = new IAccessRule[2];
			rules[0] = JavaCore.newAccessRule(new Path("javafx/**"), IAccessRule.K_ACCESSIBLE);
			rules[1] = JavaCore.newAccessRule(new Path("netscape/javascript/**"), IAccessRule.K_ACCESSIBLE);

			IAccessRule[][] rv = new IAccessRule[libraries.length][];

			for( int i = 0; i < rv.length; i++ ) {
				rv[i] = rules;
			}

			return rv;
		}

		return new IAccessRule[0][0];
	}

}