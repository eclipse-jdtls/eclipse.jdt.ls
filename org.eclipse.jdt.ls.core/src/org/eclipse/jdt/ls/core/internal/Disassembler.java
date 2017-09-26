/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API
 *     Red Hat Inc. - initial implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.ClassFileBytesDisassembler;
import org.eclipse.jdt.core.util.ClassFormatException;

public class Disassembler implements IDecompiler {

	private static final String LF = "\n";

	@Override
	public String decompile(IClassFile classFile, Map<String, Object> configuration, IProgressMonitor monitor) throws CoreException {
		ClassFileBytesDisassembler disassembler = ToolFactory.createDefaultClassFileBytesDisassembler();
		String disassembledByteCode = null;
		try {
			disassembledByteCode = disassembler.disassemble(classFile.getBytes(), LF, ClassFileBytesDisassembler.WORKING_COPY);
		} catch (ClassFormatException e) {
			throw new CoreException(new Status(Status.ERROR, "", "Error disassembling", e));
		}
		return disassembledByteCode;
	}

}
