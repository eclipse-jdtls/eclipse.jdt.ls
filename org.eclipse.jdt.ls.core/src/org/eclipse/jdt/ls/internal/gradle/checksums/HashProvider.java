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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 *
 * @author snjeza
 *
 */
public class HashProvider {
	public static final String SHA256 = "SHA-256";
	public static final String SHA1 = "SHA-1";

	private String alghorithm;

	public HashProvider() {
		this(SHA256);
	}

	public HashProvider(String alghorithm) {
		this.alghorithm = alghorithm;
	}

	public String getChecksum(File file) throws IOException {
		MessageDigest messageDigest;
		try {
			messageDigest = MessageDigest.getInstance(alghorithm);
		} catch (NoSuchAlgorithmException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return null;
		}
		try (DigestInputStream dis = new DigestInputStream(new FileInputStream(file), messageDigest)) {
			byte[] bytes = new byte[32768];
			while (dis.read(bytes) != -1) {
				;
			}
			messageDigest = dis.getMessageDigest();
		}
		StringBuilder result = new StringBuilder();
		for (byte b : messageDigest.digest()) {
			result.append(String.format("%02x", b));
		}
		return result.toString();
	}
}

