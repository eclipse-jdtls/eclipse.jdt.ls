/*******************************************************************************
 * Copyright (c) 2020, 2023 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.internal.gradle.checksums;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.ExceptionFactory;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult.Status;
import org.osgi.framework.Bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 * @author snjeza
 *
 */
public class WrapperValidator {

	public static final String GRADLE_CHECKSUMS = "/gradle/checksums/checksums.json";
	public static final String GRADLE_WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar";
	public static final String GRADLE_WRAPPER_PROPERTIES = "gradle/wrapper/gradle-wrapper.properties";
	private static final int NETWORK_TIMEOUT_MS = 5000;
	private static final Pattern VERSION_PATTERN = Pattern.compile("gradle-([^-/]+(-.+?)?)-(?:bin|all)\\.zip");

	private static Set<String> allowed = new HashSet<>();
	private static Set<String> disallowed = new HashSet<>();
	// version -> expected sha256
	private static Map<String, String> bundledChecksums = new HashMap<>();
	private static boolean bundledLoaded = false;
	private HashProvider hashProvider;

	public WrapperValidator() {
		this.hashProvider = new HashProvider();
	}

	public ValidationResult checkWrapper(String baseDir) throws CoreException {
		Path basePath = Paths.get(baseDir);
		Path wrapperJar = basePath.resolve(GRADLE_WRAPPER_JAR);
		if (!wrapperJar.toFile().exists()) {
			throw ExceptionFactory.newException(wrapperJar.toString() + " doesn't exist.");
		}
		if (!bundledLoaded) {
			loadInternalChecksums();
			bundledLoaded = true;
		}
		try {
			String sha256 = hashProvider.getChecksum(wrapperJar.toFile());
			String version = parseGradleVersion(basePath);
			JavaLanguageServerPlugin.logInfo("Validating Gradle wrapper checksum for " + wrapperJar + " (version: " + version + ", sha256: " + sha256 + ")");

			// 1. Check user-allowed checksums
			if (allowed.contains(sha256)) {
				JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum matches user-allowed list: VALID");
				return new ValidationResult(wrapperJar.toString(), sha256, Status.VALID);
			}

			// 2. Check user-disallowed checksums
			if (disallowed.contains(sha256)) {
				JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum matches user-disallowed list: INVALID");
				return new ValidationResult(wrapperJar.toString(), sha256, Status.INVALID);
			}

			// 3. If version can't be parsed, the distributionUrl may have been tampered with.
			//    Only allow if the jar matches a known bundled checksum.
			if (version == null) {
				if (bundledChecksums.containsValue(sha256)) {
					JavaLanguageServerPlugin.logInfo("Gradle wrapper version not parseable but checksum matches a known bundled checksum: VALID");
					return new ValidationResult(wrapperJar.toString(), sha256, Status.VALID);
				}
				JavaLanguageServerPlugin.logInfo("Gradle wrapper version not parseable from distributionUrl and checksum is unknown: INVALID");
				return new ValidationResult(wrapperJar.toString(), sha256, Status.INVALID);
			}

			// 4. Look up expected checksum from bundled checksums.json by version
			String expectedChecksum = bundledChecksums.get(version);
			if (expectedChecksum != null && expectedChecksum.equals(sha256)) {
				JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum matches bundled checksum for version " + version + ": VALID");
				return new ValidationResult(wrapperJar.toString(), sha256, Status.VALID);
			}

			// 5. Check if sha256 matches ANY bundled version's checksum
			// (wrapper jars are generic bootstraps, same jar may be used across versions)
			if (bundledChecksums.containsValue(sha256)) {
				JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum matches a known bundled checksum: VALID");
				return new ValidationResult(wrapperJar.toString(), sha256, Status.VALID);
			}

			// 6. Check disk cache
			String cachedChecksum = readCachedChecksum(version);
			if (cachedChecksum != null) {
				Status status = cachedChecksum.equals(sha256) ? Status.VALID : Status.INVALID;
				JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum compared against disk cache for version " + version + ": " + status);
				return new ValidationResult(wrapperJar.toString(), sha256, status);
			}

			// 7. Network fetch with timeout
			String checksumUrl = "https://services.gradle.org/distributions/gradle-" + version + "-wrapper.jar.sha256";
			JavaLanguageServerPlugin.logInfo("Fetching Gradle wrapper checksum from " + checksumUrl);
			String fetchedChecksum = fetchChecksum(checksumUrl);
			if (fetchedChecksum != null) {
				boolean valid = fetchedChecksum.equals(sha256);
				if (valid) {
					// Cache successfully validated checksum to disk
					writeCachedChecksum(version, fetchedChecksum);
					allowed.add(sha256);
				}
				Status status = valid ? Status.VALID : Status.INVALID;
				JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum compared against fetched checksum for version " + version + ": " + status);
				return new ValidationResult(wrapperJar.toString(), sha256, status);
			}

			// 8. Version not in bundled data and network fetch failed -> unverifiable
			JavaLanguageServerPlugin.logInfo("Gradle wrapper checksum could not be verified for version " + version + ": UNVERIFIABLE");
			return new ValidationResult(wrapperJar.toString(), sha256, Status.UNVERIFIABLE);
		} catch (IOException e) {
			throw ExceptionFactory.newException(e);
		}
	}

	/**
	 * Parse the Gradle version from gradle-wrapper.properties distributionUrl.
	 */
	public static String parseGradleVersion(Path baseDir) {
		Path propsPath = baseDir.resolve(GRADLE_WRAPPER_PROPERTIES);
		if (!Files.exists(propsPath)) {
			return null;
		}
		try (InputStream is = Files.newInputStream(propsPath)) {
			Properties props = new Properties();
			props.load(is);
			String distributionUrl = props.getProperty("distributionUrl");
			if (distributionUrl != null) {
				Matcher matcher = VERSION_PATTERN.matcher(distributionUrl);
				if (matcher.find()) {
					return matcher.group(1);
				}
			}
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	private void loadInternalChecksums() {
		Bundle bundle = Platform.getBundle(IConstants.PLUGIN_ID);
		URL url = FileLocator.find(bundle, new org.eclipse.core.runtime.Path(GRADLE_CHECKSUMS));
		if (url != null) {
			try (InputStream inputStream = url.openStream(); InputStreamReader inputStreamReader = new InputStreamReader(inputStream); Reader reader = new BufferedReader(inputStreamReader)) {
				JsonElement jsonElement = JsonParser.parseReader(reader);
				if (jsonElement instanceof JsonArray array) {
					for (JsonElement json : array) {
						if (!json.isJsonObject()) {
							continue;
						}
						JsonObject jsonObject = json.getAsJsonObject();
						String sha256 = jsonObject.has("sha256") ? jsonObject.get("sha256").getAsString() : null;
						if (sha256 == null) {
							continue;
						}
						// Prefer explicit version field, fall back to extracting from URL
						String version = jsonObject.has("version") ? jsonObject.get("version").getAsString() : null;
						if (version == null) {
							String wrapperChecksumUrl = jsonObject.has("wrapperChecksumUrl") ? jsonObject.get("wrapperChecksumUrl").getAsString() : null;
							version = extractVersionFromChecksumUrl(wrapperChecksumUrl);
						}
						if (version != null) {
							bundledChecksums.put(version, sha256);
						}
					}
				}
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
	}

	/**
	 * Extract version from a wrapperChecksumUrl like
	 * "https://services.gradle.org/distributions/gradle-8.5-wrapper.jar.sha256"
	 */
	public static String extractVersionFromChecksumUrl(String checksumUrl) {
		if (checksumUrl == null) {
			return null;
		}
		Pattern pattern = Pattern.compile("gradle-([^/]+)-wrapper\\.jar\\.sha256$");
		Matcher matcher = pattern.matcher(checksumUrl);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private String readCachedChecksum(String version) {
		File cacheDir = getSha256CacheFile();
		File cachedFile = new File(cacheDir, "gradle-" + version + "-wrapper.jar.sha256");
		if (cachedFile.exists()) {
			try {
				return Files.readString(cachedFile.toPath(), StandardCharsets.UTF_8).trim();
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return null;
	}

	private void writeCachedChecksum(String version, String checksum) {
		File cacheDir = getSha256CacheFile();
		File cachedFile = new File(cacheDir, "gradle-" + version + "-wrapper.jar.sha256");
		try {
			Files.writeString(cachedFile.toPath(), checksum, StandardCharsets.UTF_8);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	private String fetchChecksum(String checksumUrl) {
		HttpURLConnection connection = null;
		try {
			URL url = new URI(checksumUrl).toURL();
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
			connection.setReadTimeout(NETWORK_TIMEOUT_MS);
			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
					StringBuilder sb = new StringBuilder();
					char[] buf = new char[256];
					int n;
					while ((n = reader.read(buf)) != -1) {
						sb.append(buf, 0, n);
					}
					return sb.toString().trim();
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logInfo("Failed to fetch checksum from " + checksumUrl + ": " + e.getMessage());
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		return null;
	}

	private static String getXdgCache() {
		String xdgCache = System.getenv("XDG_CACHE_HOME");
		if (xdgCache == null) {
			xdgCache = System.getProperty("user.home") + "/.cache/";
		}
		return xdgCache;
	}

	public static File getSha256CacheFile() {
		String checksumCache = System.getProperty("gradle.checksum.cacheDir");
		File file;
		if (checksumCache == null || checksumCache.isEmpty()) {
			String xdgCache = getXdgCache();
			file = new File(xdgCache, "tooling/gradle/checksums");
		} else {
			file = new File(checksumCache);
		}
		file.mkdirs();
		return file;
	}

	public static void clear() {
		allowed.clear();
		disallowed.clear();
		bundledChecksums.clear();
		bundledLoaded = false;
	}

	public static void allow(Collection<String> c) {
		allowed.addAll(c);
	}

	public static void allow(String checksum) {
		allowed.add(checksum);
	}

	public static void disallow(Collection<String> c) {
		disallowed.addAll(c);
	}

	public static boolean contains(String checksum) {
		return disallowed.contains(checksum);
	}

	public static int size() {
		return allowed.size() + disallowed.size();
	}

	public static Set<String> getAllowed() {
		return Collections.unmodifiableSet(allowed);
	}

	public static Set<String> getDisallowed() {
		return Collections.unmodifiableSet(disallowed);
	}

	public static void putSha256(List<?> gradleWrapperList) {
		List<String> sha256Allowed = new ArrayList<>();
		List<String> sha256Disallowed = new ArrayList<>();
		for (Object object : gradleWrapperList) {
			if (object instanceof Map<?, ?> map) {
				final ChecksumWrapper sha256 = new ChecksumWrapper();
				sha256.allowed = true;
				map.forEach((k, v) -> {
					if (k instanceof String key) {
						switch (key) {
							case "sha256":
								if (v instanceof String value) {
									sha256.checksum = value;
								}
								break;
							case "allowed":
								if (v instanceof Boolean bool) {
									sha256.allowed = bool;
								}
								break;
							default:
								break;
						}
					}
				});
				if (sha256.checksum != null) {
					if (sha256.allowed) {
						sha256Allowed.add(sha256.checksum);
					} else {
						sha256Disallowed.add(sha256.checksum);
					}
				}
			}
		}
		WrapperValidator.clear();
		if (sha256Allowed != null) {
			WrapperValidator.allow(sha256Allowed);
		}
		if (sha256Disallowed != null) {
			WrapperValidator.disallow(sha256Disallowed);
		}
	}

	private static class ChecksumWrapper {
		private String checksum;
		private boolean allowed;
	}

}
