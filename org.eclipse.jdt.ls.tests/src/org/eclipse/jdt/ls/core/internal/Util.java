/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

public class Util {

	private Util() {
		// private constructor - only static methods are expected
	}

	public static String convertToIndependentLineDelimiter(String source) {
		if (source.indexOf('\n') == -1 && source.indexOf('\r') == -1) return source;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0, length = source.length(); i < length; i++) {
			char car = source.charAt(i);
			if (car == '\r') {
				buffer.append('\n');
				if (i < length-1 && source.charAt(i+1) == '\n') {
					i++; // skip \n after \r
				}
			} else {
				buffer.append(car);
			}
		}
		return buffer.toString();
	}

}
