/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;

public class FakeDecompiler implements IDecompiler {

	public static final String DECOMPILED_CODE = "This is decompiled";

	@Override
	public String decompile(IClassFile classFile, Map<String, Object> configuration, IProgressMonitor monitor) throws CoreException {
		return DECOMPILED_CODE;
	}
}
