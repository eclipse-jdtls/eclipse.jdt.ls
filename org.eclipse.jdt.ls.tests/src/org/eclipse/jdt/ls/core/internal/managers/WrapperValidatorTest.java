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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.internal.gradle.checksums.ValidationResult;
import org.eclipse.jdt.ls.internal.gradle.checksums.WrapperValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * @author snjeza
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class WrapperValidatorTest extends AbstractGradleBasedTest{

	@Before
	public void setProperty() throws Exception {
		System.setProperty("gradle.checksum.cacheDir", "target/gradle/checksums");
		WrapperValidator.clear();
	}

	@After
	public void clearProperty() throws IOException {
		System.clearProperty("gradle.checksum.cacheDir");
	}

	@Test
	public void testGradleWrapper() throws Exception {
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		assertTrue(file.isDirectory());
		File sha256Directory = WrapperValidator.getSha256CacheFile();
		assertTrue(sha256Directory.isDirectory());
		ValidationResult result = new WrapperValidator(100).checkWrapper(file.getAbsolutePath());
		assertTrue(result.isValid());
		// test cache
		assertTrue(sha256Directory.isDirectory());
		String fileName = "gradle-6.3-wrapper.jar.sha256";
		Bundle bundle = Platform.getBundle(IConstants.PLUGIN_ID);
		URL url = FileLocator.find(bundle, new org.eclipse.core.runtime.Path(WrapperValidator.GRADLE_CHECKSUMS));
		String sha256 = null;
		if (url == null) {
			String message = Files.list(Paths.get(sha256Directory.getAbsolutePath())).collect(Collectors.toList()).toString();
			file = new File(sha256Directory, fileName);
			if (file.isFile()) {
				assertTrue(message, file.isFile());
				sha256 = Files.lines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8).findFirst().get();
			}
		} else {
			try (InputStream inputStream = url.openStream(); InputStreamReader inputStreamReader = new InputStreamReader(inputStream); Reader reader = new BufferedReader(inputStreamReader)) {
				JsonElement jsonElement = JsonParser.parseReader(reader);
				if (jsonElement instanceof JsonArray) {
					JsonArray array = (JsonArray) jsonElement;
					for (JsonElement json : array) {
						String wrapperChecksumUrl = json.getAsJsonObject().get("wrapperChecksumUrl").getAsString();
						if (wrapperChecksumUrl != null && wrapperChecksumUrl.endsWith("/" + fileName)) {
							sha256 = json.getAsJsonObject().get("sha256").getAsString();
							break;
						}
					}
				}
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
		}
		assertEquals("1cef53de8dc192036e7b0cc47584449b0cf570a00d560bfaa6c9eabe06e1fc06", sha256);
	}

	@Test
	public void testMissingSha256() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator(100);
		Set<String> allowed = WrapperValidator.getAllowed();
		Set<String> disallowed = WrapperValidator.getDisallowed();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		wrapperValidator.checkWrapper(file.getAbsolutePath());
		int size = WrapperValidator.size();
		List<String> sha256 = new ArrayList<>();
		try {
			sha256.add("41c8aa7a337a44af18d8cda0d632ebba469aef34f3041827624ef5c1a4e4419d");
			WrapperValidator.clear();
			WrapperValidator.disallow(sha256);
			assertTrue(file.isDirectory());
			ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertFalse(result.isValid());
			assertNotNull(result.getChecksum());
			WrapperValidator.clear();
			WrapperValidator.allow(sha256);
			result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertTrue(result.isValid());
		} finally {
			WrapperValidator.clear();
			WrapperValidator.allow(allowed);
			WrapperValidator.disallow(disallowed);
			wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertEquals(size, WrapperValidator.size());
		}
	}

	@Test
	public void testPreferences() throws Exception {
		WrapperValidator wrapperValidator = new WrapperValidator(100);
		Set<String> allowed = WrapperValidator.getAllowed();
		Set<String> disallowed = WrapperValidator.getDisallowed();
		File file = new File(getSourceProjectDirectory(), "gradle/gradle-4.0");
		wrapperValidator.checkWrapper(file.getAbsolutePath());
		int size = WrapperValidator.size();
		List list = new ArrayList();
		Map map = new HashMap();
		map.put("sha256", "41c8aa7a337a44af18d8cda0d632ebba469aef34f3041827624ef5c1a4e4419d");
		map.put("allowed", Boolean.TRUE);
		list.add(map);
		try {
			ValidationResult result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertFalse(result.isValid());
			assertNotNull(result.getChecksum());
			WrapperValidator.clear();
			WrapperValidator.putSha256(list);
			result = wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertTrue(result.isValid());
		} finally {
			WrapperValidator.clear();
			WrapperValidator.allow(allowed);
			WrapperValidator.disallow(disallowed);
			wrapperValidator.checkWrapper(file.getAbsolutePath());
			assertEquals(size, WrapperValidator.size());
		}
	}
}
