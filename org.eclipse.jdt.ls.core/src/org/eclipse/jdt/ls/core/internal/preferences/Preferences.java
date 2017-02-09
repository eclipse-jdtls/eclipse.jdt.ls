/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import java.util.Map;

import org.eclipse.lsp4j.MessageType;

/**
 * Preferences model
 *
 * @author Fred Bricon
 *
 */
public class Preferences {

	/**
	 * Preference key to enable/disable reference code lenses.
	 */
	public static final String REFERENCES_CODE_LENS_ENABLED_KEY = "java.referencesCodeLens.enabled";

	/**
	 * Preference key for project build/configuration update settings.
	 */
	public static final String CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY = "java.configuration.updateBuildConfiguration";

	/**
	 * Preference key for incomplete classpath severity messages.
	 */
	public static final String ERRORS_INCOMPLETE_CLASSPATH_SEVERITY_KEY = "java.errors.incompleteClasspath.severity";

	private Severity incompleteClasspathSeverity;
	private FeatureStatus updateBuildConfigurationStatus;
	private boolean referencesCodeLensEnabled;

	public static enum Severity {
		ignore, log, info, warning, error;

		static Severity fromString(String value, Severity defaultSeverity) {
			if (value != null) {
				String val = value.toLowerCase();
				try {
					return valueOf(val);
				} catch(Exception e) {
					//fall back to default severity
				}
			}
			return defaultSeverity;
		}

		public MessageType toMessageType() {
			for (MessageType type : MessageType.values()) {
				if (name().equalsIgnoreCase(type.name())) {
					return type;
				}
			}
			//'ignore' has no MessageType equivalent
			return null;
		}
	}

	public static enum FeatureStatus {
		disabled, interactive, automatic ;

		static FeatureStatus fromString(String value, FeatureStatus defaultStatus) {
			if (value != null) {
				String val = value.toLowerCase();
				try {
					return valueOf(val);
				} catch(Exception e) {
					//fall back to default severity
				}
			}
			return defaultStatus;
		}
	}

	public Preferences() {
		incompleteClasspathSeverity = Severity.warning;
		updateBuildConfigurationStatus = FeatureStatus.interactive;
		referencesCodeLensEnabled = true;
	}

	/**
	 * Create a {@link Preferences} model from a {@link Map} configuration.
	 */
	public static Preferences createFrom(Map<String, Object> configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("Configuration can not be null");
		}
		Preferences prefs = new Preferences();
		Object incompleteClasspathSeverity = configuration.get(ERRORS_INCOMPLETE_CLASSPATH_SEVERITY_KEY);
		if (incompleteClasspathSeverity != null) {
			prefs.setIncompleteClasspathSeverity(Severity.fromString(incompleteClasspathSeverity.toString(), Severity.warning));
		}

		Object updateBuildConfiguration = configuration.get(CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY);
		if (updateBuildConfiguration != null) {
			prefs.setUpdateBuildConfigurationStatus(FeatureStatus.fromString(updateBuildConfiguration.toString(), FeatureStatus.interactive));
		}

		Object referenceCodelensEnabled = configuration.get(REFERENCES_CODE_LENS_ENABLED_KEY);
		if (referenceCodelensEnabled != null) {
			prefs.setReferencesCodelensEnabled(Boolean.valueOf(referenceCodelensEnabled.toString()));
		}


		return prefs;
	}

	private Preferences setReferencesCodelensEnabled(boolean enabled) {
		this.referencesCodeLensEnabled = enabled;
		return this;
	}

	private Preferences setUpdateBuildConfigurationStatus(FeatureStatus status) {
		this.updateBuildConfigurationStatus = status;
		return this;
	}

	private Preferences setIncompleteClasspathSeverity(Severity severity) {
		this.incompleteClasspathSeverity = severity;
		return this;
	}

	public Severity getIncompleteClasspathSeverity() {
		return incompleteClasspathSeverity;
	}

	public FeatureStatus getUpdateBuildConfigurationStatus() {
		return updateBuildConfigurationStatus;
	}

	public boolean isReferencesCodeLensEnabled() {
		return referencesCodeLensEnabled;
	}
}
