/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import org.eclipse.core.runtime.Plugin;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;

/**
 * @author Thomas Mäder
 *
 *         This class handles digests for pom files. It serves to prevent
 *         unnecessary updating of maven info on workspace projects.
 *
 */
public class DigestStore {
	private Map<String, String> pomDigests;

	private File stateFile;

	private static final String POM_SERIALIZATION_FILE_NAME = ".pom-digests";

	public DigestStore(Plugin plugin) {
		this.stateFile = plugin.getStateLocation().append(POM_SERIALIZATION_FILE_NAME).toFile();
		if (stateFile.isFile()) {
			pomDigests = deserializePomDigests();
		} else {
			pomDigests = new HashMap<>();
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
			synchronized (pomDigests) {
				if (!digest.equals(pomDigests.get(p.toString()))) {
					pomDigests.put(p.toString(), digest);
					serializePomDigests();
					return true;
				} else {
					return false;
				}
			}
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Exception updating Maven project", e));
		}

	}

	private void serializePomDigests() {
		try (ObjectOutputStream outStream = new ObjectOutputStream(new FileOutputStream(stateFile))) {
			outStream.writeObject(pomDigests);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Exception occured while serialization of pom digests", e);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> deserializePomDigests() {
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
			return (Map<String, String>) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			JavaLanguageServerPlugin.logException("Exception occured while deserialization of pom digests", e);
			return new HashMap<>();
		}
	}

	private String computeDigest(Path path) throws IOException, NoSuchAlgorithmException {
		byte[] fileBytes = Files.readAllBytes(path);
		byte[] digest = MessageDigest.getInstance("MD5").digest(fileBytes);
		return Arrays.toString(digest);
	}

}
