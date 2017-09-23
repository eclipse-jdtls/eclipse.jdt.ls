/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;

/**
 * Interface for command handler to decompile bytecode
 */
public interface IDecompilerCommandHandler {
	/**
	 * Decompile bytecode into source code.
	 *
	 * @param classFile
	 *            the class file to decompile
	 * @param configuration
	 *            optional configuration to pass to the decompiler
	 * @param monitor
	 *            monitor of the activity progress
	 * @return decompiled bytecode or <code>null</code>
	 * @throws Exception
	 */
	String decompile(IClassFile classFile, String configuration, IProgressMonitor monitor) throws Exception;
}
