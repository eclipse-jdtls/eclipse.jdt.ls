/*******************************************************************************
 * Copyright (c) 2008-2020 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.internal.gradle.checksums.HashProvider;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.osgi.util.NLS;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * @author Fred Bricon
 *
 *         Partially copied from
 *         https://github.com/jbosstools/jbosstools-central/blob/24e907a07cbf81b9b7a9cafa69bd38b2271878eb/maven/plugins/org.jboss.tools.maven.core/src/org/jboss/tools/maven/core/internal/identification/MavenCentralIdentifier.java
 *
 */
public class MavenCentralIdentifier implements IMavenArtifactIdentifier {

	private static final String SHA1_SEARCH_QUERY = "https://search.maven.org/solrsearch/select?q=1:%22{0}%22&rows=1&wt=json";

	private HashProvider hashProvider = new HashProvider(HashProvider.SHA1);

	@Override
	public ArtifactKey identify(IPath path, IProgressMonitor monitor) {
		if (path == null) {
			return null;
		}
		return identify(path.toFile(), monitor);
	}

	private ArtifactKey identify(File file, IProgressMonitor monitor) {
		if (file == null || !file.isFile() || !file.canRead()) {
			return null;
		}
		String sha1 = null;
		try {
			sha1 = hashProvider.getChecksum(file);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logError("Failed to compute SHA1 checksum for " + file + " : " + e.getMessage());
			return null;
		}
		return identifySha1(sha1, monitor);
	}

	private ArtifactKey identifySha1(String sha1, IProgressMonitor monitor) {
		if (sha1 == null || sha1.isBlank()) {
			return null;
		}
		String searchUrl = NLS.bind(SHA1_SEARCH_QUERY, sha1);
		try {
			return find(searchUrl, monitor);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logError("Failed to identify " + sha1 + " with Maven Central : " + e.getMessage());
		}
		return null;
	}

	private ArtifactKey find(String searchUrl, IProgressMonitor monitor) {
		String timeoutStr = System.getProperty("java.lsp.mavensearch.timeout", "10");
		long seconds;
		try {
			seconds = Long.parseLong(timeoutStr);
		} catch (NumberFormatException e) {
			seconds = 10;
		}
		seconds = seconds <= 0 ? 10 : seconds;
		Duration timeout = Duration.ofSeconds(seconds);
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(timeout)
					.proxy(ProxySelector.getDefault())
			        .version(Version.HTTP_2)
					.build();
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.timeout(timeout)
			        .uri(URI.create(searchUrl))
			        .GET()
			        .build();

			if (monitor == null) {
				monitor = new NullProgressMonitor();
			}
			if (monitor.isCanceled()) {
				JavaLanguageServerPlugin.logInfo("Maven Central search cancelled");
				return null;
			}

			//TODO implement request cancellation, according to monitor status
			HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			JsonElement jsonElement = JsonParser.parseString(response.body());
			if (jsonElement != null && jsonElement.isJsonObject()) {
				return extractKey(jsonElement.getAsJsonObject());
			}
		} catch (JsonSyntaxException | IOException | InterruptedException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return null;
	}

	private ArtifactKey extractKey(JsonObject modelNode) {
		JsonObject response = modelNode.getAsJsonObject("response");
		if (response != null) {
			int num = response.get("numFound").getAsInt();
			if (num > 0) {
				JsonArray docs = response.getAsJsonArray("docs");
					String a = null, g = null, v = null;
					for (JsonElement d : docs) {
						JsonObject o = d.getAsJsonObject();
						a = o.get("a").getAsString();
						g = o.get("g").getAsString();
						v = o.get("v").getAsString();
						if (a != null && g != null && v != null) {
							return new ArtifactKey(g, a, v, null);
						}
				}
			}
		}
		return null;
	}

}
