/*******************************************************************************
 * Copyright (c) 2000-2023 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.Arrays;
import java.util.Collection;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.util.StringMatcher;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * copied from org.eclipse.jdt.internal.corext.util.TypeFilter
 */
public class TypeFilter {

	public static final String TYPEFILTER_ENABLED = "org.eclipse.jdt.ui.typefilter.enabled"; //$NON-NLS-1$

	public static TypeFilter getDefault() {
		return JavaLanguageServerPlugin.getInstance().getTypeFilter();
	}

	public static boolean isFiltered(String fullTypeName) {
		return getDefault().filter(fullTypeName);
	}

	public static boolean isFiltered(char[] fullTypeName) {
		return getDefault().filter(new String(fullTypeName));
	}

	public static boolean isFiltered(char[] packageName, char[] typeName) {
		return getDefault().filter(JavaModelUtil.concatenateName(packageName, typeName));
	}

	public static boolean isFiltered(IType type) {
		TypeFilter typeFilter = getDefault();
		if (typeFilter.hasFilters()) {
			return typeFilter.filter(type.getFullyQualifiedName('.'));
		}
		return false;
	}

	public static boolean isFiltered(TypeNameMatch match) {
		boolean filteredByPattern= getDefault().filter(match.getFullyQualifiedName());
		if (filteredByPattern) {
			return true;
		}

		int accessibility= match.getAccessibility();
		switch (accessibility) {
			case IAccessRule.K_NON_ACCESSIBLE:
				return JavaCore.ENABLED.equals(JavaCore.getOption(JavaCore.CODEASSIST_FORBIDDEN_REFERENCE_CHECK));
			case IAccessRule.K_DISCOURAGED:
				return JavaCore.ENABLED.equals(JavaCore.getOption(JavaCore.CODEASSIST_DISCOURAGED_REFERENCE_CHECK));
			default:
				return false;
		}
	}

	/**
	 * Remove the type filter if any of the imported element matches.
	 */
	public synchronized void removeFilterIfMatched(Collection<String> importedElements) {
		if (importedElements == null || importedElements.isEmpty()) {
			return;
		}

		StringMatcher[] matchers = getStringMatchers();
		this.fStringMatchers = Arrays.stream(matchers).filter(m -> {
			for (String importedElement : importedElements) {
				if (m.match(importedElement)) {
					return false;
				}
			}
			return true;
		}).toArray(size -> new StringMatcher[size]);
	}

	private StringMatcher[] fStringMatchers;

	public TypeFilter() {
		fStringMatchers= null;
	}

	private synchronized StringMatcher[] getStringMatchers() {
		if (fStringMatchers == null) {
			String str = getPreference(TYPEFILTER_ENABLED);
			StringTokenizer tok= new StringTokenizer(str, ";"); //$NON-NLS-1$
			int nTokens= tok.countTokens();

			fStringMatchers= new StringMatcher[nTokens];
			for (int i= 0; i < nTokens; i++) {
				String curr= tok.nextToken();
				if (curr.length() > 0) {
					fStringMatchers[i]= new StringMatcher(curr, false, false);
				}
			}
		}
		return fStringMatchers;
	}

	public boolean hasFilters() {
		return getStringMatchers().length > 0;
	}

	/**
	 * @param fullTypeName fully-qualified type name
	 * @return <code>true</code> iff the given type is filtered out
	 */
	public boolean filter(String fullTypeName) {
		StringMatcher[] matchers= getStringMatchers();
		for (int i= 0; i < matchers.length; i++) {
			StringMatcher curr= matchers[i];
			if (curr.match(fullTypeName)) {
				return true;
			}
		}
		return false;
	}

	public static String getPreference(String key) {
		String val = InstanceScope.INSTANCE.getNode(JavaManipulation.getPreferenceNodeId()).get(key, null);
		if (val != null) {
			return val;
		}
		return DefaultScope.INSTANCE.getNode(JavaManipulation.getPreferenceNodeId()).get(key, null);
	}

	public void dispose() {
		fStringMatchers = null;
	}

}
