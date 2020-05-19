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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult;
import org.eclipse.jdt.ls.internal.gradle.checksums.WrapperValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author snjeza
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class WrapperValidatorTest extends AbstractGradleBasedTest{

	@Before
	public void setProperty() throws Exception {
		System.setProperty("gradle.checksum.cacheDir", "target/gradle/checksums");
	}

	@After
	public void clearProperty() {
		System.clearProperty("gradle.checksum.cacheDir");
	}

	@Test
	public void testGradleWrapper() throws Exception {
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		assertTrue(file.isDirectory());
		ValidationResult result = new WrapperValidator().checkWrapper(file.getAbsolutePath());
		assertTrue(result.isValid());
		File sha256Directory = WrapperValidator.getSha256CacheFile();
		// test cache
		file = new File(sha256Directory, "gradle-6.4-wrapper.jar.sha256");
		String sha256 = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8).findFirst().get();
		assertEquals("70239e6ca1f0d5e3b2808ef6d82390cf9ad58d3a3a0d271677a51d1b89475857", sha256);
	}

	@Test
	public void testMissingSha256() throws Exception {
		Preferences prefs = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		List<String> allowed = prefs.getSha256Allowed();
		int size = WrapperValidator.size();
		WrapperValidator wrapperValidator = new WrapperValidator();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		try {
			List<String> sha256 = new ArrayList<>();
			sha256.add("41c8aa7a337a44af18d8cda0d632ebba469aef34f3041827624ef5c1a4e4419d");
			prefs.putSha256(null, sha256);
			assertTrue(file.isDirectory());
			ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertFalse(result.isValid());
			assertNotNull(result.getChecksum());
			prefs.putSha256(sha256, null);
			result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertTrue(result.isValid());
		} finally {
			prefs.putSha256(allowed, null);
			wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertEquals(size, WrapperValidator.size());
		}
	}
}
