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
	 * The IPath portable string value.
	 */
	private String nodePath;

	/**
	 * The nodeId to get the ClasspathNode list.
	 */
	private String nodeId;

	public ClasspathQuery() {
	}

	public ClasspathQuery(String projectUri) {
		this.projectUri = projectUri;
	}

	public ClasspathQuery(String projectUri, String nodePath) {
		this.projectUri = projectUri;
		this.nodePath = nodePath;
	}

	public ClasspathQuery(String projectUri, String nodePath, String nodeId) {
		this.projectUri = projectUri;
		this.nodePath = nodePath;
		this.nodeId = nodeId;
	}

	public String getProjectUri() {
		return projectUri;
	}

	public void setProjectUri(String projectUri) {
		this.projectUri = projectUri;
	}

	public String getNodePath() {
		return nodePath;
	}

	public void setNodePtah(String itemPath) {
		this.nodePath = itemPath;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
}
