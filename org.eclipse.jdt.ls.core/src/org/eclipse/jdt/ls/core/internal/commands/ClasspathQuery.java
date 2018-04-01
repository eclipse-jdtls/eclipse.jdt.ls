/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.commands;

/**
 * The query object to get the project dependency information from the language
 * server.
 */
public class ClasspathQuery {

	/**
	 * The node kind that the query against.
	 */
	private ClasspathNodeKind kind;

	/**
	 * The project URI value.
	 */
	private String projectUri;

	/**
	 * The node path for the query. For the node path that cannot unique determines
	 * the query node such as the file, classfile and folder contained inside a
	 * package/jar, it need combine with the rootPath to uniquely identify the node.
	 */

	private String path;

	/**
	 * The rootPath value for the query. This value is optional if the path can
	 * uniquely determine the query node.
	 */
	private String rootPath;

	public ClasspathQuery() {
	}

	public ClasspathQuery(ClasspathNodeKind kind, String projectUri) {
		this.kind = kind;
		this.projectUri = projectUri;
	}

	public ClasspathQuery(ClasspathNodeKind kind, String projectUri, String path) {
		this.kind = kind;
		this.projectUri = projectUri;
		this.path = path;
	}

	public ClasspathQuery(ClasspathNodeKind kind, String projectUri, String path, String rootNodePath) {
		this.kind = kind;
		this.projectUri = projectUri;
		this.path = path;
		this.rootPath = rootNodePath;
	}

	public ClasspathNodeKind getKind() {
		return kind;
	}

	public void setKind(ClasspathNodeKind kind) {
		this.kind = kind;
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
