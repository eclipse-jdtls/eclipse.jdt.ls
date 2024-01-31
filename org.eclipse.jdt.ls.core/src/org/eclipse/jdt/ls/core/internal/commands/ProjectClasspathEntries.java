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

import java.util.List;
import java.util.Objects;

public class ProjectClasspathEntries {

	public ProjectClasspathEntries(List<ProjectClasspathEntry> classpathEntries) {
		this.classpathEntries = classpathEntries;
	}

	private List<ProjectClasspathEntry> classpathEntries;

	public List<ProjectClasspathEntry> getClasspathEntries() {
		return classpathEntries;
	}

	public void setClasspathEntries(List<ProjectClasspathEntry> classpathEntries) {
		this.classpathEntries = classpathEntries;
	}

	@Override
	public int hashCode() {
		return Objects.hash(classpathEntries);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProjectClasspathEntries other = (ProjectClasspathEntries) obj;
		return Objects.equals(classpathEntries, other.classpathEntries);
	}
}