/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.ExceptionFactory;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 *
 * @author snjeza
 *
 */
public class WrapperValidator {

	private static final int QUEUE_LENGTH = 20;
	private static final String WRAPPER_CHECKSUM_URL = "wrapperChecksumUrl";
	private static final String GRADLE_WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar";

	private static Set<String> allowed = new HashSet<>();
	private static Set<String> disallowed = new HashSet<>();
	private static AtomicBoolean downloaded = new AtomicBoolean(false);
	private HashProvider hashProvider;
	private int queueLength;

	public WrapperValidator(int queueLength) {
		this.hashProvider = new HashProvider();
		this.queueLength = queueLength;
	}

	public WrapperValidator() {
		this(QUEUE_LENGTH);
	}

	public ValidationResult checkWrapper(String baseDir) throws CoreException {
		Path wrapperJar = Paths.get(baseDir, GRADLE_WRAPPER_JAR);
		if (!wrapperJar.toFile().exists()) {
			throw ExceptionFactory.newException(wrapperJar.toString() + " doesn't exist.");
		}
		if (!downloaded.get() || allowed.isEmpty()) {
			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
			if (preferenceManager != null && preferenceManager.getPreferences().getSha256Allowed() != null) {
				allow(preferenceManager.getPreferences().getSha256Allowed());
			}
			File versionFile = getVersionCacheFile();
			if (!versionFile.exists()) {
				JobHelpers.waitForLoadingGradleVersionJob();
			}
			if (versionFile.exists()) {
				InputStreamReader reader = null;
				try {
					reader = new InputStreamReader(new FileInputStream(versionFile), Charsets.UTF_8);
					String json = CharStreams.toString(reader);
					Gson gson = new GsonBuilder().create();
					TypeToken<List<Map<String, String>>> typeToken = new TypeToken<List<Map<String, String>>>() {
					};
					List<Map<String, String>> versions = gson.fromJson(json, typeToken.getType());
					//@formatter:off
					ImmutableList<String> wrapperChecksumUrls = FluentIterable
						.from(versions)
						.filter(new Predicate<Map<String, String>>() {
							@Override
							public boolean apply(Map<String, String> input) {
								return input.get(WRAPPER_CHECKSUM_URL) != null;
							}
						})
						.transform(new Function<Map<String, String>, String>() {
							@Override
							public String apply(Map<String, String> input) {
								return input.get(WRAPPER_CHECKSUM_URL);
							}
						})
					.toList();
					// @formatter:on
					DownloadChecksumJob downloadJob = new DownloadChecksumJob();
					int count = 0;
					File cacheDir = getSha256CacheFile();
					for (String wrapperChecksumUrl : wrapperChecksumUrls) {
						try {
							String fileName = getFileName(wrapperChecksumUrl);
							if (fileName == null) {
								continue;
							}
							File sha256File = new File(cacheDir, fileName);
							if (!sha256File.exists() || sha256File.lastModified() < versionFile.lastModified()) {
								count++;
								if (count > queueLength) {
									downloadJob.schedule();
									downloadJob = new DownloadChecksumJob();
									count = 0;
								}
								downloadJob.add(wrapperChecksumUrl);
							} else {
								String sha256 = read(sha256File);
								allowed.add(sha256);
							}
						} catch (Exception e) {
							JavaLanguageServerPlugin.logException(e.getMessage(), e);
						}
					}
					if (!downloadJob.isEmpty()) {
						downloadJob.schedule();
					}
					JobHelpers.waitForJobs(DownloadChecksumJob.WRAPPER_VALIDATOR_JOBS, new NullProgressMonitor());
					downloaded.set(true);
				} catch (IOException | OperationCanceledException e) {
					throw ExceptionFactory.newException(e);
				} finally {
					try {
						Closeables.close(reader, false);
					} catch (IOException e) {
						// ignore
					}
				}
			}
		}
		try {
			String sha256 = hashProvider.getChecksum(wrapperJar.toFile());
			return new ValidationResult(wrapperJar.toString(), sha256, allowed.contains(sha256));
		} catch (IOException e) {
			throw ExceptionFactory.newException(e);
		}
	}

	public static String getFileName(String url) {
		int index = url.lastIndexOf("/");
		if (index < 0 || url.length() < index + 1) {
			JavaLanguageServerPlugin.logInfo("Invalid wrapper URL " + url);
			return null;
		}
		return url.substring(index + 1);
	}

	private static String read(File file) {
		Optional<String> firstLine;
		try {
			firstLine = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8).findFirst();
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException(e);
			return null;
		}
		if (firstLine.isPresent()) {
			return firstLine.get();
		}
		return null;
	}

	private static File getVersionCacheFile() {
		return new File(System.getProperty("user.home"), ".tooling/gradle/versions.json");
	}

	public static File getSha256CacheFile() {
		String checksumCache = System.getProperty("gradle.checksum.cacheDir");
		File file;
		if (checksumCache == null || checksumCache.isEmpty()) {
			file = new File(System.getProperty("user.home"), ".tooling/gradle/checksums");
		} else {
			file = new File(checksumCache);
		}
		file.mkdirs();
		return file;
	}

	public static void clear() {
		allowed.clear();
		disallowed.clear();
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

}
