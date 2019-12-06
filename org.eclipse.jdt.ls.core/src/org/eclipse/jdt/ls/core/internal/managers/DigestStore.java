/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;

/**
 * @author Thomas Mäder
 *
 *         This class handles digests for build files. It serves to prevent
 *         unnecessary updating of maven/gradle, etc. info on workspace
 *         projects.
 */
public class DigestStore {
	private Map<String, String> fileDigests;
	private File stateFile;

	private static final String SERIALIZATION_FILE_NAME = ".file-digests";

	public DigestStore(File stateLocation) {
		this.stateFile = new File(stateLocation, SERIALIZATION_FILE_NAME);
		if (stateFile.isFile()) {
			fileDigests = deserializeFileDigests();
		} else {
			fileDigests = new HashMap<>();
		}
	}

	/**
	 * Updates the digest for the given path.
	 *
	 * @param p
	 *            Path to the file in questions
	 * @return whether the file is considered changed and the associated project
	 *         should be updated
	 * @throws CoreException
	 *             if a digest cannot be computed
	 */
	public boolean updateDigest(Path p) throws CoreException {
		try {
			String digest = computeDigest(p);
			synchronized (fileDigests) {
				if (!digest.equals(fileDigests.get(p.toString()))) {
					fileDigests.put(p.toString(), digest);
					serializeFileDigests();
					return true;
				} else {
					return false;
				}
			}
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Exception updating digest for " + p, e));
		}

	}

	private void serializeFileDigests() {
		try (ObjectOutputStream outStream = new ObjectOutputStream(new FileOutputStream(stateFile))) {
			outStream.writeObject(fileDigests);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Exception occured while serialization of file digests", e);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> deserializeFileDigests() {
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
			return (Map<String, String>) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			JavaLanguageServerPlugin.logException("Exception occured while deserialization of file digests", e);
			return new HashMap<>();
		}
	}

	private String computeDigest(Path path) throws IOException, NoSuchAlgorithmException {
		byte[] fileBytes = Files.readAllBytes(path);
		byte[] digest = MessageDigest.getInstance("MD5").digest(fileBytes);
		return Arrays.toString(digest);
	}

}
