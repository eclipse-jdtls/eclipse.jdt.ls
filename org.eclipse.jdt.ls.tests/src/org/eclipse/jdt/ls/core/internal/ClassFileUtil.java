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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.lsp4j.Location;

/**
 * @author snjeza
 * @author Fred Bricon
 *
 */
public final class ClassFileUtil {

	private ClassFileUtil() {
	}

	public static String getURI(IProject project, String fqcn) throws JavaModelException {
		Assert.isNotNull(project, "Project can't be null");
		Assert.isNotNull(fqcn, "FQCN can't be null");

		IJavaProject javaProject = JavaCore.create(project);
		int lastDot = fqcn.lastIndexOf(".");
		String packageName = lastDot > 0? fqcn.substring(0, lastDot):"";
		String className = lastDot > 0? fqcn.substring(lastDot+1):fqcn;
		ClassUriExtractor extractor = new ClassUriExtractor();
		new SearchEngine().searchAllTypeNames(packageName.toCharArray(),SearchPattern.R_EXACT_MATCH,
				className.toCharArray(), SearchPattern.R_EXACT_MATCH,
				IJavaSearchConstants.TYPE,
				JDTUtils.createSearchScope(javaProject, JavaLanguageServerPlugin.getPreferencesManager()),
				extractor,
				IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
				new NullProgressMonitor());
		return extractor.uri;
	}

	private static class ClassUriExtractor extends TypeNameMatchRequestor {

		String uri;

		@Override
		public void acceptTypeNameMatch(TypeNameMatch match) {
			try {
				if (match.getType().isBinary()) {
					Location location = JDTUtils.toLocation(match.getType().getClassFile());
					if (location != null) {
						uri = location.getUri();
					}
				}  else {
					uri = match.getType().getResource().getLocationURI().toString();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

}
