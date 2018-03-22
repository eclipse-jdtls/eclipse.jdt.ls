/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

public class FileSystemWatcher {

	public static final int WATCH_KIND_DEFAULT = 7;
	/**
	 * The glob pattern to watch
	 */
	private String globPattern;

	/**
	 * The kind of events of interest. If omitted it defaults to WatchKind.Create |
	 * WatchKind.Change | WatchKind.Delete which is 7.
	 */
	private int kind;

	public FileSystemWatcher(String globPattern, int kind) {
		this.globPattern = globPattern;
		this.kind = kind;
	}

	public FileSystemWatcher() {
	}

	/**
	 * @return the globPattern
	 */
	public String getGlobPattern() {
		return globPattern;
	}

	/**
	 * @param globPattern
	 *            the globPattern to set
	 */
	public void setGlobPattern(String globPattern) {
		this.globPattern = globPattern;
	}

	/**
	 * @return the kind
	 */
	public int getKind() {
		return kind;
	}

	/**
	 * @param kind
	 *            the kind to set
	 */
	public void setKind(int kind) {
		this.kind = kind;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((globPattern == null) ? 0 : globPattern.hashCode());
		result = prime * result + kind;
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
		FileSystemWatcher other = (FileSystemWatcher) obj;
		if (globPattern == null) {
			if (other.globPattern != null) {
				return false;
			}
		} else if (!globPattern.equals(other.globPattern)) {
			return false;
		}
		if (kind != other.kind) {
			return false;
		}
		return true;
	}

}
