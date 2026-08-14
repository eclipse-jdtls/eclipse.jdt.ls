/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import com.google.gson.Gson;

/**
 * Adapts Eclipse LTK refactoring statuses to the language-server protocol and
 * creates fingerprints for refactoring confirmations that span multiple client
 * requests.
 */
public final class RefactoringConfirmation {
	private static final Gson GSON = new Gson();

	private RefactoringConfirmation() {
	}

	/**
	 * Creates a deterministic fingerprint for the operation, its inputs, and the
	 * problems the user is being asked to confirm.
	 *
	 * @param operationId a versioned identifier for the confirmation protocol
	 * @param status the refactoring problems being confirmed
	 * @param confirmationInputs the request and workspace state that must still match
	 * @return a lowercase hexadecimal SHA-256 fingerprint
	 */
	public static String createToken(String operationId, RefactoringStatus status, Object... confirmationInputs) {
		Objects.requireNonNull(operationId, "operationId");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			updateDigest(digest, operationId);
			for (Object input : confirmationInputs) {
				updateDigest(digest, GSON.toJson(input));
			}
			if (status != null) {
				for (RefactoringStatusEntry entry : status.getEntries()) {
					updateDigest(digest, Integer.toString(entry.getSeverity()));
					updateDigest(digest, entry.getMessage());
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static void updateDigest(MessageDigest digest, String value) {
		byte[] bytes = Objects.toString(value, "").getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
