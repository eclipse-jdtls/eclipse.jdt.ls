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

/**
 *
 * @author snjeza
 *
 */
public class ValidationResult {

	public enum Status {
		VALID,
		INVALID,
		UNVERIFIABLE
	}

	private String checksum;
	private String wrapperJar;
	private Status status;

	public ValidationResult(String wrapperJar, String checksum, Status status) {
		this.wrapperJar = wrapperJar;
		this.checksum = checksum;
		this.status = status;
	}

	public String getChecksum() {
		return checksum;
	}

	public boolean isValid() {
		return status == Status.VALID;
	}

	public boolean isUnverifiable() {
		return status == Status.UNVERIFIABLE;
	}

	public Status getStatus() {
		return status;
	}

	public String getWrapperJar() {
		return wrapperJar;
	}

}
