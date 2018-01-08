/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */

package org.eclipse.jdt.ls.core.internal.commands;

/**
 * The query instance for fetching the ClasspathNode list from the language
 * server.
 */
public class ClasspathQuery {

	/**
	 * The project URI value.
	 */
	private String projectUri;

	/**
	 * The path value for the query. This value binding with the rootPath to unique
	 * determine the ClasspathNode.
	 */
	private String path;

	/**
	 * The rootPath value for the query. This value is optional if the path can
	 * unique determine the query ClasspathNode.
	 */
	private String rootPath;

	public ClasspathQuery() {
	}

	public ClasspathQuery(String projectUri) {
		this.projectUri = projectUri;
	}

	public ClasspathQuery(String projectUri, String path) {
		this.projectUri = projectUri;
		this.path = path;
	}

	public ClasspathQuery(String projectUri, String path, String rootNodePath) {
		this.projectUri = projectUri;
		this.path = path;
		this.rootPath = rootNodePath;
	}

	public String getProjectUri() {
		return projectUri;
	}

	public void setProjectUri(String projectUri) {
		this.projectUri = projectUri;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String nodePath) {
		this.path = nodePath;
	}

	public String getRootPath() {
		return rootPath;
	}

	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}
}
