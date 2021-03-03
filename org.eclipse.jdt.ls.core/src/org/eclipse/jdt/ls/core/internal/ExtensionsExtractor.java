/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

/**
 * The class simplifies extracting extensions from the extension points.
 *
 * @author D.Bushenko
 *
 */
public class ExtensionsExtractor {
	public static <T> List<T> extractExtensions( final String namespace
											   , final String extensionPointName) {

		final var extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(namespace, extensionPointName);
		final var configs = extensionPoint.getConfigurationElements();

		return Arrays
				.stream(configs)
				.map(ExtensionsExtractor::<T>makeExtension)
				.collect(Collectors.toUnmodifiableList());
	}

	@SuppressWarnings("unchecked")
	private static <T> T makeExtension(IConfigurationElement config) {
		try {
			return (T) config.createExecutableExtension("class");

		} catch (Exception ex) {
			throw new IllegalArgumentException("Could not create the extension", ex);
		}
	}

}
