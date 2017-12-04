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

import org.eclipse.core.runtime.IPath;

public class ClasspathItem {

	public final static String PROJECT = "Project";

	public final static String CONTAINER = "Container";

	public final static String JAR = "Jar";

	public final static String PACKAGE = "Package";

	public final static String CLASSFILE = "Classfile";

	public final static String SOURCE = "Source";

	private String name;

	private String path;

	private String kind;

	private ClasspathItem[] children;

	public ClasspathItem(String name, IPath path, String kind) {
		this.name = name;
		this.path = path.toPortableString();
		this.kind = kind;
	}

	public String getName() {
		return name;
	}

	public String getPath() {
		return path;
	}

	public String getKind() {
		return kind;
	}

	public void setChildren(ClasspathItem[] children) {
		this.children = children;
	}

	public ClasspathItem[] getChildren() {
		return this.children;
	}
}