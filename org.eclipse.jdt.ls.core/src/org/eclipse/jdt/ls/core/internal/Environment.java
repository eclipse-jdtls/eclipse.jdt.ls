/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

/**
 * Helper class for reading environment variables and system properties.
 *
 * @author Gorkem Ercan
 *
 */
public final class Environment {

	/**
	 * Retrieves environment variable value for given name.
	 *
	 * @return value or null
	 */
	public static String getEnvironment(final String name){
		if(name == null || name.isEmpty()) {
			return null;
		}
		return System.getenv(name);
	}

	/**
	 * Retrieves system property value for given name.
	 *
	 * @return value or null
	 */
	public static String getProperty(final String name){
		if(name == null || name.isEmpty()) {
			return null;
		}
		return System.getProperty(name);
	}

	/**
	 * Retrieves environment variable or system property value for given name.
	 * Checks the environment value first.
	 *
	 * @return value or null
	 */
	public static String get(final String name){
		String value = getEnvironment(name);
		if(value == null) {
			value = getProperty(name);
		}
		return value;
	}

	/**
	 * Retrieves environment variable or system property value for given name.
	 * Checks the environment value first.
	 *
	 * @return value or null
	 */
	public static String get(final String name, final String defaultValue){
		String value = get(name);
		if(value == null) {
			value = defaultValue;
		}
		return value;
	}

}
