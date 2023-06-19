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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.DecompilerResult;
import org.eclipse.jdt.ls.core.internal.IDecompiler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

public abstract class DecompilerImpl implements IDecompiler {
	private static Map<DecompilerType, Map<String, DecompilerResult>> decompilerCache = 
			Collections.synchronizedMap(new EnumMap<>(DecompilerType.class));
	private static final int CACHE_SIZE = 100;

	private static Map<String, DecompilerResult> getLRUCache(int maxEntries) {
		Map<String, DecompilerResult> map = new LinkedHashMap<>(maxEntries + 1, .75F, true) {
			@Override
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > maxEntries;
			}
		};
		return Collections.synchronizedMap(map);
	}

	private static Map<String, DecompilerResult> getCache(DecompilerType type) {
		return decompilerCache.computeIfAbsent(type, key -> {
			return getLRUCache(CACHE_SIZE);
		});
	}

	@Override
	public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
		Map<String, DecompilerResult> cache = getCache(getDecompilerType());
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
		Map<String, DecompilerResult> cache = getCache(getDecompilerType());
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

	protected abstract DecompilerResult decompileContent(URI uri, IProgressMonitor monitor) throws CoreException;
	protected abstract DecompilerResult decompileContent(IClassFile classFile, IProgressMonitor monitor) throws CoreException;
	protected abstract DecompilerType getDecompilerType();
}
