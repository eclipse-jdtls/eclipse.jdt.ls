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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JDTUtils.toLocation;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.ImplementationCollector.ResultMapper;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Maps {@link IJavaElement} and a position to a {@link Location} object.
 */
public class ImplementationToLocationMapper implements ResultMapper<Location> {

	private boolean includeClassFiles;
	private boolean useDefaultPosition;

	/**
	 * Creates a ImplementationToLocationMapper instance. If
	 * <b>includeClassFiles</b> is <code>true</code>, will include elements from
	 * binary files. If <b>useDefaultPosition</b> is <code>true</code>, returned
	 * {@link Location} will always return a default {@link Range}, at
	 * {@link Position}s [0,0].
	 *
	 * @param includeClassFiles,
	 *            if <code>true</code>, will include elements from binary files
	 * @param useDefaultPosition,
	 *            if <code>true</code>, will always return a default
	 *            Range([0,0],[0,0]) ( to avoid actually reading files)
	 */
	public ImplementationToLocationMapper(boolean includeClassFiles, boolean useDefaultPosition) {
		this.includeClassFiles = includeClassFiles;
		this.useDefaultPosition = useDefaultPosition;
	}

	@Override
	public Location convert(IJavaElement element, int offset, int position) {
		ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		Location location = null;
		try {
			if (compilationUnit != null) {
				if (useDefaultPosition || offset > 0 && position > 0) {
					//builds location from offset and position directly
					location = toLocation(compilationUnit, offset, position);
				} else {
					// opens file to determine location
					location = toLocation(element);
				}

			} else if (includeClassFiles) {
				IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
				if (cf != null) {
					if (useDefaultPosition || offset > 0 && position > 0) {
						//builds location from offset and position directly
						location = toLocation(cf, offset, position);
					} else {
						//opens source to determine location
						location = toLocation(element);
						if (location == null) {//If no source was attached, return default location
							location = toLocation(cf, 0, 0);
						}
					}
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to convert " + element, e);
		}
		return location;
	}

}
