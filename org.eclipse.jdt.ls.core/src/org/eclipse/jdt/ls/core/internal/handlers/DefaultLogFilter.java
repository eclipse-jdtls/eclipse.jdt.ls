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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.function.Predicate;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.manipulation.JavaManipulation;

/**
 * Default Log filter. Excludes the following messages from being logged to the
 * client:
 * <ul>
 * <li>"Missing resource filter type"</li>
 * </ul>
 *
 * @author Fred Bricon
 *
 */
public class DefaultLogFilter implements Predicate<IStatus> {

	private static final String MISSING_RESOURCE_FILTER_TYPE = "Missing resource filter type";
	
	private static final String AST_CREATION_ERROR = "Exception occurred during compilation unit conversion";

	@Override
	public boolean test(IStatus status) {

		String message = getMessage(status);
		// Checking for status messages is a bit weak, since it could still change in theory (although highly unlikely)
		// and might fail in case of I18n'ed messages
		if (message == null || message.startsWith(MISSING_RESOURCE_FILTER_TYPE) 
				// Hack to silence errors logged in CoreASTProvider.getAST()
				// See https://github.com/eclipse/eclipse.jdt.ls/issues/2608
				// See https://github.com/eclipse-jdt/eclipse.jdt.core/issues/317
				|| message.startsWith(AST_CREATION_ERROR)
				|| JavaManipulation.ID_PLUGIN.equals(status.getPlugin())) {
			return false;
		}
		return true;
	}

	private String getMessage(IStatus status) {
		return status == null ? null : status.getMessage();
	}


}
