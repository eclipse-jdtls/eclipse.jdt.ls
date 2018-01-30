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

import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getBoolean;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getList;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.lsp4j.MessageType;

/**
 * Preferences model
 *
 * @author Fred Bricon
 *
 */
public class Preferences {

	public static final String COMMA = ",";
	/**
	 * Specifies the folder path to the JDK .
	 */
	public static final String JAVA_HOME = "java.home";
	/**
	 * Preference key to enable/disable gradle importer.
	 */
	public static final String IMPORT_GRADLE_ENABLED = "java.import.gradle.enabled";
	/**
	 * Preference key to enable/disable maven importer.
	 */
	public static final String IMPORT_MAVEN_ENABLED = "java.import.maven.enabled";
	/**
	 * Preference key to enable/disable reference code lenses.
	 */
	public static final String REFERENCES_CODE_LENS_ENABLED_KEY = "java.referencesCodeLens.enabled";

	/**
	 * Preference key to enable/disable implementation code lenses.
	 */
	public static final String IMPLEMENTATIONS_CODE_LENS_ENABLED_KEY = "java.implementationsCodeLens.enabled";

	/**
	 * Preference key to enable/disable formatter.
	 */
	public static final String JAVA_FORMAT_ENABLED_KEY = "java.format.enabled";

	/**
	 * Preference key to enable/disable organize imports on save
	 */
	public static final String JAVA_SAVE_ACTIONS_ORGANIZE_IMPORTS_KEY = "java.saveActions.organizeImports";

	/**
	 * Preference key to enable/disable signature help.
	 */
	public static final String SIGNATURE_HELP_ENABLED_KEY = "java.signatureHelp.enabled";

	/**
	 * Preference key to enable/disable rename.
	 */
	public static final String RENAME_ENABLED_KEY = "java.rename.enabled";

	/**
	 * Preference key to enable/disable executeCommand.
	 */
	public static final String EXECUTE_COMMAND_ENABLED_KEY = "java.executeCommand.enabled";

	/**
	 * Preference key to enable/disable the 'auto build'.
	 */
	public static final String AUTOBUILD_ENABLED_KEY = "java.autobuild.enabled";

	/**
	 * Preference key to exclude directories when importing projects.
	 */
	public static final String JAVA_IMPORT_EXCLUSIONS_KEY = "java.import.exclusions";
	public static final List<String> JAVA_IMPORT_EXCLUSIONS_DEFAULT;

	/**
	 * Preference key for project build/configuration update settings.
	 */
	public static final String CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY = "java.configuration.updateBuildConfiguration";

	/**
	 * Preference key for incomplete classpath severity messages.
	 */
	public static final String ERRORS_INCOMPLETE_CLASSPATH_SEVERITY_KEY = "java.errors.incompleteClasspath.severity";

	/**
	 * Preference key for Maven user settings.xml location.
	 */
	public static final String MAVEN_USER_SETTINGS_KEY = "java.configuration.maven.userSettings";

	/**
	 * A named preference that holds the favorite static members.
	 * <p>
	 * Value is of type <code>String</code>: list of favorites.
	 * </p>
	 */
	public static final String JAVA_COMPLETION_FAVORITE_MEMBERS_KEY = "java.completion.favoriteStaticMembers";
	public static final List<String> JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT;

	/**
	 * A named preference that defines how member elements are ordered by code
	 * actions.
	 * <p>
	 * Value is of type <code>String</code>: A comma separated list of the
	 * following entries. Each entry must be in the list, no duplication. List
	 * order defines the sort order.
	 * <ul>
	 * <li><b>T</b>: Types</li>
	 * <li><b>C</b>: Constructors</li>
	 * <li><b>I</b>: Initializers</li>
	 * <li><b>M</b>: Methods</li>
	 * <li><b>F</b>: Fields</li>
	 * <li><b>SI</b>: Static Initializers</li>
	 * <li><b>SM</b>: Static Methods</li>
	 * <li><b>SF</b>: Static Fields</li>
	 * </ul>
	 * </p>
	 */
	public static final String MEMBER_SORT_ORDER = "java.memberSortOrder"; //$NON-NLS-1$

	/**
	 * Preference key for the id(s) of the preferred content provider(s).
	 */
	public static final String PREFERRED_CONTENT_PROVIDER_KEY = "java.contentProvider.preferred";

	/**
	 * A named preference that holds a list of package names. The list specifies the
	 * import order used by the "Organize Imports" operation.
	 * <p>
	 * Value is of type <code>String</code>: list of package names
	 * </p>
	 */
	public static final String JAVA_IMPORT_ORDER_KEY = "java.completion.importOrder";
	public static final List<String> JAVA_IMPORT_ORDER_DEFAULT;

	public static final String TEXT_DOCUMENT_FORMATTING = "textDocument/formatting";
	public static final String TEXT_DOCUMENT_RANGE_FORMATTING = "textDocument/rangeFormatting";
	public static final String TEXT_DOCUMENT_CODE_LENS = "textDocument/codeLens";
	public static final String TEXT_DOCUMENT_SIGNATURE_HELP = "textDocument/signatureHelp";
	public static final String TEXT_DOCUMENT_RENAME = "textDocument/rename";
	public static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	public static final String WORKSPACE_SYMBOL = "workspace/symbol";
	public static final String DOCUMENT_SYMBOL = "textDocument/documentSymbol";
	public static final String CODE_ACTION = "textDocument/codeAction";
	public static final String DEFINITION = "textDocument/definition";
	public static final String HOVER = "textDocument/hover";
	public static final String REFERENCES = "textDocument/references";
	public static final String DOCUMENT_HIGHLIGHT = "textDocument/documentHighlight";

	public static final String FORMATTING_ID = UUID.randomUUID().toString();
	public static final String FORMATTING_RANGE_ID = UUID.randomUUID().toString();
	public static final String CODE_LENS_ID = UUID.randomUUID().toString();
	public static final String SIGNATURE_HELP_ID = UUID.randomUUID().toString();
	public static final String RENAME_ID = UUID.randomUUID().toString();
	public static final String EXECUTE_COMMAND_ID = UUID.randomUUID().toString();
	public static final String WORKSPACE_SYMBOL_ID = UUID.randomUUID().toString();
	public static final String DOCUMENT_SYMBOL_ID = UUID.randomUUID().toString();
	public static final String CODE_ACTION_ID = UUID.randomUUID().toString();
	public static final String DEFINITION_ID = UUID.randomUUID().toString();
	public static final String HOVER_ID = UUID.randomUUID().toString();
	public static final String REFERENCES_ID = UUID.randomUUID().toString();
	public static final String DOCUMENT_HIGHLIGHT_ID = UUID.randomUUID().toString();

	private Map<String, Object> configuration;
	private Severity incompleteClasspathSeverity;
	private FeatureStatus updateBuildConfigurationStatus;
	private boolean referencesCodeLensEnabled;
	private boolean importGradleEnabled;
	private boolean importMavenEnabled;
	private boolean implementationsCodeLensEnabled;
	private boolean javaFormatEnabled;
	private boolean javaSaveActionsOrganizeImportsEnabled;
	private boolean signatureHelpEnabled;
	private boolean renameEnabled;
	private boolean executeCommandEnabled;
	private boolean autobuildEnabled;
	private MemberSortOrder memberOrders;
	private List<String> preferredContentProviderIds;

	private String mavenUserSettings;

	private List<String> javaCompletionFavoriteMembers;

	private List<String> javaImportExclusions = new ArrayList<>();
	private String javaHome;
	private List<String> importOrder;

	static {
		JAVA_IMPORT_EXCLUSIONS_DEFAULT = new ArrayList<>();
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/node_modules");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/.metadata");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/archetype-resources");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT = new ArrayList<>();
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.Assert.*:");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.Assume.*:");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.Assertions.*:");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.Assumptions.*:");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.DynamicContainer.*:");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.DynamicTest.*");
		JAVA_IMPORT_ORDER_DEFAULT = new ArrayList<>();
		JAVA_IMPORT_ORDER_DEFAULT.add("java");
		JAVA_IMPORT_ORDER_DEFAULT.add("javax");
		JAVA_IMPORT_ORDER_DEFAULT.add("com");
		JAVA_IMPORT_ORDER_DEFAULT.add("org");
	}

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
		configuration = null;
		incompleteClasspathSeverity = Severity.warning;
		updateBuildConfigurationStatus = FeatureStatus.interactive;
		importGradleEnabled = true;
		importMavenEnabled = true;
		referencesCodeLensEnabled = true;
		implementationsCodeLensEnabled = false;
		javaFormatEnabled = true;
		javaSaveActionsOrganizeImportsEnabled = false;
		signatureHelpEnabled = false;
		renameEnabled = true;
		executeCommandEnabled = true;
		autobuildEnabled = true;
		memberOrders = new MemberSortOrder(null);
		preferredContentProviderIds = null;
		javaImportExclusions = JAVA_IMPORT_EXCLUSIONS_DEFAULT;
		javaCompletionFavoriteMembers = JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT;
		javaHome = null;
		importOrder = JAVA_IMPORT_ORDER_DEFAULT;
	}

	/**
	 * Create a {@link Preferences} model from a {@link Map} configuration.
	 */
	public static Preferences createFrom(Map<String, Object> configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("Configuration can not be null");
		}
		Preferences prefs = new Preferences();
		prefs.configuration = configuration;

		String incompleteClasspathSeverity = getString(configuration, ERRORS_INCOMPLETE_CLASSPATH_SEVERITY_KEY, null);
		prefs.setIncompleteClasspathSeverity(Severity.fromString(incompleteClasspathSeverity, Severity.warning));

		String updateBuildConfiguration = getString(configuration, CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY, null);
		prefs.setUpdateBuildConfigurationStatus(
				FeatureStatus.fromString(updateBuildConfiguration, FeatureStatus.interactive));

		boolean importGradleEnabled = getBoolean(configuration, IMPORT_GRADLE_ENABLED, true);
		prefs.setImportGradleEnabled(importGradleEnabled);
		boolean importMavenEnabled = getBoolean(configuration, IMPORT_MAVEN_ENABLED, true);
		prefs.setImportMavenEnabled(importMavenEnabled);
		boolean referenceCodelensEnabled = getBoolean(configuration, REFERENCES_CODE_LENS_ENABLED_KEY, true);
		prefs.setReferencesCodelensEnabled(referenceCodelensEnabled);
		boolean implementationCodeLensEnabled = getBoolean(configuration, IMPLEMENTATIONS_CODE_LENS_ENABLED_KEY, false);
		prefs.setImplementationCodelensEnabled(implementationCodeLensEnabled);

		boolean javaFormatEnabled = getBoolean(configuration, JAVA_FORMAT_ENABLED_KEY, true);
		prefs.setJavaFormatEnabled(javaFormatEnabled);

		boolean javaSaveActionAutoOrganizeImportsEnabled = getBoolean(configuration, JAVA_SAVE_ACTIONS_ORGANIZE_IMPORTS_KEY, false);
		prefs.setJavaSaveActionAutoOrganizeImportsEnabled(javaSaveActionAutoOrganizeImportsEnabled);

		boolean signatureHelpEnabled = getBoolean(configuration, SIGNATURE_HELP_ENABLED_KEY, true);
		prefs.setSignatureHelpEnabled(signatureHelpEnabled);

		boolean renameEnabled = getBoolean(configuration, RENAME_ENABLED_KEY, true);
		prefs.setRenameEnabled(renameEnabled);

		boolean executeCommandEnable = getBoolean(configuration, EXECUTE_COMMAND_ENABLED_KEY, true);
		prefs.setExecuteCommandEnabled(executeCommandEnable);

		boolean autobuildEnable = getBoolean(configuration, AUTOBUILD_ENABLED_KEY, true);
		prefs.setAutobuildEnabled(autobuildEnable);

		List<String> javaImportExclusions = getList(configuration, JAVA_IMPORT_EXCLUSIONS_KEY, JAVA_IMPORT_EXCLUSIONS_DEFAULT);
		prefs.setJavaImportExclusions(javaImportExclusions);

		List<String> javaCompletionFavoriteMembers = getList(configuration, JAVA_COMPLETION_FAVORITE_MEMBERS_KEY, JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT);
		prefs.setJavaCompletionFavoriteMembers(javaCompletionFavoriteMembers);

		String mavenUserSettings = getString(configuration, MAVEN_USER_SETTINGS_KEY, null);
		prefs.setMavenUserSettings(mavenUserSettings);

		String sortOrder = getString(configuration, MEMBER_SORT_ORDER, null);
		prefs.setMembersSortOrder(sortOrder);

		List<String> preferredContentProviders = getList(configuration, PREFERRED_CONTENT_PROVIDER_KEY);
		prefs.setPreferredContentProviderIds(preferredContentProviders);

		String javaHome = getString(configuration, JAVA_HOME);
		prefs.setJavaHome(javaHome);

		List<String> javaImportOrder = getList(configuration, JAVA_IMPORT_ORDER_KEY, JAVA_IMPORT_ORDER_DEFAULT);
		prefs.setImportOrder(javaImportOrder);
		return prefs;
	}

	public Preferences setJavaHome(String javaHome) {
		this.javaHome = javaHome;
		return this;
	}

	public Preferences setJavaImportExclusions(List<String> javaImportExclusions) {
		this.javaImportExclusions = javaImportExclusions;
		return this;
	}

	public Preferences setJavaCompletionFavoriteMembers(List<String> javaCompletionFavoriteMembers) {
		this.javaCompletionFavoriteMembers = (javaCompletionFavoriteMembers == null || javaCompletionFavoriteMembers.isEmpty()) ? JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT : javaCompletionFavoriteMembers;
		return this;
	}

	private Preferences setMembersSortOrder(String sortOrder) {
		this.memberOrders = new MemberSortOrder(sortOrder);
		return this;
	}

	private Preferences setPreferredContentProviderIds(List<String> preferredContentProviderIds) {
		this.preferredContentProviderIds = preferredContentProviderIds;
		return this;
	}

	private Preferences setReferencesCodelensEnabled(boolean enabled) {
		this.referencesCodeLensEnabled = enabled;
		return this;
	}

	public Preferences setImportGradleEnabled(boolean enabled) {
		this.importGradleEnabled = enabled;
		return this;
	}

	public Preferences setImportMavenEnabled(boolean enabled) {
		this.importMavenEnabled = enabled;
		return this;
	}

	private Preferences setSignatureHelpEnabled(boolean enabled) {
		this.signatureHelpEnabled = enabled;
		return this;
	}

	private Preferences setImplementationCodelensEnabled(boolean enabled) {
		this.implementationsCodeLensEnabled = enabled;
		return this;
	}

	private Preferences setRenameEnabled(boolean enabled) {
		this.renameEnabled = enabled;
		return this;
	}

	private Preferences setExecuteCommandEnabled(boolean enabled) {
		this.executeCommandEnabled = enabled;
		return this;
	}

	public Preferences setAutobuildEnabled(boolean enabled) {
		this.autobuildEnabled = enabled;
		return this;
	}

	public Preferences setJavaFormatEnabled(boolean enabled) {
		this.javaFormatEnabled = enabled;
		return this;
	}

	public Preferences setJavaSaveActionAutoOrganizeImportsEnabled(boolean javaSaveActionAutoOrganizeImportsEnabled) {
		this.javaSaveActionsOrganizeImportsEnabled = javaSaveActionAutoOrganizeImportsEnabled;
		return this;
	}

	public Preferences setUpdateBuildConfigurationStatus(FeatureStatus status) {
		this.updateBuildConfigurationStatus = status;
		return this;
	}

	private Preferences setIncompleteClasspathSeverity(Severity severity) {
		this.incompleteClasspathSeverity = severity;
		return this;
	}

	public Preferences setImportOrder(List<String> importOrder) {
		this.importOrder = (importOrder == null || importOrder.size() == 0) ? JAVA_IMPORT_ORDER_DEFAULT : importOrder;
		return this;
	}

	public Severity getIncompleteClasspathSeverity() {
		return incompleteClasspathSeverity;
	}

	public FeatureStatus getUpdateBuildConfigurationStatus() {
		return updateBuildConfigurationStatus;
	}

	public List<String> getJavaImportExclusions() {
		return javaImportExclusions;
	}

	public String[] getJavaCompletionFavoriteMembers() {
		return javaCompletionFavoriteMembers.toArray(new String[0]);
	}

	public String getJavaHome() {
		return javaHome;
	}

	public MemberSortOrder getMemberSortOrder() {
		return this.memberOrders;
	}

	public List<String> getPreferredContentProviderIds() {
		return this.preferredContentProviderIds;
	}

	public boolean isReferencesCodeLensEnabled() {
		return referencesCodeLensEnabled;
	}

	public boolean isImportGradleEnabled() {
		return importGradleEnabled;
	}

	public boolean isImportMavenEnabled() {
		return importMavenEnabled;
	}

	public boolean isImplementationsCodeLensEnabled() {
		return implementationsCodeLensEnabled;
	}

	public boolean isCodeLensEnabled() {
		return referencesCodeLensEnabled || implementationsCodeLensEnabled;
	}

	public boolean isJavaFormatEnabled() {
		return javaFormatEnabled;
	}

	public boolean isJavaSaveActionsOrganizeImportsEnabled() {
		return javaSaveActionsOrganizeImportsEnabled;
	}

	public boolean isSignatureHelpEnabled() {
		return signatureHelpEnabled;
	}

	public boolean isRenameEnabled() {
		return renameEnabled;
	}

	public boolean isExecuteCommandEnabled() {
		return executeCommandEnabled;
	}

	public boolean isAutobuildEnabled() {
		return autobuildEnabled;
	}

	public Preferences setMavenUserSettings(String mavenUserSettings) {
		this.mavenUserSettings = mavenUserSettings;
		return this;
	}

	public String getMavenUserSettings() {
		return mavenUserSettings;
	}

	public String[] getImportOrder() {
		return this.importOrder == null ? new String[0] : this.importOrder.toArray(new String[importOrder.size()]);
	}

	public Map<String, Object> asMap() {
		if (configuration == null) {
			return null;
		}
		return Collections.unmodifiableMap(configuration);
	}
}
