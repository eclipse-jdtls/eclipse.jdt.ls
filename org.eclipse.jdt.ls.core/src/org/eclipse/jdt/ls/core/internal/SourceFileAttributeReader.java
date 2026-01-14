/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.util.ISourceAttribute;
import org.eclipse.jdt.internal.core.util.ClassFileReader;

/**
 * Utility class to read the SourceFile attribute from class files.
 * The SourceFile attribute contains the name of the source file from which
 * the class was compiled (e.g., "OkHttpClient.kt" for Kotlin classes).
 */
public class SourceFileAttributeReader {

	private SourceFileAttributeReader() {
		// Utility class - no instantiation
	}

	/**
	 * Gets the source file name from the SourceFile attribute of the given class file.
	 *
	 * @param classFile the class file to read
	 * @return the source file name (e.g., "OkHttpClient.kt", "MyClass.java"),
	 *         or null if the attribute is not present or cannot be read
	 */
	public static String getSourceFileName(IClassFile classFile) {
		if (classFile == null) {
			return null;
		}
		try {
			return getSourceFileName(classFile.getBytes());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Error reading class file bytes", e);
			return null;
		}
	}

	/**
	 * Gets the source file name from the SourceFile attribute of the given class file bytes.
	 *
	 * @param classFileBytes the raw bytes of the class file
	 * @return the source file name (e.g., "OkHttpClient.kt", "MyClass.java"),
	 *         or null if the attribute is not present or cannot be read
	 */
	public static String getSourceFileName(byte[] classFileBytes) {
		if (classFileBytes == null || classFileBytes.length == 0) {
			return null;
		}

		try {
			// Use Eclipse JDT's class file reader to parse the class file
			IClassFileReader reader = new ClassFileReader(classFileBytes, IClassFileReader.CLASSFILE_ATTRIBUTES);

			// Get the SourceFile attribute from the class file
			ISourceAttribute sourceFileAttribute = reader.getSourceFileAttribute();
			if (sourceFileAttribute == null) {
				return null;
			}

			// Get the source file name from the constant pool
			char[] sourceFileName = sourceFileAttribute.getSourceFileName();
			if (sourceFileName == null || sourceFileName.length == 0) {
				return null;
			}

			return new String(sourceFileName);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error parsing class file format", e);
		}
		return null;
	}
}