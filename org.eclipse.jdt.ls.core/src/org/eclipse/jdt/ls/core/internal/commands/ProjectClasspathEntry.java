/*******************************************************************************
 * Copyright (c) 2024 Microsoft Corporation and others.
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

import java.util.Map;
import java.util.Objects;


public class ProjectClasspathEntry {

	private int kind;
	private String path;
	private String output;
	private Map<String, String> attributes;

	public ProjectClasspathEntry(int kind, String path, String output, Map<String, String> attributes) {
		this.kind = kind;
		this.path = path;
		this.output = output;
		this.attributes = attributes;
	}

	public int getKind() {
		return kind;
	}

	public void setKind(int kind) {
		this.kind = kind;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getOutput() {
		return output;
	}

	public void setOutput(String output) {
		this.output = output;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes;
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, path, output, attributes);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProjectClasspathEntry other = (ProjectClasspathEntry) obj;
		return kind == other.kind && Objects.equals(path, other.path) && Objects.equals(output, other.output) && Objects.equals(attributes, other.attributes);
	}
}
