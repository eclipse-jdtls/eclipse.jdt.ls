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

	private String checksum;
	private String wrapperJar;
	private boolean valid;

	public ValidationResult(String wrapperJar, String checksum, boolean valid) {
		this.wrapperJar = wrapperJar;
		this.checksum = checksum;
		this.valid = valid;
	}

	public String getChecksum() {
		return checksum;
	}

	public boolean isValid() {
		return valid;
	}

	public String getWrapperJar() {
		return wrapperJar;
	}

}

