/*******************************************************************************
 * Copyright (c) 2016-2018 Red Hat Inc. and others.
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
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getInt;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getList;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.core.internal.resources.PreferenceInitializer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.internal.core.manipulation.MembersOrderPreferenceCacheCommon;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.lsp4j.MessageType;

/**
 * Preferences model
 *
 * @author Fred Bricon
 *
 */
public class Preferences {

	/**
	 * Specifies the folder path to the JDK .
	 */
	public static final String JAVA_HOME = "java.home";
	/**
	 * Specifies the file path to the formatter xml url.
	 */
	public static final String JAVA_FORMATTER_URL = "java.format.settings.url";
	/**
	 * Specifies the formatter profile name.
	 */
	public static final String JAVA_FORMATTER_PROFILE_NAME = "java.format.settings.profile";
	/**
	 * Preference key used to include the comments during the formatting.
	 */
	public static final String JAVA_FORMAT_COMMENTS = "java.format.comments.enabled";
	/**
	 * Preference key to enable/disable gradle importer.
	 */
	public static final String IMPORT_GRADLE_ENABLED = "java.import.gradle.enabled";
	/**
	 * Preference key to enable/disable maven importer.
	 */
	public static final String IMPORT_MAVEN_ENABLED = "java.import.maven.enabled";
	/**
	 * Preference key to enable/disable downloading Maven source artifacts.
	 */
	public static final String MAVEN_DOWNLOAD_SOURCES = "java.maven.downloadSources";
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
	 * Preference key to enable/disable formatter on-type.
	 */
	public static final String JAVA_FORMAT_ON_TYPE_ENABLED_KEY = "java.format.onType.enabled";

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
	 * Preference key to set max concurrent build count.
	 */
	public static final String JAVA_MAX_CONCURRENT_BUILDS = "java.maxConcurrentBuilds";

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
	 * Preference key to enable/disable the 'completion'.
	 */
	public static final String COMPLETION_ENABLED_KEY = "java.completion.enabled";

	/**
	 * Preference key to enable/disable the 'foldingRange'.
	 */
	public static final String FOLDINGRANGE_ENABLED_KEY = "java.foldingRange.enabled";

	/**
	 * A named preference that holds the favorite static members.
	 * <p>
	 * Value is of type <code>String</code>: list of favorites.
	 * </p>
	 */
	public static final String JAVA_COMPLETION_FAVORITE_MEMBERS_KEY = "java.completion.favoriteStaticMembers";
	public static final List<String> JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT;

	/**
	 * A named preference that controls if the Java code assist only inserts
	 * completions. When set to true, code completion overwrites the current text.
	 * When set to false, code is simply added instead.
	 * <p>
	 * Value is of type <code>Boolean</code>.
	 * </p>
	 */
	public static final String JAVA_COMPLETION_OVERWRITE_KEY = "java.completion.overwrite";
	/**
	 * A named preference that controls if method arguments are guessed when a
	 * method is selected from as list of code assist proposal.
	 * <p>
	 * Value is of type <code>Boolean</code>.
	 * </p>
	 */
	public static final String JAVA_COMPLETION_GUESS_METHOD_ARGUMENTS_KEY = "java.completion.guessMethodArguments";

	/**
	 * A named preference that defines how member elements are ordered by code
	 * actions.
	 * <p>
	 * Value is of type <code>String</code>: A comma separated list of the following
	 * entries. Each entry must be in the list, no duplication. List order defines
	 * the sort order.
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

	// A named preference that defines whether to use Objects.hash and Objects.equals methods when generating the hashCode and equals methods.
	public static final String JAVA_CODEGENERATION_HASHCODEEQUALS_USEJAVA7OBJECTS = "java.codeGeneration.hashCodeEquals.useJava7Objects";
	// A named preference that defines whether to use 'instanceof' to compare types when generating the hashCode and equals methods.
	public static final String JAVA_CODEGENERATION_HASHCODEEQUALS_USEINSTANCEOF = "java.codeGeneration.hashCodeEquals.useInstanceof";
	// A named preference that defines whether to use blocks in 'if' statements when generating the hashCode and equals methods.
	public static final String JAVA_CODEGENERATION_HASHCODEEQUALS_USEBLOCKS = "java.codeGeneration.hashCodeEquals.useBlocks";
	// A named preference that defines whether to generate method comments when generating the hashCode and equals methods.
	public static final String JAVA_CODEGENERATION_HASHCODEEQUALS_GENERATECOMMENTS = "java.codeGeneration.hashCodeEquals.generateComments";

	public static final String TEXT_DOCUMENT_FORMATTING = "textDocument/formatting";
	public static final String TEXT_DOCUMENT_RANGE_FORMATTING = "textDocument/rangeFormatting";
	public static final String TEXT_DOCUMENT_ON_TYPE_FORMATTING = "textDocument/onTypeFormatting";
	public static final String TEXT_DOCUMENT_CODE_LENS = "textDocument/codeLens";
	public static final String TEXT_DOCUMENT_SIGNATURE_HELP = "textDocument/signatureHelp";
	public static final String TEXT_DOCUMENT_RENAME = "textDocument/rename";
	public static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	public static final String WORKSPACE_SYMBOL = "workspace/symbol";
	public static final String WORKSPACE_WATCHED_FILES = "workspace/didChangeWatchedFiles";
	public static final String DOCUMENT_SYMBOL = "textDocument/documentSymbol";
	public static final String COMPLETION = "textDocument/completion";
	public static final String CODE_ACTION = "textDocument/codeAction";
	public static final String DEFINITION = "textDocument/definition";
	public static final String TYPEDEFINITION = "textDocument/typeDefinition";
	public static final String HOVER = "textDocument/hover";
	public static final String REFERENCES = "textDocument/references";
	public static final String DOCUMENT_HIGHLIGHT = "textDocument/documentHighlight";
	public static final String FOLDINGRANGE = "textDocument/foldingRange";
	public static final String WORKSPACE_CHANGE_FOLDERS = "workspace/didChangeWorkspaceFolders";
	public static final String IMPLEMENTATION = "textDocument/implementation";

	public static final String FORMATTING_ID = UUID.randomUUID().toString();
	public static final String FORMATTING_ON_TYPE_ID = UUID.randomUUID().toString();
	public static final String FORMATTING_RANGE_ID = UUID.randomUUID().toString();
	public static final String CODE_LENS_ID = UUID.randomUUID().toString();
	public static final String SIGNATURE_HELP_ID = UUID.randomUUID().toString();
	public static final String RENAME_ID = UUID.randomUUID().toString();
	public static final String EXECUTE_COMMAND_ID = UUID.randomUUID().toString();
	public static final String WORKSPACE_SYMBOL_ID = UUID.randomUUID().toString();
	public static final String DOCUMENT_SYMBOL_ID = UUID.randomUUID().toString();
	public static final String COMPLETION_ID = UUID.randomUUID().toString();
	public static final String CODE_ACTION_ID = UUID.randomUUID().toString();
	public static final String DEFINITION_ID = UUID.randomUUID().toString();
	public static final String TYPEDEFINITION_ID = UUID.randomUUID().toString();
	public static final String HOVER_ID = UUID.randomUUID().toString();
	public static final String REFERENCES_ID = UUID.randomUUID().toString();
	public static final String DOCUMENT_HIGHLIGHT_ID = UUID.randomUUID().toString();
	public static final String FOLDINGRANGE_ID = UUID.randomUUID().toString();
	public static final String WORKSPACE_CHANGE_FOLDERS_ID = UUID.randomUUID().toString();
	public static final String WORKSPACE_WATCHED_FILES_ID = UUID.randomUUID().toString();
	public static final String IMPLEMENTATION_ID = UUID.randomUUID().toString();

	private Map<String, Object> configuration;
	private Severity incompleteClasspathSeverity;
	private FeatureStatus updateBuildConfigurationStatus;
	private boolean referencesCodeLensEnabled;
	private boolean importGradleEnabled;
	private boolean importMavenEnabled;
	private boolean mavenDownloadSources;
	private boolean implementationsCodeLensEnabled;
	private boolean javaFormatEnabled;
	private boolean javaFormatOnTypeEnabled;
	private boolean javaSaveActionsOrganizeImportsEnabled;
	private boolean signatureHelpEnabled;
	private boolean renameEnabled;
	private boolean executeCommandEnabled;
	private boolean autobuildEnabled;
	private boolean completionEnabled;
	private boolean completionOverwrite;
	private boolean foldingRangeEnabled;
	private boolean guessMethodArguments;
	private boolean javaFormatComments;
	private boolean hashCodeEqualsTemplateUseJava7Objects;
	private boolean hashCodeEqualsTemplateUseInstanceof;
	private boolean hashCodeEqualsTemplateUseBlocks;
	private boolean hashCodeEqualsTemplateGenerateComments;
	private List<String> preferredContentProviderIds;

	private String mavenUserSettings;

	private List<String> javaCompletionFavoriteMembers;

	private List<String> javaImportExclusions = new ArrayList<>();
	private String javaHome;
	private List<String> importOrder;
	private String formatterUrl;
	private String formatterProfileName;
	private Collection<IPath> rootPaths;
	private Collection<IPath> triggerFiles;

	private int parallelBuildsCount;

	static {
		JAVA_IMPORT_EXCLUSIONS_DEFAULT = new ArrayList<>();
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/node_modules/**");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/.metadata/**");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/archetype-resources/**");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/META-INF/maven/**");
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
		mavenDownloadSources = false;
		referencesCodeLensEnabled = true;
		implementationsCodeLensEnabled = false;
		javaFormatEnabled = true;
		javaFormatOnTypeEnabled = false;
		javaSaveActionsOrganizeImportsEnabled = false;
		signatureHelpEnabled = false;
		renameEnabled = true;
		executeCommandEnabled = true;
		autobuildEnabled = true;
		completionEnabled = true;
		completionOverwrite = true;
		foldingRangeEnabled = true;
		guessMethodArguments = false;
		javaFormatComments = true;
		hashCodeEqualsTemplateUseJava7Objects = false;
		hashCodeEqualsTemplateUseInstanceof = false;
		hashCodeEqualsTemplateUseBlocks = false;
		hashCodeEqualsTemplateGenerateComments = false;
		preferredContentProviderIds = null;
		javaImportExclusions = JAVA_IMPORT_EXCLUSIONS_DEFAULT;
		javaCompletionFavoriteMembers = JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT;
		javaHome = null;
		formatterUrl = null;
		formatterProfileName = null;
		importOrder = JAVA_IMPORT_ORDER_DEFAULT;
		parallelBuildsCount = PreferenceInitializer.PREF_MAX_CONCURRENT_BUILDS_DEFAULT;
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
		boolean downloadSources = getBoolean(configuration, MAVEN_DOWNLOAD_SOURCES, false);
		prefs.setMavenDownloadSources(downloadSources);
		boolean referenceCodelensEnabled = getBoolean(configuration, REFERENCES_CODE_LENS_ENABLED_KEY, true);
		prefs.setReferencesCodelensEnabled(referenceCodelensEnabled);
		boolean implementationCodeLensEnabled = getBoolean(configuration, IMPLEMENTATIONS_CODE_LENS_ENABLED_KEY, false);
		prefs.setImplementationCodelensEnabled(implementationCodeLensEnabled);

		boolean javaFormatEnabled = getBoolean(configuration, JAVA_FORMAT_ENABLED_KEY, true);
		prefs.setJavaFormatEnabled(javaFormatEnabled);

		boolean javaFormatOnTypeEnabled = getBoolean(configuration, JAVA_FORMAT_ON_TYPE_ENABLED_KEY, false);
		prefs.setJavaFormatOnTypeEnabled(javaFormatOnTypeEnabled);

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

		boolean completionEnable = getBoolean(configuration, COMPLETION_ENABLED_KEY, true);
		prefs.setCompletionEnabled(completionEnable);
		boolean completionOverwrite = getBoolean(configuration, JAVA_COMPLETION_OVERWRITE_KEY, true);
		prefs.setCompletionOverwrite(completionOverwrite);

		boolean foldingRangeEnable = getBoolean(configuration, FOLDINGRANGE_ENABLED_KEY, true);
		prefs.setFoldingRangeEnabled(foldingRangeEnable);

		boolean guessMethodArguments = getBoolean(configuration, JAVA_COMPLETION_GUESS_METHOD_ARGUMENTS_KEY, false);
		prefs.setGuessMethodArguments(guessMethodArguments);

		boolean hashCodeEqualsTemplateUseJava7Objects = getBoolean(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEJAVA7OBJECTS, false);
		prefs.setHashCodeEqualsTemplateUseJava7Objects(hashCodeEqualsTemplateUseJava7Objects);
		boolean hashCodeEqualsTemplateUseInstanceof = getBoolean(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEINSTANCEOF, false);
		prefs.setHashCodeEqualsTemplateUseInstanceof(hashCodeEqualsTemplateUseInstanceof);
		boolean hashCodeEqualsTemplateUseBlocks = getBoolean(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEBLOCKS, false);
		prefs.setHashCodeEqualsTemplateUseBlocks(hashCodeEqualsTemplateUseBlocks);
		boolean hashCodeEqualsTemplateGenerateComments = getBoolean(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_GENERATECOMMENTS, false);
		prefs.setHashCodeEqualsTemplateGenerateComments(hashCodeEqualsTemplateGenerateComments);

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

		String formatterUrl = getString(configuration, JAVA_FORMATTER_URL);
		prefs.setFormatterUrl(formatterUrl);

		String formatterProfileName = getString(configuration, JAVA_FORMATTER_PROFILE_NAME);
		prefs.setFormatterProfileName(formatterProfileName);

		boolean javaFormatComments = getBoolean(configuration, JAVA_FORMAT_COMMENTS, true);
		prefs.setJavaFormatComments(javaFormatComments);

		List<String> javaImportOrder = getList(configuration, JAVA_IMPORT_ORDER_KEY, JAVA_IMPORT_ORDER_DEFAULT);
		prefs.setImportOrder(javaImportOrder);

		int maxConcurrentBuilds = getInt(configuration, JAVA_MAX_CONCURRENT_BUILDS, PreferenceInitializer.PREF_MAX_CONCURRENT_BUILDS_DEFAULT);
		maxConcurrentBuilds = maxConcurrentBuilds >= 1 ? maxConcurrentBuilds : 1;
		prefs.setMaxBuildCount(maxConcurrentBuilds);

		return prefs;
	}

	public Preferences setJavaHome(String javaHome) {
		this.javaHome = javaHome;
		return this;
	}

	public Preferences setFormatterUrl(String formatterUrl) {
		this.formatterUrl = formatterUrl;
		return this;
	}

	public Preferences setJavaFormatComments(boolean javaFormatComments) {
		this.javaFormatComments = javaFormatComments;
		return this;
	}

	public boolean isJavaFormatComments() {
		return this.javaFormatComments;
	}

	public Preferences setFormatterProfileName(String formatterProfileName) {
		this.formatterProfileName = formatterProfileName;
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
		if (sortOrder != null) {
			IEclipsePreferences fPreferenceStore = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
			fPreferenceStore.put(MembersOrderPreferenceCacheCommon.APPEARANCE_MEMBER_SORT_ORDER, sortOrder);
		}
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

	public Preferences setMavenDownloadSources(boolean enabled) {
		this.mavenDownloadSources = enabled;
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

	public Preferences setCompletionEnabled(boolean enabled) {
		this.completionEnabled = enabled;
		return this;
	}

	public Preferences setCompletionOverwrite(boolean completionOverwrite) {
		this.completionOverwrite = completionOverwrite;
		return this;
	}

	public Preferences setFoldingRangeEnabled(boolean enabled) {
		this.foldingRangeEnabled = enabled;
		return this;
	}

	public Preferences setGuessMethodArguments(boolean guessMethodArguments) {
		this.guessMethodArguments = guessMethodArguments;
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

	public Preferences setHashCodeEqualsTemplateUseJava7Objects(boolean hashCodeEqualsTemplateUseJ7Objects) {
		this.hashCodeEqualsTemplateUseJava7Objects = hashCodeEqualsTemplateUseJ7Objects;
		return this;
	}

	public Preferences setHashCodeEqualsTemplateUseInstanceof(boolean hashCodeEqualsTemplateUseInstanceof) {
		this.hashCodeEqualsTemplateUseInstanceof = hashCodeEqualsTemplateUseInstanceof;
		return this;
	}

	public Preferences setHashCodeEqualsTemplateUseBlocks(boolean hashCodeEqualsTemplateUseBlocks) {
		this.hashCodeEqualsTemplateUseBlocks = hashCodeEqualsTemplateUseBlocks;
		return this;
	}

	public Preferences setHashCodeEqualsTemplateGenerateComments(boolean hashCodeEqualsTemplateGenerateComments) {
		this.hashCodeEqualsTemplateGenerateComments = hashCodeEqualsTemplateGenerateComments;
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
		IEclipsePreferences pref = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		pref.put(CodeStyleConfiguration.ORGIMPORTS_IMPORTORDER, String.join(";", importOrder));
		return this;
	}

	public Preferences setMaxBuildCount(int maxConcurrentBuilds) {
		this.parallelBuildsCount = maxConcurrentBuilds;
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

	public String getFormatterUrl() {
		return formatterUrl;
	}

	public String getFormatterProfileName() {
		return formatterProfileName;
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

	public boolean isMavenDownloadSources() {
		return mavenDownloadSources;
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

	public boolean isCompletionEnabled() {
		return completionEnabled;
	}

	public boolean isCompletionOverwrite() {
		return completionOverwrite;
	}

	public boolean isFoldingRangeEnabled() {
		return foldingRangeEnabled;
	}

	public boolean isGuessMethodArguments() {
		return guessMethodArguments;
	}

	public boolean isHashCodeEqualsTemplateUseJava7Objects() {
		return hashCodeEqualsTemplateUseJava7Objects;
	}

	public boolean isHashCodeEqualsTemplateUseInstanceof() {
		return hashCodeEqualsTemplateUseInstanceof;
	}

	public boolean isHashCodeEqualsTemplateUseBlocks() {
		return hashCodeEqualsTemplateUseBlocks;
	}

	public boolean isHashCodeEqualsTemplateGenerateComments() {
		return hashCodeEqualsTemplateGenerateComments;
	}

	public Preferences setMavenUserSettings(String mavenUserSettings) {
		this.mavenUserSettings = ResourceUtils.expandPath(mavenUserSettings);
		return this;
	}

	public String getMavenUserSettings() {
		return mavenUserSettings;
	}

	public String[] getImportOrder() {
		return this.importOrder == null ? new String[0] : this.importOrder.toArray(new String[importOrder.size()]);
	}

	public int getMaxConcurrentBuilds() {
		return parallelBuildsCount;
	}

	public Map<String, Object> asMap() {
		if (configuration == null) {
			return null;
		}
		return Collections.unmodifiableMap(configuration);
	}

	public Preferences setRootPaths(Collection<IPath> rootPaths) {
		this.rootPaths = rootPaths;
		return this;
	}

	public Collection<IPath> getRootPaths() {
		return rootPaths;
	}

	public Preferences setTriggerFiles(Collection<IPath> triggerFiles) {
		this.triggerFiles = triggerFiles;
		return this;
	}

	public Collection<IPath> getTriggerFiles() {
		return triggerFiles;
	}

	public boolean isJavaFormatOnTypeEnabled() {
		return javaFormatOnTypeEnabled;
	}

	public void setJavaFormatOnTypeEnabled(boolean javaFormatOnTypeEnabled) {
		this.javaFormatOnTypeEnabled = javaFormatOnTypeEnabled;
	}
}
