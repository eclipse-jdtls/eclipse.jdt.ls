/*******************************************************************************
 * Copyright (c) 2018-2022 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.lang.Runtime.Version;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.RuntimeEnvironment;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.GradleEnvironment;

public class GradleUtils {

	// see https://github.com/gradle/gradle/pull/17397
	public static String INVALID_TYPE_FIXED_VERSION = "7.2";
	// see https://github.com/gradle/gradle/issues/890
	// see https://github.com/gradle/gradle/issues/16922
	public static String JPMS_SUPPORTED_VERSION = "7.0.1";

	private static final String MESSAGE_DIGEST_ALGORITHM = "SHA-256";

	public static boolean isIncompatible(GradleVersion gradleVersion, String javaVersion) {
		if (gradleVersion == null || javaVersion == null || javaVersion.isEmpty()) {
			return false;
		}
		String highestSupportedJava = getHighestSupportedJava(gradleVersion);
		return JavaCore.compareJavaVersions(javaVersion, highestSupportedJava) > 0;
	}

	public static String getHighestSupportedJava(GradleVersion gradleVersion) {
		GradleVersion baseVersion = gradleVersion.getBaseVersion();
		try {
			// https://docs.gradle.org/current/userguide/compatibility.html
			if (baseVersion.compareTo(GradleVersion.version("8.8")) >= 0) {
				return JavaCore.VERSION_22;
			} else if (baseVersion.compareTo(GradleVersion.version("8.5")) >= 0) {
				return JavaCore.VERSION_21;
			} else if (baseVersion.compareTo(GradleVersion.version("8.3")) >= 0) {
				return JavaCore.VERSION_20;
			} else if (baseVersion.compareTo(GradleVersion.version("7.6")) >= 0) {
				return JavaCore.VERSION_19;
			} else if (baseVersion.compareTo(GradleVersion.version("7.5")) >= 0) {
				return JavaCore.VERSION_18;
			} else if (baseVersion.compareTo(GradleVersion.version("7.3")) >= 0) {
				return JavaCore.VERSION_17;
			} else if (baseVersion.compareTo(GradleVersion.version("7.0")) >= 0) {
				return JavaCore.VERSION_16;
			} else if (baseVersion.compareTo(GradleVersion.version("6.7")) >= 0) {
				return JavaCore.VERSION_15;
			} else if (baseVersion.compareTo(GradleVersion.version("6.3")) >= 0) {
				return JavaCore.VERSION_14;
			} else if (baseVersion.compareTo(GradleVersion.version("6.0")) >= 0) {
				return JavaCore.VERSION_13;
			} else if (baseVersion.compareTo(GradleVersion.version("5.4")) >= 0) {
				return JavaCore.VERSION_12;
			} else if (baseVersion.compareTo(GradleVersion.version("5.0")) >= 0) {
				return JavaCore.VERSION_11;
			} else if (baseVersion.compareTo(GradleVersion.version("4.7")) >= 0) {
				return JavaCore.VERSION_10;
			} else if (baseVersion.compareTo(GradleVersion.version("4.3")) >= 0) {
				return JavaCore.VERSION_9;
			}
		} catch (IllegalArgumentException e) {
			// ignore
		}
		return JavaCore.VERSION_1_8;
	}

	public static boolean hasGradleInvalidTypeCodeException(IStatus status, Path projectFolder, IProgressMonitor monitor) {
		if (!GradleProjectImporter.isFailedStatus(status)) {
			return false;
		}
		if (!isGradleInvalidTypeCodeException(status.getException())) {
			return false;
		}
		GradleVersion version = getGradleVersion(projectFolder, monitor);
		return version != null && version.compareTo(GradleVersion.version(GradleUtils.INVALID_TYPE_FIXED_VERSION)) < 0;
	}

	public static boolean isGradleInvalidTypeCodeException(Throwable throwable) {
		Throwable cause = throwable;
		while (cause != null) {
			String message = cause.getMessage();
			// see https://github.com/gradle/gradle/pull/17397
			if (cause instanceof StreamCorruptedException && message.contains("invalid type code")) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	public static GradleVersion getGradleVersion(Path projectFolder, IProgressMonitor monitor) {
		try {
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(projectFolder, true);
			GradleBuild gradleBuild = GradleCore.getWorkspace().createBuild(build);
			BuildEnvironment environment = gradleBuild.withConnection(connection -> connection.getModel(BuildEnvironment.class), monitor);
			GradleEnvironment gradleEnvironment = environment.getGradle();
			String gradleVersion = gradleEnvironment.getGradleVersion();
			return GradleVersion.version(gradleVersion);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get the Gradle init script file. If any exception happens, a temp file
	 * will be created and be used instead.
	 */
	public static File getGradleInitScript(String scriptPath) {
		try {
			URL fileURL = FileLocator.toFileURL(JavaLanguageServerPlugin.class.getResource(scriptPath));
			String fileString = fileURL.getFile();
			// workaround for https://github.com/eclipse/buildship/issues/1207, buildship doesn't support spaces in passed Gradle arguments
			if (fileString.contains(" ")) {
				return getGradleInitScriptTempFile(scriptPath);
			}

			File initScript = new File(fileString);
			try (InputStream input = JavaLanguageServerPlugin.class.getResourceAsStream(scriptPath)) {
				if (!initScript.exists()) {
					initScript.createNewFile();
					Files.copy(input, initScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
					return initScript;
				}
				byte[] fileBytes = input.readAllBytes();
				byte[] contentDigest = getContentDigest(fileBytes);
				if (needReplaceContent(initScript, contentDigest)) {
					Files.write(initScript.toPath(), fileBytes);
				}
			}
			return initScript;
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
		}

		return getGradleInitScriptTempFile(scriptPath);
	}

	/**
	 * Create a temp file as the Gradle init script.
	 */
	private static File getGradleInitScriptTempFile(String scriptPath) {
		try (InputStream input = JavaLanguageServerPlugin.class.getResourceAsStream(scriptPath)) {
			byte[] fileBytes = input.readAllBytes();
			byte[] contentDigest = getContentDigest(fileBytes);
			String fileName = bytesToHex(contentDigest) + ".gradle";
			File initScript = new File(System.getProperty("java.io.tmpdir"), fileName);
			if (needReplaceContent(initScript, contentDigest)) {
				Files.write(initScript.toPath(), fileBytes);
			}
			return initScript;
		} catch (IOException | NoSuchAlgorithmException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return null;
	}

	/**
	 * Note: The method is public only for testing purpose!
	 * <p>
	 * Check if the content of the input init script file needs to be replaced.
	 * </p>
	 * <p>
	 * The method will return true when:
	 * </p>
	 * <ul>
	 *  <li>The file does not exist.</li>
	 *  <li>The file is empty.</li>
	 *  <li>The checksum of the file content does not match the expected checksum.</li>
	 * <ul>
	 * @param initScript the init script file.
	 * @param checksum the expected checksum of the file.
	 * 
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	public static boolean needReplaceContent(File initScript, byte[] checksum) throws IOException, NoSuchAlgorithmException {
		if (!initScript.exists() || initScript.length() == 0) {
			return true;
		}

		byte[] digest = getContentDigest(Files.readAllBytes(initScript.toPath()));
		if (Arrays.equals(digest, checksum)) {
			return false;
		}
		return true;
	}

	private static byte[] getContentDigest(byte[] contentBytes) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
		md.update(contentBytes);
		return md.digest();
	}

	/**
	 * Convert byte array to hex string.
	 * @param in byte array.
	 */
	private static String bytesToHex(byte[] in) {
		final StringBuilder builder = new StringBuilder();
		for(byte b : in) {
			builder.append(String.format("%02x", b));
		}
		return builder.toString();
	}

	/**
	 * Copied from org.eclipse.m2e.apt.internal.utils.ProjectUtils
	 */
	public static Map<String, String> parseProcessorOptions(List<Object> compilerArgs) {
		if((compilerArgs == null) || compilerArgs.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> options = new HashMap<>();
	
		for(Object arg : compilerArgs) {
			String argString = String.valueOf(arg);
			if (argString.startsWith("-A")) {
				parse(argString.substring(2), options);
			}
		}
		return options;
	}

	private static void parse(String argument, Map<String, String> results) {
		if(argument == null || argument.isBlank()) {
			return;
		}
		String key;
		String value;
		int optionalEqualsIndex = argument.indexOf('=');
		switch(optionalEqualsIndex) {
			case -1: // -Akey : ok
				key = argument;
				value = null;
				break;
			case 0: // -A=value : invalid
				return;
			default:
				key = argument.substring(0, optionalEqualsIndex);
				if (containsWhitespace(key)) { // -A key = value : invalid
					return;
				}
				value = argument.substring(optionalEqualsIndex + 1, argument.length());
		}
		results.put(key, value);
	}

	private static boolean containsWhitespace(String seq) {
		return seq != null && !seq.isBlank() && seq.chars().anyMatch(Character::isWhitespace);
	}

	public static void synchronizeAnnotationProcessingConfiguration(IProgressMonitor monitor) {
		for (IProject project : ProjectUtils.getGradleProjects()) {
			Optional<GradleBuild> build = GradleCore.getWorkspace().getBuild(project);
			if (build.isPresent()) {
				GradleBuildSupport.syncAnnotationProcessingConfiguration(build.get(), monitor);
				// Break the loop because any sub project will update the AP configuration
				// for all the projects. It's the Gradle custom model api's limitation.
				break;
			}
		}
	}

	/**
	 * Find the latest JDK but equal or lower than the {@code highestJavaVersion}.
	 */
	public static File getJdkToLaunchDaemon(String highestJavaVersion) {
		if (StringUtils.isBlank(highestJavaVersion)) {
			return null;
		}

		Map<String, File> jdks = getAllVmInstalls();;
		Entry<String, File> selected = null;
		for (Entry<String, File> jdk : jdks.entrySet()) {
			String javaVersion = jdk.getKey();
			if (Version.parse(javaVersion).compareTo(Version.parse(highestJavaVersion)) <= 0
					&& (selected == null || Version.parse(selected.getKey()).compareTo(Version.parse(javaVersion)) < 0)) {
				selected = jdk;
			}
		}

		return selected == null ? null : selected.getValue();
	}

	/**
	 * Get all the available JDK installations in the Eclipse VM registry. If multiple installations
	 * are found for the same major version, the first found one is return.
	 *
	 * The results are returned as map, where key is the major version and value is the file instance of
	 * the installation.
	 */
	private static Map<String, File> getAllVmInstalls() {
		List<IVMInstall> vmList = Stream.of(JavaRuntime.getVMInstallTypes())
						.map(IVMInstallType::getVMInstalls)
						.flatMap(Arrays::stream)
						.toList();
		Map<String, File> vmInstalls = new HashMap<>();
		for (IVMInstall vmInstall : vmList) {
			if (vmInstall instanceof AbstractVMInstall vm) {
				String javaVersion = getMajorJavaVersion(vm.getJavaVersion());
				if (StringUtils.isBlank(javaVersion) || vm.getInstallLocation() == null) {
					continue;
				}

				vmInstalls.putIfAbsent(javaVersion, vm.getInstallLocation());
			}
		}

		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		Set<RuntimeEnvironment> runtimes = preferences.getRuntimes();
		for (RuntimeEnvironment runtime : runtimes) {
			if (StringUtils.isBlank(runtime.getPath())) {
				continue;
			}
			File javaHome = new File(runtime.getPath());
			if (vmInstalls.containsValue(javaHome)) {
				continue;
			}

			String javaVersion = new StandardVMType().readReleaseVersion(javaHome);
			if (StringUtils.isNotBlank(javaVersion)) {
				// the user preference should have higher priority and replace
				// the existing one if the major version is the same.
				vmInstalls.put(getMajorJavaVersion(javaVersion), javaHome);
			}
		}

		return vmInstalls;
	}

	public static String getMajorJavaVersion(String version) {
		return CompilerOptions.versionFromJdkLevel(CompilerOptions.versionToJdkLevel(version));
	}
}
