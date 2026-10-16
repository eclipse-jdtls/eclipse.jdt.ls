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

/**
 * An external dependency module in {@code mbt.json}.
 *
 * <p>Supports field name variants across different mbt.json generators:
 * <ul>
 *   <li>{@code "jar"} or {@code "path"} for the dependency JAR</li>
 *   <li>{@code "sources"} or {@code "source"} for the sources JAR</li>
 * </ul>
 */
public class MbtDependencyModuleInfo {

	private String id;

	// "jar" (namespaces format, file:// URI) or "path" (targets format, plain path)
	private String jar;
	private String path;

	// "sources" (namespaces format) or "source" (targets format)
	private String sources;
	private String source;

	public String getId() {
		return id;
	}

	/**
	 * Returns the JAR location (may be a {@code file://} URI or a plain path).
	 */
	public String getJar() {
		return jar != null ? jar : path;
	}

	/**
	 * Returns the sources JAR location, or {@code null} if unavailable.
	 */
	public String getSources() {
		return sources != null ? sources : source;
	}
}
