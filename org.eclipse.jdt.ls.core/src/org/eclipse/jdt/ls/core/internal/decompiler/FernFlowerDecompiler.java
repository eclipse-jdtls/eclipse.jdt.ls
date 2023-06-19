/*******************************************************************************
* Copyright (c) 2023 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.decompiler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.jar.Manifest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.DecompilerResult;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;

public class FernFlowerDecompiler extends DecompilerImpl {
	public static final String DECOMPILER_HEADER = "// Source code is decompiled from a .class file using FernFlower decompiler.\n";

	public static boolean isDecompiledContents(String contents) {
		return contents != null && contents.startsWith(DECOMPILER_HEADER);
	}

	@Override
	protected DecompilerResult decompileContent(URI uri, IProgressMonitor monitor) throws CoreException {
		return getContent(new BytecodeProvider(uri), monitor);
	}

	@Override
	protected DecompilerResult decompileContent(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		return getContent(new BytecodeProvider(classFile), monitor);
	}

	@Override
	protected DecompilerType getDecompilerType() {
		return DecompilerType.FERNFLOWER;
	}

	private DecompilerResult getContent(BytecodeProvider provider, IProgressMonitor monitor) throws CoreException {
		Map<String, Object> decompilerOptions = new HashMap<>();
		decompilerOptions.put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "0");
		decompilerOptions.put(IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1");
		decompilerOptions.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
		decompilerOptions.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
		decompilerOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		decompilerOptions.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
		decompilerOptions.put(IFernflowerPreferences.DECOMPILE_ENUM, "1");
		decompilerOptions.put(IFernflowerPreferences.LOG_LEVEL, Severity.ERROR.name());
		decompilerOptions.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "1");
		decompilerOptions.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
		if (Boolean.getBoolean("jdt.ls.debug")) {
			decompilerOptions.put(IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1");
		}
		ResultSaver resultSaver = new ResultSaver();
		BaseDecompiler fernflower = new BaseDecompiler(provider, resultSaver, decompilerOptions, new IFernflowerLogger() {
			@Override
			public void writeMessage(String message, Severity severity) {
				if (severity.ordinal() >= Severity.ERROR.ordinal()) {
					JavaLanguageServerPlugin.logError(message);
				}
			}

			@Override
			public void writeMessage(String message, Severity severity, Throwable t) {
				if (severity.ordinal() >= Severity.ERROR.ordinal()) {
					JavaLanguageServerPlugin.logException(message, t);
				}
			}
		});
		fernflower.addSource(new File("__Fernflower__.class"));
		fernflower.decompileContext();
		final String decompiledCode = DECOMPILER_HEADER + resultSaver.content;
		
		final Map<Integer, Set<Integer>> originalLineMappings = new TreeMap<>();
		final Map<Integer, Set<Integer>> decompiledLineMappings = new TreeMap<>();
		for (int i = 0; i + 1 < resultSaver.originalLinesMapping.length; i = i + 2) {
			int srcLine = resultSaver.originalLinesMapping[i];
			int decompiledLine = resultSaver.originalLinesMapping[i + 1] + 1; // add one extra line for the header
			Set<Integer> decompiled = originalLineMappings.computeIfAbsent(srcLine, k -> new TreeSet<>());
			decompiled.add(decompiledLine);
			Set<Integer> src = decompiledLineMappings.computeIfAbsent(decompiledLine, k -> new TreeSet<>());
			src.add(srcLine);
		}

		List<Integer> originals = new ArrayList<>(originalLineMappings.size() * 2);
		for (Entry<Integer, Set<Integer>> entry: originalLineMappings.entrySet()) {
			int original = entry.getKey();
			for (int value : entry.getValue()) {
				originals.add(original);
				originals.add(value);
			}
		}

		List<Integer> decompiles = new ArrayList<>(decompiledLineMappings.size() * 2);
		for (Entry<Integer, Set<Integer>> entry: decompiledLineMappings.entrySet()) {
			int decompiled = entry.getKey();
			for (int value : entry.getValue()) {
				decompiles.add(decompiled);
				decompiles.add(value);
			}
		}

		return new DecompilerResult(decompiledCode,
			originals.stream().mapToInt(Integer::intValue).toArray(),
			decompiles.stream().mapToInt(Integer::intValue).toArray());
	}

	static class ResultSaver implements IResultSaver {
		private String content;
		private int[] originalLinesMapping;

		@Override
		public void closeArchive(String arg0, String arg1) {
		}

		@Override
		public void copyEntry(String arg0, String arg1, String arg2, String arg3) {
		}

		@Override
		public void copyFile(String arg0, String arg1, String arg2) {
		}

		@Override
		public void createArchive(String arg0, String arg1, Manifest arg2) {
		}

		@Override
		public void saveClassEntry(String arg0, String arg1, String arg2, String arg3, String arg4) {
		}

		@Override
		public void saveClassFile(String filename, String qualifiedName, String entryName, String content, int[] originalLinesMapping) {
			this.content = content;
			this.originalLinesMapping = originalLinesMapping;
		}

		@Override
		public void saveDirEntry(String arg0, String arg1, String arg2) {
		}

		@Override
		public void saveFolder(String arg0) {
		}
	}

	static class BytecodeProvider implements IBytecodeProvider {
		private URI uri;
		private byte[] classBytes;

		public BytecodeProvider(URI uri) {
			this.uri = uri;
		}

		public BytecodeProvider(IClassFile classFile) {
			try {
				classBytes = readClassFileBytes(classFile);
			} catch (IOException e) {
				// do nothing
			}
		}

		@Override
		public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
			if (classBytes != null) {
				return classBytes;
			} else if (uri == null) {
				return null;
			}

			classBytes = readClassFileBytes(JDTUtils.resolveClassFile(uri));
			if (classBytes == null) {
				classBytes = Files.readAllBytes(Paths.get(uri));
			}
			return classBytes;
		}

		private byte[] readClassFileBytes(IClassFile classFile) throws IOException {
			if (classFile == null) {
				return null;
			}

			try {
				return classFile.getBytes();
			} catch (JavaModelException e) {
				throw new IOException(e);
			}
		}
	}
}
