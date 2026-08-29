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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult.Status;
import org.eclipse.jdt.ls.internal.gradle.checksums.WrapperValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author snjeza
 *
 */
@ExtendWith(MockitoExtension.class)
public class WrapperValidatorTest extends AbstractGradleBasedTest{

	@BeforeEach
	public void setProperty() throws Exception {
		System.setProperty("gradle.checksum.cacheDir", "target/gradle/checksums");
		WrapperValidator.clear();
	}

	@AfterEach
	public void clearProperty() throws IOException {
		System.clearProperty("gradle.checksum.cacheDir");
	}

	@Test
	public void testGradleWrapper() throws Exception {
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		assertTrue(file.isDirectory());
		ValidationResult result = new WrapperValidator().checkWrapper(file.getAbsolutePath());
		assertTrue(result.isValid());
		assertEquals(Status.VALID, result.getStatus());
		assertNotNull(result.getChecksum());
	}

	@Test
	public void testVersionParsing() throws Exception {
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		String version = WrapperValidator.parseGradleVersion(file.toPath());
		assertEquals("8.5", version);

		File file2 = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		String version2 = WrapperValidator.parseGradleVersion(file2.toPath());
		assertEquals("4.0", version2);
	}

	@Test
	public void testVersionExtractionFromChecksumUrl() throws Exception {
		assertEquals("8.5", WrapperValidator.extractVersionFromChecksumUrl(
			"https://services.gradle.org/distributions/gradle-8.5-wrapper.jar.sha256"));
		assertEquals("9.5.0-milestone-4", WrapperValidator.extractVersionFromChecksumUrl(
			"https://services.gradle.org/distributions/gradle-9.5.0-milestone-4-wrapper.jar.sha256"));
		assertEquals("9.5.0-20260226004946+0000", WrapperValidator.extractVersionFromChecksumUrl(
			"https://services.gradle.org/distributions-snapshots/gradle-9.5.0-20260226004946+0000-wrapper.jar.sha256"));
	}

	@Test
	public void testMissingSha256() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		// gradle-4.0 is not in bundled checksums, so should be UNVERIFIABLE (no network in tests)
		ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
		assertNotNull(result.getChecksum());
		// Since gradle 4.0 is not in bundled checksums and network fetch will fail in tests,
		// the result should be UNVERIFIABLE
		assertEquals(Status.UNVERIFIABLE, result.getStatus());
	}

	@Test
	public void testDisallowedChecksum() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		List<String> sha256 = new ArrayList<>();
		sha256.add("41c8aa7a337a44af18d8cda0d632ebba469aef34f3041827624ef5c1a4e4419d");
		try {
			WrapperValidator.disallow(sha256);
			ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertFalse(result.isValid());
			assertEquals(Status.INVALID, result.getStatus());
			assertNotNull(result.getChecksum());
		} finally {
			WrapperValidator.clear();
		}
	}

	@Test
	public void testAllowedChecksum() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		List<String> sha256 = new ArrayList<>();
		sha256.add("41c8aa7a337a44af18d8cda0d632ebba469aef34f3041827624ef5c1a4e4419d");
		try {
			WrapperValidator.allow(sha256);
			ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertTrue(result.isValid());
			assertEquals(Status.VALID, result.getStatus());
		} finally {
			WrapperValidator.clear();
		}
	}

	@Test
	public void testPreferences() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		List list = new ArrayList();
		Map map = new HashMap();
		map.put("sha256", "41c8aa7a337a44af18d8cda0d632ebba469aef34f3041827624ef5c1a4e4419d");
		map.put("allowed", Boolean.TRUE);
		list.add(map);
		try {
			WrapperValidator.putSha256(list);
			ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertTrue(result.isValid());
			assertEquals(Status.VALID, result.getStatus());
		} finally {
			WrapperValidator.clear();
		}
	}

	@Test
	public void testUnverifiable() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		// gradle 4.0 is not in bundled checksums and network is not available in tests
		ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
		assertTrue(result.isUnverifiable());
		assertEquals(Status.UNVERIFIABLE, result.getStatus());
		assertNotNull(result.getChecksum());
	}

	@Test
	public void testTamperedDistributionUrlWithUnknownJar() throws Exception {
		// tampered-wrapper has a non-standard distributionUrl and a jar whose
		// checksum is not in bundled data -> should be INVALID
		File file = new File(getSourceProjectDirectory(), "gradle/tampered-wrapper");
		assertTrue(file.isDirectory());
		String version = WrapperValidator.parseGradleVersion(file.toPath());
		assertNull(version, "Tampered distributionUrl should not be parseable");
		ValidationResult result = new WrapperValidator().checkWrapper(file.getAbsolutePath());
		assertFalse(result.isValid());
		assertEquals(Status.INVALID, result.getStatus());
	}

	@Test
	public void testTamperedDistributionUrlWithKnownJar() throws Exception {
		// simple-gradle has a jar whose checksum IS in bundled data;
		// even with a tampered distributionUrl, the known-good jar should be accepted
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		// Temporarily overwrite the properties to simulate a tampered URL
		Path propsPath = file.toPath().resolve("gradle/wrapper/gradle-wrapper.properties");
		String original = Files.readString(propsPath);
		try {
			Files.writeString(propsPath, original.replace(
					"https\\://services.gradle.org/distributions/gradle-8.5-bin.zip",
					"https\\://evil.com/malware.zip"));
			String version = WrapperValidator.parseGradleVersion(file.toPath());
			assertNull(version, "Tampered distributionUrl should not be parseable");
			ValidationResult result = new WrapperValidator().checkWrapper(file.getAbsolutePath());
			assertTrue(result.isValid(), "Known-good jar should still be accepted");
			assertEquals(Status.VALID, result.getStatus());
		} finally {
			Files.writeString(propsPath, original);
		}
	}
}
