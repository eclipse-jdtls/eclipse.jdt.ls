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

import java.util.List;

import org.eclipse.core.runtime.IPath;

/**
 * Represent a ClasspathNode in the dependency view.
 */
public class ClasspathNode {

	/**
	 * The name of the ClasspathNode
	 */
	private String name;

	/**
	 * The module name of the ClasspathNode for Java 9 and above
	 */
	private String moduleName;

	/**
	 * The type of {@link IPath} portable string value
	 */
	private String path;

	/**
	 * The URI value of the ClasspathNode
	 */
	private String uri;

	/**
	 * ClasspathNode kind
	 */
	private ClasspathNodeKind kind;

	/**
	 * ClasspathNode children list
	 */
	private List<ClasspathNode> children;

	public ClasspathNode() {

	}

	public ClasspathNode(String name, String path, ClasspathNodeKind kind) {
		this.name = name;
		this.path = path;
		this.kind = kind;
	}

	public String getName() {
		return name;
	}

	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	public String getModuleName() {
		return moduleName;
	}

	public String getPath() {
		return path;
	}

	public ClasspathNodeKind getKind() {
		return kind;
	}

	public String getUri() {
		return this.uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public List<ClasspathNode> getChildren() {
		return this.children;
	}

	public void setChildren(List<ClasspathNode> children) {
		this.children = children;
	}
}