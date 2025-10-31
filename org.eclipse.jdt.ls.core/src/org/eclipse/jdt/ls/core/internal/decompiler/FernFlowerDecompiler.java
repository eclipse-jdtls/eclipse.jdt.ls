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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Manifest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.DecompilerResult;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

public class FernFlowerDecompiler extends DecompilerImpl {
	public static final String DECOMPILER_HEADER = "// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).\n";

	public static boolean isDecompiledContents(String contents) {
		return contents != null && contents.startsWith(DECOMPILER_HEADER);
	}

	@Override
	protected DecompilerResult decompileContent(URI uri, IProgressMonitor monitor) throws CoreException {
		try {
			return getContent(new BytecodeProvider(uri), monitor);
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Failed to decompile class file: " + uri, e));
		}
	}

	@Override
	protected DecompilerResult decompileContent(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		try {
			return getContent(new BytecodeProvider(classFile), monitor);
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Failed to decompile class file: " + classFile, e));
		}
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
		decompilerOptions.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "0");
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
		// Add all sources (outer + inner classes) to the decompiler
		for (File file : provider.getAllClassFiles()) {
			fernflower.addSource(file);
		}
		fernflower.decompileContext();
		final String decompiledCode = DECOMPILER_HEADER + resultSaver.content;

		final Map<Integer, Set<Integer>> originalLineMappings = new TreeMap<>();
		final Map<Integer, Set<Integer>> decompiledLineMappings = new TreeMap<>();
		if (resultSaver.originalLinesMapping != null) {
			for (int i = 0; i + 1 < resultSaver.originalLinesMapping.length; i = i + 2) {
				int srcLine = resultSaver.originalLinesMapping[i];
				int decompiledLine = resultSaver.originalLinesMapping[i + 1] + 1; // add one extra line for the header
				Set<Integer> decompiled = originalLineMappings.computeIfAbsent(srcLine, k -> new TreeSet<>());
				decompiled.add(decompiledLine);
				Set<Integer> src = decompiledLineMappings.computeIfAbsent(decompiledLine, k -> new TreeSet<>());
				src.add(srcLine);
			}
		} else {
			JavaLanguageServerPlugin.logInfo("Line mappings not available for decompiled content - decompilation may have failed or line mapping was disabled");
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
		private Map<String, byte[]> bytecodeMap = new HashMap<>();
		private List<File> classFiles = new ArrayList<>();

		public BytecodeProvider(URI uri) throws CoreException, IOException {
			collectClassFiles(uri);
		}

		public BytecodeProvider(IClassFile classFile) throws CoreException, IOException {
			collectClassFiles(classFile);
		}

		public List<File> getAllClassFiles() {
			return classFiles;
		}

		@Override
		public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
			byte[] bytes = bytecodeMap.get(externalPath);
			if (bytes == null) {
				throw new IOException("Class file not found: " + externalPath +
					" (available: " + bytecodeMap.keySet() + ")");
			}
			return bytes;
		}

		//-----------------------------------------------------------
		// Collect all class files from a URI - File-based detection
		//-----------------------------------------------------------
		private void collectClassFiles(URI uri) throws CoreException, IOException {
			IClassFile classFile = JDTUtils.resolveClassFile(uri);
			if (classFile != null) {
				collectClassFiles(classFile);
				return;
			}

			// File-based approach
			java.nio.file.Path classPath = Paths.get(uri);
			if (!Files.exists(classPath)) {
				throw new IOException("Class file does not exist: " + classPath);
			}

			File file = classPath.toFile();

			// 1. Find the top-level class file
			File topLevelFile = findTopLevelClassFile(file);

			// 2. Scan all matching classes (top-level + inner classes)
			Set<File> allClassFiles = new LinkedHashSet<>();
			scanClassFiles(topLevelFile, allClassFiles);

			// 3. Process all classes found
			for (File classFileToProcess : allClassFiles) {
				processClassFile(classFileToProcess);
			}
		}

		private File findTopLevelClassFile(File file) throws IOException {
			String fileName = file.getName();
			if (!fileName.endsWith(".class")) {
				return file;
			}

			String baseName = fileName.substring(0, fileName.length() - 6);
			int dollarIndex = baseName.indexOf('$');

			if (dollarIndex > 0) {
				// Check if this might be an inner class
				String topLevelBaseName = baseName.substring(0, dollarIndex);
				File parentDir = file.getParentFile();
				if (parentDir != null) {
					File topLevelFile = new File(parentDir, topLevelBaseName + ".class");
					if (topLevelFile.exists()) {
						// Found the parent class, so this is indeed an inner class
						return topLevelFile;
					}
					// Parent class doesn't exist - treat this as a top-level class
					// (e.g., a class with $ in its name, though uncommon)
				}
			}

			return file;
		}

		private void scanClassFiles(File topLevelFile, Set<File> result) {
			// Add the top-level class
			result.add(topLevelFile);

			// Find all inner classes
			String fileName = topLevelFile.getName();
			if (fileName.endsWith(".class")) {
				String baseName = fileName.substring(0, fileName.length() - 6);
				String mask = baseName + "$";

				File parentDir = topLevelFile.getParentFile();
				if (parentDir != null && parentDir.isDirectory()) {
					File[] innerClasses = parentDir.listFiles((dir, name) ->
						name.startsWith(mask) && name.endsWith(".class"));
					if (innerClasses != null) {
						Arrays.stream(innerClasses).forEach(result::add);
					}
				}
			}
		}

		private void processClassFile(File file) throws IOException {
			String path = file.getPath();
			classFiles.add(file);
			bytecodeMap.put(path, Files.readAllBytes(file.toPath()));
		}

		//-----------------------------------------------------------
		// Collect all class files from an IClassFile
		//-----------------------------------------------------------
		private void collectClassFiles(IClassFile classFile) throws CoreException {
			// 1. Find the top-level class file
			IClassFile topLevelClassFile = findTopLevelClassFile(classFile);

			// 2. Scan all matching IClassFile (top-level + inner classes)
			Set<IClassFile> allClassFiles = new LinkedHashSet<>();
			scanClassFiles(topLevelClassFile, allClassFiles);

			// 3. Process all classes found
			for (IClassFile cf : allClassFiles) {
				processClassFile(cf);
			}
		}

		private IClassFile findTopLevelClassFile(IClassFile classFile) throws CoreException {
			IClassFile topLevelClassFile = classFile;
			if (classFile instanceof IOrdinaryClassFile ordinaryClassFile) {
				IType type = ordinaryClassFile.getType();
				// Navigate up to the top-level type
				IType topLevelType = type;
				while (topLevelType.getDeclaringType() != null) {
					topLevelType = topLevelType.getDeclaringType();
				}
				// Get the class file for the top-level type
				if (topLevelType != type) {
					topLevelClassFile = topLevelType.getClassFile();
				}
			}
			return topLevelClassFile;
		}

		private void scanClassFiles(IClassFile classFile, Set<IClassFile> result) throws CoreException {
			if (classFile instanceof IOrdinaryClassFile ordinaryClassFile) {
				if (!result.add(classFile)) {
					return;
				}

				// Collect declared member types (inner classes/interfaces)
				IType type = ordinaryClassFile.getType();
				IType[] innerTypes = type.getTypes();
				for (IType innerType : innerTypes) {
					IClassFile innerClassFile = innerType.getClassFile();
					if (innerClassFile != null) {
						// Recursive call to handle nested inner classes
						scanClassFiles(innerClassFile, result);
					}
				}
			}
		}

		private void processClassFile(IClassFile classFile) throws CoreException {
			File file = new File(classFile.getElementName()).getAbsoluteFile();
			byte[] classBytes = classFile.getBytes();
			classFiles.add(file);
			bytecodeMap.put(file.getPath(), classBytes);
		}

	}
}
