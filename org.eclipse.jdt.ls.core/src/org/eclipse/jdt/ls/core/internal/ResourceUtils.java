/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * @author Fred Bricon
 */
public class ResourceUtils {

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

	public static String toString(List<IMarker> markers) {
		if (markers == null || markers.isEmpty()) {
			return "";
		}
		String s = markers.stream().map(m -> toString(m)).collect(Collectors.joining(", "));
		return s;
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
			content = Files.toString(new File(fileURI), Charsets.UTF_8);
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
			Files.write(content, new File(fileURI), Charsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Can not write to " + fileURI, e));
		}
	}

	/**
	 * Fix uris by adding missing // to single file:/ prefix
	 */
	public static String fixURI(URI uri) {
		String uriString = uri.toString();
		return uriString.replaceFirst("file:/([^/])", "file:///$1");
	}
}
