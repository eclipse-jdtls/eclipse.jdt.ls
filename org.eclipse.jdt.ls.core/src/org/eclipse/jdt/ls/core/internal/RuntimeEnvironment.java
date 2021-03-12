/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import com.google.common.base.Strings;

/**
 *
 * @author snjeza
 *
 */
public class RuntimeEnvironment {

	private String name;
	private String path;
	private String javadoc;
	private String sources;
	private boolean isDefault;

	/**
	 * @return the isDefault
	 */
	public boolean isDefault() {
		return isDefault;
	}

	/**
	 * @param isDefault
	 *            the isDefault to set
	 */
	public void setDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getJavadoc() {
		return javadoc;
	}

	public void setJavadoc(String javadoc) {
		this.javadoc = javadoc;
	}

	public String getSources() {
		return sources;
	}

	public void setSources(String sources) {
		this.sources = sources;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		RuntimeEnvironment other = (RuntimeEnvironment) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	public boolean isValid() {
		return !Strings.isNullOrEmpty(name) && !Strings.isNullOrEmpty(path);
	}

	public File getInstallationFile() {
		if (isValid()) {
			return new File(path);
		}
		return null;
	}

	public URL getJavadocURL() {
		if (Strings.isNullOrEmpty(javadoc)) {
			return null;
		}
		URL url;
		try {
			url = new URL(javadoc);
		} catch (MalformedURLException e) {
			File file = new File(javadoc);
			if (file.exists() && file.isAbsolute()) {
				try {
					URI uri = file.toURI();
					uri = new URI(ResourceUtils.fixURI(uri));
					return uri.toURL();
				} catch (MalformedURLException | IllegalArgumentException | URISyntaxException e1) {
					JavaLanguageServerPlugin.logException(e1.getMessage(), e1);
				}
			}
			JavaLanguageServerPlugin.logInfo("Invalid javadoc: " + javadoc);
			return null;
		}
		return url;
	}

	public IPath getSourcePath() {
		if (!Strings.isNullOrEmpty(sources)) {
			return new Path(sources);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "JavaEnvironment [name=" + name + ", path=" + path + ", javadoc=" + javadoc + ", sources=" + sources + "]";
	}

}
