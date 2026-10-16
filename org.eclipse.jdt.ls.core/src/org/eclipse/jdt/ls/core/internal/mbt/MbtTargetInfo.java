/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.mbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A build target in {@code mbt.json}.
 *
 * <p>Supports field name variants across different mbt.json generators.</p>
 */
public class MbtTargetInfo {

	// "targets" format uses "id" field
	private String id;

	// "compilerOptions" (namespaces format) or "javacOptions" (targets format)
	private List<String> compilerOptions;
	private List<String> javacOptions;

	// "jdk" (targets format) or "javaHome" (namespaces/classpath-extractor format)
	private String jdk;
	private String javaHome;

	private List<String> sources;
	private List<String> classes;
	private List<String> dependencyModules;
	private List<String> dependsOn;

	public String getId() {
		return id;
	}

	public List<String> getCompilerOptions() {
		if (compilerOptions != null) {
			return compilerOptions;
		}
		return javacOptions != null ? javacOptions : Collections.emptyList();
	}

	public String getJdk() {
		return jdk != null ? jdk : javaHome;
	}

	public List<String> getSources() {
		return sources != null ? sources : Collections.emptyList();
	}

	public List<String> getClasses() {
		return classes != null ? classes : Collections.emptyList();
	}

	public List<String> getDependencyModules() {
		return dependencyModules != null ? dependencyModules : Collections.emptyList();
	}

	public List<String> getDependsOn() {
		return dependsOn != null ? dependsOn : Collections.emptyList();
	}

	/**
	 * Moves a dependency from {@code dependencyModules} to {@code dependsOn}.
	 * Used after per-module cascade generation when a dependency module
	 * has been resolved as a source target.
	 */
	void promoteDependencyToTarget(String depId) {
		if (dependencyModules != null && dependencyModules.remove(depId)) {
			if (dependsOn == null) {
				dependsOn = new ArrayList<>();
			}
			if (!dependsOn.contains(depId)) {
				dependsOn.add(depId);
			}
		}
	}
}
