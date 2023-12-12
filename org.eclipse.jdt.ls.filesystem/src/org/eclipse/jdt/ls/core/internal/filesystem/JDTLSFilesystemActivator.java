package org.eclipse.jdt.ls.core.internal.filesystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
public class JDTLSFilesystemActivator implements BundleActivator {

	private static BundleContext context;
	private static final String JAVA_LS_PLUGIN_ID = "org.eclipse.jdt.ls.core";
	private static final String JAVA_RESOURCE_FILTERS = "java.project.resourceFilters";
	private static final String JAVA_RESOURCE_FILTERS_DEFAULT = "node_modules::\\.git";
	private List<Pattern> resourcePatterns; 
	private String resourceFilters;
	private static JDTLSFilesystemActivator instance;

	static BundleContext getContext() {
		return context;
	}

	public void start(BundleContext bundleContext) throws Exception {
		JDTLSFilesystemActivator.context = bundleContext;
		JDTLSFilesystemActivator.instance = this;
		configureResourceFilters();
	}

	private void configureResourceFilters() {
		IEclipsePreferences eclipsePreferences = InstanceScope.INSTANCE.getNode(JAVA_LS_PLUGIN_ID);
		if (eclipsePreferences != null) {
			resourceFilters = eclipsePreferences.get(JAVA_RESOURCE_FILTERS, JAVA_RESOURCE_FILTERS_DEFAULT);
			eclipsePreferences.addPreferenceChangeListener(new IPreferenceChangeListener() {

				@Override
				public void preferenceChange(PreferenceChangeEvent event) {
					if (event.getNewValue() instanceof String newValue && Objects.equals(JAVA_RESOURCE_FILTERS, event.getKey()) && !Objects.equals(resourceFilters, event.getNewValue())) {
						resourceFilters = newValue;
						setResourcePatterns();
					}
				}
			});
		} else {
			resourceFilters = JAVA_RESOURCE_FILTERS_DEFAULT;
		}
		setResourcePatterns();
	}

	protected void setResourcePatterns() {
		resourcePatterns = new ArrayList<>();
		for (String element : resourceFilters.split("::")) {
			Pattern pattern = Pattern.compile(element);
			resourcePatterns.add(pattern);
		}
	}

	public void stop(BundleContext bundleContext) throws Exception {
		JDTLSFilesystemActivator.context = null;
	}

	public static List<Pattern> getResourcePatterns() {
		if (instance != null) {
			return instance.resourcePatterns;
		}
		return null;
	}

}
