/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.URIUtil;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

/**
 * @author Fred Bricon
 */
public final class ResourceUtils {

	public static final String FILE_UNC_PREFIX = "file:////";

	private ResourceUtils() {
		// No instanciation
	}

	public static List<IMarker> findMarkers(IResource resource, Integer... severities) throws CoreException {
		if (resource == null) {
			return null;
		}
		Set<Integer> targetSeverities = severities == null ? Collections.emptySet()
				: new HashSet<>(Arrays.asList(severities));
		IMarker[] allmarkers = resource.findMarkers(null /* all markers */, true /* subtypes */,
				IResource.DEPTH_INFINITE);
		List<IMarker> markers = Stream.of(allmarkers).filter(
				m -> targetSeverities.isEmpty() || targetSeverities.contains(m.getAttribute(IMarker.SEVERITY, 0)))
				.collect(Collectors.toList());
		return markers;
	}

	public static List<IMarker> getErrorMarkers(IResource resource) throws CoreException {
		return findMarkers(resource, IMarker.SEVERITY_ERROR);
	}

	public static List<IMarker> getWarningMarkers(IResource resource) throws CoreException {
		return findMarkers(resource, IMarker.SEVERITY_WARNING);
	}

	public static String toString(List<IMarker> markers) {
		if (markers == null || markers.isEmpty()) {
			return "";
		}
		String s = markers.stream().map(m -> toString(m)).collect(Collectors.joining(", "));
		return s;
	}

	public static String getMessage(IMarker marker) {
		if (marker == null) {
			return null;
		}
		return marker.getAttribute(IMarker.MESSAGE, null);
	}

	public static String toString(IMarker marker) {
		if (marker == null) {
			return null;
		}
		try {
			StringBuilder sb = new StringBuilder("Type=").append(marker.getType()).append(":Message=")
					.append(marker.getAttribute(IMarker.MESSAGE)).append(":LineNumber=")
					.append(marker.getAttribute(IMarker.LINE_NUMBER));
			return sb.toString();
		} catch (CoreException e) {
			e.printStackTrace();
			return "Unknown marker";
		}
	}

	/**
	 * Reads file content directly from the filesystem.
	 */
	public static String getContent(URI fileURI) throws CoreException {
		if (fileURI == null) {
			return null;
		}
		String content;
		try {
			content = Files.toString(toFile(fileURI), Charsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Can not get " + fileURI + " content", e));
		}
		return content;
	}

	/**
	 * Writes content to file, outside the workspace. No change event is
	 * emitted.
	 */
	public static void setContent(URI fileURI, String content) throws CoreException {
		if (content == null) {
			content = "";
		}
		try {
			Files.write(content, toFile(fileURI), Charsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Can not write to " + fileURI, e));
		}
	}

	public static String getContent(IFile file) throws CoreException {
		if (file == null) {
			return null;
		}
		String content;
		try {
			try (final Reader reader = new InputStreamReader(file.getContents())) {
				content = CharStreams.toString(reader);
			}
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Can not get " + file.getRawLocation() + " content", e));
		}
		return content;
	}

	/**
	 * Writes content to file, within the workspace. A change event is emitted.
	 */
	public static void setContent(IFile file, String content) throws CoreException {
		Assert.isNotNull(file, "file can not be null");
		if (content == null) {
			content = "";
		}
		try (InputStream newContent = new ByteArrayInputStream(content.getBytes())) {
			file.setContents(newContent, IResource.FORCE, null);
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Can not write to " + file.getRawLocation(), e));
		}
	}

	/**
	 * Fix uris by adding missing // to single file:/ prefix
	 */
	public static String fixURI(URI uri) {
		if (uri == null) {
			return null;
		}
		if (Platform.OS_WIN32.equals(Platform.getOS()) && URIUtil.isFileURI(uri)) {
			uri = URIUtil.toFile(uri).toURI();
		}
		String uriString = uri.toString();
		return uriString.replaceFirst("file:/([^/])", "file:///$1");
	}

	public static File toFile(URI uri) {
		if (Platform.OS_WIN32.equals(Platform.getOS())) {
			return URIUtil.toFile(uri);
		}
		return new File(uri);
	}

	/**
	 * Format URIs to be consumed by clients. On Windows platforms, UNC (Universal
	 * Naming Convention) URIs are transformed to follow the <code>file://</code>
	 * pattern.
	 *
	 * @param uri
	 *            the String URI to transform.
	 * @return a String URI compatible with clients.
	 */
	public static String toClientUri(String uri) {
		if (uri != null && Platform.OS_WIN32.equals(Platform.getOS()) && uri.startsWith(FILE_UNC_PREFIX)) {
			uri = uri.replace(FILE_UNC_PREFIX, "file://");
		}
		return uri;
	}

	public static IPath filePathFromURI(String uriStr) {
		URI uri = URI.create(uriStr);
		if ("file".equals(uri.getScheme())) {
			return Path.fromOSString(Paths.get(uri).toString());
		}
		return null;
	}

	public static IPath canonicalFilePathFromURI(String uriStr) {
		URI uri = URI.create(uriStr);
		if ("file".equals(uri.getScheme())) {
			return FileUtil.canonicalPath(Path.fromOSString(Paths.get(uri).toString()));
		}
		return null;
	}

	public static boolean isContainedIn(IPath location, Collection<IPath> paths) {
		if (location == null || paths == null || paths.isEmpty()) {
			return false;
		}
		for (IPath path : paths) {
			if (path.isPrefixOf(location)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Expand paths starting with ~/ if necessary, or return the original path.
	 *
	 * @param path
	 * @return expanded or original path
	 */
	public static String expandPath(String path) {
		if (path != null && path.startsWith("~" + File.separator)) {
			return System.getProperty("user.home") + path.substring(1);
		}
		return path;
	}

	/**
	 * Convert an {@link IPath} to a glob pattern (i.e. ending with /**)
	 *
	 * @param path
	 *            the path to convert
	 * @return a glob pattern prefixed with the path
	 */
	public static String toGlobPattern(IPath path) {
		if (path == null) {
			return null;
		}

		String baseName = path.lastSegment();
		return toGlobPattern(path, !baseName.endsWith(".jar") && !baseName.endsWith(".zip"));
	}

	/**
	 * Convert an {@link IPath} to a glob pattern.
	 *
	 * @param path
	 *            the path to convert
	 * @param recursive
	 *            whether to end the glob with "/**"
	 * @return a glob pattern prefixed with the path
	 */
	public static String toGlobPattern(IPath path, boolean recursive) {
		if (path == null) {
			return null;
		}

		String globPattern = path.toPortableString();
		if (path.getDevice() != null) {
			//This seems pretty hack-ish: need to remove device as it seems to break
			// file detection, at least on vscode
			globPattern = globPattern.replace(path.getDevice(), "**");
		}

		if (recursive) {
			if (!globPattern.endsWith("/")) {
				globPattern += "/";
			}
			globPattern += "**";
		}

		return globPattern;
	}

	public static String dos2Unix(String str) {
		if (str != null && Platform.OS_WIN32.equals(Platform.getOS())) {
			str = str.replaceAll("\\r", "");
		}
		return str;
	}

	/**
	 * Returns the longest common path for an array of paths.
	 */
	public static IPath getLongestCommonPath(IPath[] paths) {
		if (paths == null || paths.length == 0) {
			return null;
		}

		int common = paths[0].segmentCount() - 1;
		for (int i = 1; i < paths.length; i++) {
			common = Math.min(paths[i].segmentCount() - 1, common);
			if (common <= 0 || !Objects.equals(paths[0].getDevice(), paths[i].getDevice())) {
				return  null;
			}

			for (int j = 0; j < common; j++) {
				if (!Objects.equals(paths[i].segment(j), paths[0].segment(j))) {
					common = j;
					break;
				}
			}
		}

		if (common <= 0) {
			return null;
		}

		return paths[0].uptoSegment(common);
	}
}
