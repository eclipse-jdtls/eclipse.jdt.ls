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

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.DecompilerResult;
import org.eclipse.jdt.ls.core.internal.IDecompiler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

public abstract class DecompilerImpl implements IDecompiler {
	private static Map<DecompilerType, Map<String, DecompilerResult>> decompilerCache = new HashMap<>();
	private static final int CACHE_SIZE = 100;

	private static Map<String, DecompilerResult> getLRUCache(int maxEntries) {
		Map<String, DecompilerResult> map = new LinkedHashMap<>(maxEntries + 1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > maxEntries;
			}
		};
		return Collections.synchronizedMap(map);
	}

	private static Map<String, DecompilerResult> getCache(DecompilerType type, int maxEntries) {
		return decompilerCache.computeIfAbsent(type, key -> {
			return getLRUCache(maxEntries);
		});
	}

	private static DecompilerResult getCachedResult(DecompilerType type, IClassFile classFile) {
		Map<String, DecompilerResult> cache = decompilerCache.get(type);
		if (cache != null) {
			return cache.get(classFile.getHandleIdentifier());
		}

		return null;
	}

	@Override
	public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
		Map<String, DecompilerResult> cache = getCache(getDecompilerType(), CACHE_SIZE);
		String cacheKey = uri.toString();
		DecompilerResult result = cache.computeIfAbsent(cacheKey, (key) -> {
			try {
				return decompileContent(uri, monitor);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to decompile with " + getDecompilerType().name(), e);
			}

			return null;
		});

		return result == null ? null : result.getContent();
	}

	@Override
	public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		DecompilerResult result = getDecompiledSource(classFile, monitor);
		return result == null ? null : result.getContent();
	}

	@Override
	public DecompilerResult getDecompiledSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		Map<String, DecompilerResult> cache = getCache(getDecompilerType(), CACHE_SIZE);
		String cacheKey = classFile.getHandleIdentifier();
		return cache.computeIfAbsent(cacheKey, (key) -> {
			try {
				return decompileContent(classFile, monitor);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to decompile with " + getDecompilerType().name(), e);
			}

			return null;
		});
	}

	@Override
	public int[] getDecompiledLineMappings(IClassFile classFile, String contents, IProgressMonitor monitor) throws CoreException {
		if (!isDecompiledContents(classFile, contents)) {
			return null;
		}

		DecompilerResult result = getCachedResult(getDecompilerType(), classFile);
		return result == null ? null : result.getDecompiledLineMappings();
	}

	@Override
	public int[] getOriginalLineMappings(IClassFile classFile, String contents, IProgressMonitor monitor) throws CoreException {
		if (!isDecompiledContents(classFile, contents)) {
			return null;
		}

		DecompilerResult result = getCachedResult(getDecompilerType(), classFile);
		return result == null ? null : result.getOriginalLineMappings();
	}

	protected abstract DecompilerResult decompileContent(URI uri, IProgressMonitor monitor) throws CoreException;
	protected abstract DecompilerResult decompileContent(IClassFile classFile, IProgressMonitor monitor) throws CoreException;
	protected abstract DecompilerType getDecompilerType();
	protected abstract boolean isDecompiledContents(IClassFile classFile, String contents);
}
