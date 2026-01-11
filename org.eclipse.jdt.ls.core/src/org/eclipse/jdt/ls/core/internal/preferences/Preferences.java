/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getBoolean;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getInt;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getList;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getString;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getValue;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.internal.resources.PreferenceInitializer;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.core.manipulation.MembersOrderPreferenceCacheCommon;
import org.eclipse.jdt.internal.ui.PreferenceConstantsCore;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.RuntimeEnvironment;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathResult;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionGuessMethodArgumentsMode;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionMatchCaseMode;
import org.eclipse.jdt.ls.core.internal.handlers.InlayHintsParameterMode;
import org.eclipse.jdt.ls.core.internal.handlers.ProjectEncodingMode;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.MessageType;
import org.osgi.framework.Bundle;

/**
 * Preferences model
 *
 * @author Fred Bricon
 *
 */
public class Preferences {

	private static final String IGNORE = "ignore";
	public static final String LINE = "line";
	/**
	 * Specifies the folder path to the JDK .
	 */
	public static final String JAVA_HOME = "java.home";
	/**
	 * Preference key used to controls the "smart semicolon" detection
	 */
	public static final String JAVA_EDIT_SMARTSEMICOLON_DETECTION = "java.edit.smartSemicolonDetection.enabled";

	/**
	 * Preference key used to include getter, setter and builder/constructor when
	 * finding references.
	 */
	public static final String JAVA_REFERENCES_INCLUDE_ACCESSORS = "java.references.includeAccessors";

	/**
	 * Preference key used to include the decompiled sources when finding
	 * references.
	 */
	public static final String JAVA_REFERENCES_INCLUDE_DECOMPILED_SOURCES = "java.references.includeDecompiledSources";

	/**
	 * Include method declarations from source files in symbol search.
	 */
	public static final String JAVA_SYMBOLS_INCLUDE_SOURCE_METHOD_DECLARATIONS = "java.symbols.includeSourceMethodDeclarations";

	/**
	 * Insert spaces when pressing Tab
	 */
	public static final String JAVA_CONFIGURATION_INSERTSPACES = "java.format.insertSpaces";
	/**
	 * Tab Size
	 */
	public static final String JAVA_CONFIGURATION_TABSIZE = "java.format.tabSize";

	/**
	 * Files associations to languages
	 */
	public static final String JAVA_CONFIGURATION_ASSOCIATIONS = "java.associations";
	/**
	 * Specifies Java Execution Environments.
	 */
	public static final String JAVA_CONFIGURATION_RUNTIMES = "java.configuration.runtimes";
	public static final List<String> JAVA_CONFIGURATION_RUNTIMES_DEFAULT;
	/**
	 * Specifies the file path or url to the formatter xml url.
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
	 * Specifies the file path or url to the Java setting.
	 */
	public static final String JAVA_SETTINGS_URL = "java.settings.url";

	/**
	 * Specifies filter applied on projects to exclude some file system objects
	 * while populating the resources tree.
	 */
	public static final String JAVA_RESOURCE_FILTERS = "java.project.resourceFilters";
	public static final List<String> JAVA_RESOURCE_FILTERS_DEFAULT;
	/**
	 * Preference key for Show quickfixes at the problem or line level.
	 */
	public static final String QUICK_FIX_SHOW_AT = "java.quickfix.showAt";

	/**
	 * Preference key to enable/disable gradle importer.
	 */
	public static final String IMPORT_GRADLE_ENABLED = "java.import.gradle.enabled";
	/**
	 * Preference key to enable/disable gradle offline mode.
	 */
	public static final String IMPORT_GRADLE_OFFLINE_ENABLED = "java.import.gradle.offline.enabled";
	/**
	 * Preference key to enable/disable gradle wrapper.
	 */
	public static final String GRADLE_WRAPPER_ENABLED = "java.import.gradle.wrapper.enabled";
	/**
	 * Preference key for gradle version to use when the gradle wrapper is not used.
	 */
	public static final String GRADLE_VERSION = "java.import.gradle.version";
	/**
	 * Preference key for arguments to pass to Gradle
	 */
	public static final String GRADLE_ARGUMENTS = "java.import.gradle.arguments";
	/**
	 * Preference key for JVM arguments to pass to Gradle
	 */
	public static final String GRADLE_JVM_ARGUMENTS = "java.import.gradle.jvmArguments";
	/**
	 * Preference key for setting GRADLE_HOME.
	 */
	public static final String GRADLE_HOME = "java.import.gradle.home";
	/**
	 * Preference key for the JVM used to run the Gradle daemon..
	 */
	public static final String GRADLE_JAVA_HOME = "java.import.gradle.java.home";
	/**
	 * Preference key for setting GRADLE_USER_HOME.
	 */
	public static final String GRADLE_USER_HOME = "java.import.gradle.user.home";
	/**
	 * Preference key to enable/disable Gradle Annotation Processing.
	 */
	public static final String GRADLE_ANNOTATION_PROCESSING_ENABLED = "java.import.gradle.annotationProcessing.enabled";
	/**
	 * Preference key to enable/disable maven importer.
	 */
	public static final String IMPORT_MAVEN_ENABLED = "java.import.maven.enabled";
	/**
	 * Preference key to enable/disable maven offline mode.
	 */
	public static final String IMPORT_MAVEN_OFFLINE = "java.import.maven.offline.enabled";

	/**
	 * Preference key to enable/disable maven test classpath flag.
	 */
	public static final String MAVEN_DISABLE_TEST_CLASSPATH_FLAG = "java.import.maven.disableTestClasspathFlag";
	/**
	 * Preference key to enable/disable downloading Maven source artifacts.
	 */
	public static final String MAVEN_DOWNLOAD_SOURCES = "java.maven.downloadSources";
	/**
	 * Preference key to enable/disable downloading source artifacts for Eclipse
	 * projects.
	 */
	public static final String ECLIPSE_DOWNLOAD_SOURCES = "java.eclipse.downloadSources";
	/**
	 * Preference key to force update of Snapshots/Releases.
	 */
	public static final String MAVEN_UPDATE_SNAPSHOTS = "java.maven.updateSnapshots";
	/**
	 * Preference key to enable/disable reference code lenses.
	 */
	public static final String REFERENCES_CODE_LENS_ENABLED_KEY = "java.referencesCodeLens.enabled";

	/**
	 * Preference key to set implementation code lenses.
	 */
	public static final String IMPLEMENTATIONS_CODE_LENS_KEY = "java.implementationCodeLens";

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
	 * Preference key to enable/disable API descriptions in signature help.
	 */
	public static final String SIGNATURE_HELP_DESCRIPTION_ENABLED_KEY = "java.signatureHelp.description.enabled";

	/**
	 * Preference key to enable/disable Javadoc on hover.
	 */
	public static final String JAVA_HOVER_JAVADOC_ENABLED_KEY = "java.hover.javadoc.enabled";

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
	 * Preference key to specify the local libraries referenced by invisible project
	 */
	public static final String JAVA_PROJECT_REFERENCED_LIBRARIES_KEY = "java.project.referencedLibraries";
	public static final ReferencedLibraries JAVA_PROJECT_REFERENCED_LIBRARIES_DEFAULT;

	/**
	 * Preference key to specify the output path of the invisible project
	 */
	public static final String JAVA_PROJECT_OUTPUT_PATH_KEY = "java.project.outputPath";

	/**
	 * Preference key to specify the source paths of the invisible project
	 */
	public static final String JAVA_PROJECT_SOURCE_PATHS_KEY = "java.project.sourcePaths";

	/**
	 * Preference key for project build/configuration update settings.
	 */
	public static final String CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY = "java.configuration.updateBuildConfiguration";

	/**
	 * Preference key for Maven user settings.xml location.
	 */
	public static final String MAVEN_USER_SETTINGS_KEY = "java.configuration.maven.userSettings";

	/**
	 * Preference key for Maven global settings.xml location.
	 */
	public static final String MAVEN_GLOBAL_SETTINGS_KEY = "java.configuration.maven.globalSettings";

	/**
	 * Preference key for Maven lifecycle mappings xml location.
	 */
	public static final String MAVEN_LIFECYCLE_MAPPINGS_KEY = "java.configuration.maven.lifecycleMappings";

	public static final String MAVEN_NOT_COVERED_PLUGIN_EXECUTION_SEVERITY = "java.configuration.maven.notCoveredPluginExecutionSeverity";

	public static final String MAVEN_DEFAULT_MOJO_EXECUTION_ACTION = "java.configuration.maven.defaultMojoExecutionAction";

	/**
	 * Preference key to enable/disable the 'completion'.
	 */
	public static final String COMPLETION_ENABLED_KEY = "java.completion.enabled";

	/**
	 * Preference key to enable/disable postfix completion.
	 */
	public static final String POSTFIX_COMPLETION_KEY = "java.completion.postfix.enabled";

	/**
	 * Preference key to specify whether to match case when completion.
	 */
	public static final String COMPLETION_MATCH_CASE_MODE_KEY = "java.completion.matchCase";

	/**
	 * Preference key to specify whether text edit of completion item can be lazily resolved.
	 */
	public static final String COMPLETION_LAZY_RESOLVE_TEXT_EDIT_ENABLED_KEY = "java.completion.lazyResolveTextEdit.enabled";

	/**
	 * Preference key to enable/disable the 'foldingRange'.
	 */
	public static final String FOLDINGRANGE_ENABLED_KEY = "java.foldingRange.enabled";

	/**
	 * Preference key to enable/disable the selection range.
	 */
	public static final String SELECTIONRANGE_ENABLED_KEY = "java.selectionRange.enabled";

	/**
	 * A named preference that holds the allowed gradle wrapper sha256 checksums.
	 * <p>
	 * Value is of type <code>String</code>: list of checksums.
	 * </p>
	 */
	public static final String JAVA_GRADLE_WRAPPER_SHA256_KEY = "java.imports.gradle.wrapper.checksums";
	public static final List<String> JAVA_GRADLE_WRAPPER_SHA256_DEFAULT;

	/**
	 * A named preference that holds the favorite static members.
	 * <p>
	 * Value is of type <code>String</code>: list of favorites.
	 * </p>
	 */
	public static final String JAVA_COMPLETION_FAVORITE_MEMBERS_KEY = "java.completion.favoriteStaticMembers";
	public static final List<String> JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT;

	/**
	 * Preference key for maximum number of completion results to be returned.
	 * Defaults to 50.
	 */
	public static final String JAVA_COMPLETION_MAX_RESULTS_KEY = "java.completion.maxResults";
	public static final int JAVA_COMPLETION_MAX_RESULTS_DEFAULT = 50;

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

	public static final String JAVA_COMPLETION_COLLAPSE_KEY = "java.completion.collapseCompletionItems";

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
	 * A named preference that specifies the number of imports added before a
	 * star-import declaration is used.
	 * <p>
	 * Value is of type <code>Integer</code>: positive value specifying the number
	 * of non star-import is used
	 * </p>
	 */
	public static final String IMPORTS_ONDEMANDTHRESHOLD = "java.sources.organizeImports.starThreshold"; //$NON-NLS-1$

	/**
	 * A named preference that specifies the number of static imports added before a
	 * star-import declaration is used.
	 * <p>
	 * Value is of type <code>Integer</code>: positive value specifying the number
	 * of non star-import is used
	 * </p>
	 */
	public static final String IMPORTS_STATIC_ONDEMANDTHRESHOLD = "java.sources.organizeImports.staticStarThreshold"; //$NON-NLS-1$

	public static final int IMPORTS_ONDEMANDTHRESHOLD_DEFAULT = 99;

	public static final int IMPORTS_STATIC_ONDEMANDTHRESHOLD_DEFAULT = 99;

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

	public static final String JAVA_COMPLETION_FILTERED_TYPES_KEY = "java.completion.filteredTypes";
	public static final List<String> JAVA_COMPLETION_FILTERED_TYPES_DEFAULT;

	// A named preference that defines whether to use Objects.hash and Objects.equals methods when generating the hashCode and equals methods.
	public static final String JAVA_CODEGENERATION_HASHCODEEQUALS_USEJAVA7OBJECTS = "java.codeGeneration.hashCodeEquals.useJava7Objects";
	// A named preference that defines whether to use 'instanceof' to compare types when generating the hashCode and equals methods.
	public static final String JAVA_CODEGENERATION_HASHCODEEQUALS_USEINSTANCEOF = "java.codeGeneration.hashCodeEquals.useInstanceof";
	// A named preference that defines whether to use blocks in 'if' statements when generating the methods.
	public static final String JAVA_CODEGENERATION_USEBLOCKS = "java.codeGeneration.useBlocks";
	// A named preference that defines whether to generate method comments when generating the methods.
	public static final String JAVA_CODEGENERATION_GENERATECOMMENTS = "java.codeGeneration.generateComments";
	// A named preference that defines the location to insert the code generated by source actions.
	public static final String JAVA_CODEGENERATION_INSERTIONLOCATION = "java.codeGeneration.insertionLocation";

	// A named preference that defines that fields created via code generation should be declared final
	public static final String JAVA_CODEGENERATION_ADD_FINAL_FOR_NEW_DECLARATION = "java.codeGeneration.addFinalForNewDeclaration";

	// Specifies the file header snippets for new Java file.
	public static final String JAVA_TEMPLATES_FILEHEADER = "java.templates.fileHeader";
	// Specifies the type comment snippets for new Java type.
	public static final String JAVA_TEMPLATES_TYPECOMMENT = "java.templates.typeComment";
	// Project encoding settings
	public static final String JAVA_PROJECT_ENCODING = "java.project.encoding";

	public static final String JAVA_TELEMETRY_ENABLED_KEY = "java.telemetry.enabled";

	public static final String JAVA_EDIT_VALIDATE_ALL_OPEN_BUFFERS_ON_CHANGES = "java.edit.validateAllOpenBuffersOnChanges";
	public static final String JAVA_DIAGNOSTIC_FILER = "java.diagnostic.filter";
	/**
	 * The preferences for generating toString method.
	 */
	public static final String JAVA_CODEGENERATION_TOSTRING_TEMPLATE = "java.codeGeneration.toString.template";
	public static final String JAVA_CODEGENERATION_TOSTRING_CODESTYLE = "java.codeGeneration.toString.codeStyle";
	public static final String JAVA_CODEGENERATION_TOSTRING_SKIPNULLVALUES = "java.codeGeneration.toString.skipNullValues";
	public static final String JAVA_CODEGENERATION_TOSTRING_LISTARRAYCONTENTS = "java.codeGeneration.toString.listArrayContents";
	public static final String JAVA_CODEGENERATION_TOSTRING_LIMITELEMENTS = "java.codeGeneration.toString.limitElements";

	/**
	 * Preference key for the inlay hints parameters mode
	 */
	public static final String JAVA_INLAYHINTS_PARAMETERNAMES_ENABLED = "java.inlayHints.parameterNames.enabled";

	public static final String JAVA_INLAYHINTS_PARAMETERNAMES_SUPPRESS_WHEN_SAME_NAME_NUMBERED = "java.inlayHints.parameterNames.suppressWhenSameNameNumbered";

	public static final String JAVA_INLAYHINTS_VARIABLETYPES_ENABLED = "java.inlayHints.variableTypes.enabled";

	public static final String JAVA_INLAYHINTS_PARAMETERTYPES_ENABLED = "java.inlayHints.parameterTypes.enabled";

	/**
	 * Preference key for the inlay hints exclusion list
	 */
	public static final String JAVA_INLAYHINTS_PARAMETERNAMES_EXCLUSIONS = "java.inlayHints.parameterNames.exclusions";

	public static final String JAVA_CODEACTION_SORTMEMBER_AVOIDVOLATILECHANGES = "java.codeAction.sortMembers.avoidVolatileChanges";

	public static final String JAVA_JDT_LS_PROTOBUF_SUPPORT_ENABLED = "java.jdt.ls.protobufSupport.enabled";
	public static final String JAVA_JDT_LS_ANDROID_SUPPORT_ENABLED = "java.jdt.ls.androidSupport.enabled";
	public static final String JAVA_JDT_LS_ASPECTJ_SUPPORT_ENABLED = "java.jdt.ls.aspectjSupport.enabled";
	public static final String JAVA_JDT_LS_JAVAC_ENABLED = "java.jdt.ls.javac.enabled";

	public static final String JAVA_COMPILE_NULLANALYSIS_NONNULL = "java.compile.nullAnalysis.nonnull";
	public static final String JAVA_COMPILE_NULLANALYSIS_NULLABLE = "java.compile.nullAnalysis.nullable";
	public static final String JAVA_COMPILE_NULLANALYSIS_NONNULLBYDEFAULT = "java.compile.nullAnalysis.nonnullbydefault";
	public static final String JAVA_COMPILE_NULLANALYSIS_MODE = "java.compile.nullAnalysis.mode";

	public static final String LIFECYCLE_MAPPING_METADATA_SOURCE_NAME = "lifecycle-mapping-metadata.xml";

	/**
	 * Preference key for list of cleanups to run on save
	 */
	public static final String JAVA_CLEANUPS_ACTIONS = "java.cleanup.actions";
	public static final String JAVA_CLEANUPS_ACTIONS_ON_SAVE_DEPRECATED = "java.cleanup.actionsOnSave";
	public static final String JAVA_CLEANUPS_ACTIONS_ON_SAVE_CLEANUP = "java.saveActions.cleanup";
	public static final String JAVA_REFACTORING_EXTRACT_INTERFACE_REPLACE = "java.refactoring.extract.interface.replace";

	/**
	 * Preference key to enable/disable chain completion.
	 */
	public static final String CHAIN_COMPLETION_KEY = "java.completion.chain.enabled";

	/**
	 * Preference key to set the scope value to use when searching java code. Allowed value are
	 * <ul>
	 * <li><code>main</code>			-	Scope for main code</li>
	 * <li><code>all</code>				-	Scope for both test and main code</li>
	 * </ul>
	 * Any other unknown value will be treated as <code>all</code>.
	 */
	public static final String JAVA_SEARCH_SCOPE = "java.search.scope";

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
	public static final String DECLARATION = "textDocument/declaration";
	public static final String TYPEDEFINITION = "textDocument/typeDefinition";
	public static final String HOVER = "textDocument/hover";
	public static final String REFERENCES = "textDocument/references";
	public static final String DOCUMENT_HIGHLIGHT = "textDocument/documentHighlight";
	public static final String FOLDINGRANGE = "textDocument/foldingRange";
	public static final String IMPLEMENTATION = "textDocument/implementation";
	public static final String SELECTION_RANGE = "textDocument/selectionRange";
	public static final String INLAY_HINT = "textDocument/inlayHint";

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
	public static final String DECLARATION_ID = UUID.randomUUID().toString();
	public static final String TYPEDEFINITION_ID = UUID.randomUUID().toString();
	public static final String HOVER_ID = UUID.randomUUID().toString();
	public static final String REFERENCES_ID = UUID.randomUUID().toString();
	public static final String DOCUMENT_HIGHLIGHT_ID = UUID.randomUUID().toString();
	public static final String FOLDINGRANGE_ID = UUID.randomUUID().toString();
	public static final String WORKSPACE_WATCHED_FILES_ID = UUID.randomUUID().toString();
	public static final String IMPLEMENTATION_ID = UUID.randomUUID().toString();
	public static final String SELECTION_RANGE_ID = UUID.randomUUID().toString();
	public static final String INLAY_HINT_ID = UUID.randomUUID().toString();

	public static final Set<String> DISCOVERED_STATIC_IMPORTS = new LinkedHashSet<>();

	private static final String GRADLE_OFFLINE_MODE = "gradle.offline.mode";
	private static final int DEFAULT_TAB_SIZE = 4;

	// <typeName, subString of classpath>
	private static Map<String, List<String>> nonnullClasspathStorage = new HashMap<>();
	private static Map<String, List<String>> nullableClasspathStorage = new HashMap<>();
	private static Map<String, List<String>> nonnullbydefaultClasspathStorage = new HashMap<>();
	private List<String> filesAssociations = new ArrayList<>();

	private Map<String, Object> configuration;
	private FeatureStatus updateBuildConfigurationStatus;
	private boolean referencesCodeLensEnabled;
	private boolean importGradleEnabled;
	private boolean importGradleOfflineEnabled;
	private boolean gradleWrapperEnabled;
	private String gradleVersion;
	private List<String> gradleArguments;
	private List<String> gradleJvmArguments;
	private String gradleHome;
	private String gradleJavaHome;
	private String gradleUserHome;
	private boolean gradleAnnotationProcessingEnabled;
	private boolean importMavenEnabled;
	private boolean mavenOffline;
	private boolean mavenDisableTestClasspathFlag;
	private boolean mavenDownloadSources;
	private boolean eclipseDownloadSources;
	private boolean mavenUpdateSnapshots;
	private String implementationsCodeLens;
	private boolean javaFormatEnabled;
	private String javaQuickFixShowAt;
	private boolean javaFormatOnTypeEnabled;
	private boolean javaSaveActionsOrganizeImportsEnabled;
	private boolean signatureHelpEnabled;
	private boolean signatureHelpDescriptionEnabled;
	private boolean hoverJavadocEnabled;
	private boolean renameEnabled;
	private boolean executeCommandEnabled;
	private boolean autobuildEnabled;
	private boolean completionEnabled;
	private boolean postfixCompletionEnabled;
	private CompletionMatchCaseMode completionMatchCaseMode;
	private boolean completionLazyResolveTextEditEnabled;
	private boolean completionOverwrite;
	private boolean foldingRangeEnabled;
	private boolean selectionRangeEnabled;
	private CompletionGuessMethodArgumentsMode guessMethodArguments;
	private boolean collapseCompletionItems;

	private boolean javaFormatComments;
	private boolean hashCodeEqualsTemplateUseJava7Objects;
	private boolean hashCodeEqualsTemplateUseInstanceof;
	private boolean codeGenerationTemplateUseBlocks;
	private boolean codeGenerationTemplateGenerateComments;
	private String generateToStringTemplate;
	private String generateToStringCodeStyle;
	private boolean generateToStringSkipNullValues;
	private boolean generateToStringListArrayContents;
	private int generateToStringLimitElements;
	private String codeGenerationInsertionLocation;
	private String codeGenerationAddFinalForNewDeclaration;
	private List<String> preferredContentProviderIds;
	private boolean includeAccessors;
	private boolean smartSemicolonDetection;
	private boolean includeDecompiledSources;
	private boolean includeSourceMethodDeclarations;

	private String mavenUserSettings;
	private String mavenGlobalSettings;
	private String mavenLifecycleMappings;
	private String mavenNotCoveredPluginExecutionSeverity;
	private String mavenDefaultMojoExecutionAction;

	private List<String> javaCompletionFavoriteMembers;
	private List<?> gradleWrapperList;

	private List<String> javaImportExclusions = new LinkedList<>();
	private ReferencedLibraries referencedLibraries;
	private String invisibleProjectOutputPath;
	private List<String> invisibleProjectSourcePaths;
	private String javaHome;
	private List<String> importOrder;
	private List<String> filteredTypes;
	private String formatterUrl;
	private String settingsUrl;
	private String formatterProfileName;
	private Collection<IPath> rootPaths;
	private Collection<IPath> triggerFiles;
	private Collection<IPath> projectConfigurations;
	private int parallelBuildsCount;
	private int maxCompletionResults;
	private int importOnDemandThreshold;
	private int staticImportOnDemandThreshold;
	private Set<RuntimeEnvironment> runtimes = new HashSet<>();
	private List<String> resourceFilters;

	private List<String> fileHeaderTemplate = new LinkedList<>();
	private List<String> typeCommentTemplate = new LinkedList<>();
	private boolean insertSpaces;
	private int tabSize;
	private InlayHintsParameterMode inlayHintsParameterMode;
	private List<String> inlayHintsExclusionList;
	private boolean inlayHintsVariableTypesEnabled;
	private boolean inlayHintsParameterTypesEnabled;
	private ProjectEncodingMode projectEncoding;
	private boolean avoidVolatileChanges;
	private boolean protobufSupportEnabled;
	private boolean aspectjSupportEnabled;
	private boolean javacEnabled;
	private boolean androidSupportEnabled;
	private List<String> nonnullTypes;
	private List<String> nullableTypes;
	private List<String> nonnullbydefaultTypes;
	private FeatureStatus nullAnalysisMode;
	private List<String> cleanUpActions;
	private boolean cleanUpActionsOnSaveEnabled;
	private boolean extractInterfaceReplaceEnabled;
	private boolean telemetryEnabled;
	private boolean validateAllOpenBuffersOnChanges;
	private boolean chainCompletionEnabled;
	private List<String> diagnosticFilter;
	private SearchScope searchScope;
	private boolean inlayHintsSuppressedWhenSameNameNumberedParameter;

	static {
		JAVA_IMPORT_EXCLUSIONS_DEFAULT = new LinkedList<>();
		JAVA_CONFIGURATION_RUNTIMES_DEFAULT = new ArrayList<>();
		JAVA_GRADLE_WRAPPER_SHA256_DEFAULT = new ArrayList<>();
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/node_modules/**");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/.metadata/**");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/archetype-resources/**");
		JAVA_IMPORT_EXCLUSIONS_DEFAULT.add("**/META-INF/maven/**");
		JAVA_PROJECT_REFERENCED_LIBRARIES_DEFAULT = new ReferencedLibraries();
		JAVA_PROJECT_REFERENCED_LIBRARIES_DEFAULT.getInclude().add("lib/**");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT = new ArrayList<>();
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.Assert.*");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.Assume.*");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.Assertions.*");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.Assumptions.*");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.DynamicContainer.*");
		JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT.add("org.junit.jupiter.api.DynamicTest.*");
		JAVA_IMPORT_ORDER_DEFAULT = new LinkedList<>();
		JAVA_IMPORT_ORDER_DEFAULT.add("java");
		JAVA_IMPORT_ORDER_DEFAULT.add("javax");
		JAVA_IMPORT_ORDER_DEFAULT.add("org");
		JAVA_IMPORT_ORDER_DEFAULT.add("com");
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT = new ArrayList<>();
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT.add("com.sun.*");
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT.add("io.micrometer.shaded.*");
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT.add("java.awt.*");
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT.add("jdk.*");
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT.add("org.graalvm.*");
		JAVA_COMPLETION_FILTERED_TYPES_DEFAULT.add("sun.*");

		JAVA_RESOURCE_FILTERS_DEFAULT = Arrays.asList("node_modules", "\\.git");
		initializeNullAnalysisClasspathStorage();
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

	public static enum SearchScope {
		all, main;

		static SearchScope fromString(String value, SearchScope defaultScope) {
			if (value != null) {
				String val = value.toLowerCase();
				try {
					return valueOf(val);
				} catch(Exception e) {
					//fall back to default severity
				}
			}
			return defaultScope;
		}
	}

	public static class ReferencedLibraries {
		private Set<String> include;
		private Set<String> exclude;
		private Map<String, String> sources;

		public ReferencedLibraries() {
			this(new HashSet<>(), new HashSet<>(), new HashMap<>());
		}

		public ReferencedLibraries(Set<String> include) {
			this(include, new HashSet<>(), new HashMap<>());
		}

		public ReferencedLibraries(Set<String> include, Set<String> exclude, Map<String, String> sources) {
			this.include = new HashSet<>() {

				@Override
				public boolean add(String e) {
					return super.add(ResourceUtils.expandPath(e));
				}

			};
			this.include.addAll(include);
			this.exclude = new HashSet<>() {

				@Override
				public boolean add(String e) {
					return super.add(ResourceUtils.expandPath(e));
				}

			};
			this.exclude.addAll(exclude);
			this.sources = new HashMap<>() {
				@Override
				public String put(String key, String value) {
					return super.put(ResourceUtils.expandPath(key), ResourceUtils.expandPath(value));
				}
			};

			// Avoid 'putAll' as it may not call 'put', thus skipping expansion
			// https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3495
			for (Map.Entry<String, String> e : sources.entrySet()) {
				this.sources.put(e.getKey(), e.getValue());
			}
		}

		public Set<String> getInclude() {
			return include;
		}

		public Set<String> getExclude() {
			return exclude;
		}

		public Map<String, String> getSources() {
			return sources;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			ReferencedLibraries other = (ReferencedLibraries) obj;
			return Objects.equals(include, other.include)
				&& Objects.equals(exclude, other.exclude)
				&& Objects.equals(sources, other.sources);
		}

	}

	public Preferences() {
		configuration = null;
		updateBuildConfigurationStatus = FeatureStatus.interactive;
		importGradleEnabled = true;
		importGradleOfflineEnabled = false;
		gradleWrapperEnabled = true;
		gradleVersion = null;
		gradleArguments = new ArrayList<>();
		gradleJvmArguments = new ArrayList<>();
		gradleHome = null;
		gradleJavaHome = null;
		gradleUserHome = null;
		gradleAnnotationProcessingEnabled = true;
		importMavenEnabled = true;
		mavenOffline = false;
		mavenDisableTestClasspathFlag = false;
		mavenDownloadSources = false;
		eclipseDownloadSources = false;
		mavenUpdateSnapshots = false;
		referencesCodeLensEnabled = true;
		implementationsCodeLens = "none";
		javaFormatEnabled = true;
		javaQuickFixShowAt = LINE;
		javaFormatOnTypeEnabled = false;
		javaSaveActionsOrganizeImportsEnabled = false;
		signatureHelpEnabled = false;
		signatureHelpDescriptionEnabled = false;
		hoverJavadocEnabled = true;
		renameEnabled = true;
		executeCommandEnabled = true;
		autobuildEnabled = true;
		completionEnabled = true;
		postfixCompletionEnabled = true;
		completionMatchCaseMode = CompletionMatchCaseMode.OFF;
		completionLazyResolveTextEditEnabled = false;
		completionOverwrite = true;
		foldingRangeEnabled = true;
		selectionRangeEnabled = true;
		guessMethodArguments = CompletionGuessMethodArgumentsMode.INSERT_PARAMETER_NAMES;
		collapseCompletionItems = false;
		javaFormatComments = true;
		hashCodeEqualsTemplateUseJava7Objects = false;
		hashCodeEqualsTemplateUseInstanceof = false;
		codeGenerationTemplateUseBlocks = false;
		codeGenerationTemplateGenerateComments = false;
		generateToStringSkipNullValues = false;
		generateToStringListArrayContents = true;
		generateToStringLimitElements = 0;
		codeGenerationAddFinalForNewDeclaration = "none";
		codeGenerationInsertionLocation = null;
		preferredContentProviderIds = null;
		javaImportExclusions = JAVA_IMPORT_EXCLUSIONS_DEFAULT;
		javaCompletionFavoriteMembers = JAVA_COMPLETION_FAVORITE_MEMBERS_DEFAULT;
		javaHome = null;
		formatterUrl = null;
		settingsUrl = null;
		formatterProfileName = null;
		importOrder = JAVA_IMPORT_ORDER_DEFAULT;
		filteredTypes = JAVA_COMPLETION_FILTERED_TYPES_DEFAULT;
		parallelBuildsCount = PreferenceInitializer.PREF_MAX_CONCURRENT_BUILDS_DEFAULT;
		maxCompletionResults = JAVA_COMPLETION_MAX_RESULTS_DEFAULT;
		importOnDemandThreshold = IMPORTS_ONDEMANDTHRESHOLD_DEFAULT;
		staticImportOnDemandThreshold = IMPORTS_STATIC_ONDEMANDTHRESHOLD_DEFAULT;
		referencedLibraries = JAVA_PROJECT_REFERENCED_LIBRARIES_DEFAULT;
		resourceFilters = JAVA_RESOURCE_FILTERS_DEFAULT;
		includeAccessors = true;
		smartSemicolonDetection = false;
		includeDecompiledSources = true;
		includeSourceMethodDeclarations = false;
		insertSpaces = true;
		tabSize = DEFAULT_TAB_SIZE;
		mavenNotCoveredPluginExecutionSeverity = IGNORE;
		mavenDefaultMojoExecutionAction = IGNORE;
		inlayHintsParameterMode = InlayHintsParameterMode.LITERALS;
		inlayHintsVariableTypesEnabled = false;
		inlayHintsParameterTypesEnabled = false;
		projectEncoding = ProjectEncodingMode.IGNORE;
		avoidVolatileChanges = true;
		javacEnabled = false;
		nonnullTypes = new ArrayList<>();
		nullableTypes = new ArrayList<>();
		nonnullbydefaultTypes = new ArrayList<>();
		nullAnalysisMode = FeatureStatus.disabled;
		cleanUpActions = new ArrayList<>();
		cleanUpActionsOnSaveEnabled = false;
		extractInterfaceReplaceEnabled = false;
		telemetryEnabled = false;
		validateAllOpenBuffersOnChanges = true;
		diagnosticFilter = new ArrayList<>();
		searchScope = SearchScope.all;
	}

	private static void initializeNullAnalysisClasspathStorage() {
		// constructor classpath jar names with groupid + system slash + artifactid
		// should support Maven style and Gradle style classpath
		nonnullClasspathStorage.put("javax.annotation.Nonnull", getClasspathSubStringFromArtifact("com.google.code.findbugs:jsr305"));
		nullableClasspathStorage.put("javax.annotation.Nullable", getClasspathSubStringFromArtifact("com.google.code.findbugs:jsr305"));
		nonnullbydefaultClasspathStorage.put("javax.annotation.ParametersAreNonnullByDefault", getClasspathSubStringFromArtifact("com.google.code.findbugs:jsr305"));

		nonnullClasspathStorage.put("org.eclipse.jdt.annotation.NonNull", getClasspathSubStringFromArtifact("org.eclipse.jdt:org.eclipse.jdt.annotation"));
		nullableClasspathStorage.put("org.eclipse.jdt.annotation.Nullable", getClasspathSubStringFromArtifact("org.eclipse.jdt:org.eclipse.jdt.annotation"));
		nonnullbydefaultClasspathStorage.put("org.eclipse.jdt.annotation.NonNullByDefault", getClasspathSubStringFromArtifact("org.eclipse.jdt:org.eclipse.jdt.annotation"));

		nonnullClasspathStorage.put("org.springframework.lang.NonNull", getClasspathSubStringFromArtifact("org.springframework:spring-core"));
		nullableClasspathStorage.put("org.springframework.lang.Nullable", getClasspathSubStringFromArtifact("org.springframework:spring-core"));
		nonnullbydefaultClasspathStorage.put("org.springframework.lang.NonNullApi", getClasspathSubStringFromArtifact("org.springframework:spring-core"));

		nonnullClasspathStorage.put("io.micrometer.core.lang.NonNull", getClasspathSubStringFromArtifact("io.micrometer:micrometer-core"));
		nullableClasspathStorage.put("io.micrometer.core.lang.Nullable", getClasspathSubStringFromArtifact("io.micrometer:micrometer-core"));
		nonnullbydefaultClasspathStorage.put("io.micrometer.core.lang.NonNullApi", getClasspathSubStringFromArtifact("io.micrometer:micrometer-core"));

		nonnullClasspathStorage.put("org.jetbrains.annotations.NotNull", getClasspathSubStringFromArtifact("org.jetbrains:annotations"));
		nullableClasspathStorage.put("org.jetbrains.annotations.Nullable", getClasspathSubStringFromArtifact("org.jetbrains:annotations"));

		nonnullClasspathStorage.put("org.jspecify.annotations.NonNull", getClasspathSubStringFromArtifact("org.jspecify:jspecify"));
		nullableClasspathStorage.put("org.jspecify.annotations.Nullable", getClasspathSubStringFromArtifact("org.jspecify:jspecify"));
		nonnullbydefaultClasspathStorage.put("org.jspecify.annotations.NullMarked", getClasspathSubStringFromArtifact("org.jspecify:jspecify"));
	}

	private static List<String> getClasspathSubStringFromArtifact(String artifact) {
		// groupID:artifactID
		String[] splitIds = artifact.split(":");
		if (splitIds.length != 2) {
			return new ArrayList<>();
		}
		String groupId = splitIds[0];
		String artifactId = splitIds[1];
		String gradleStyleClasspath = Paths.get(groupId, artifactId).toString();
		String[] groupIdSplitByDot = groupId.split("\\.");
		if (groupIdSplitByDot.length < 1) {
			return new ArrayList<>();
		}
		String mavenStyleClasspath = Paths.get("", groupIdSplitByDot).resolve(artifactId).toString();
		return new ArrayList<>(Arrays.asList(gradleStyleClasspath, mavenStyleClasspath));
	}

	/**
	 * Create a {@link Preferences} model from a {@link Map} configuration.
	 *
	 * @param configuration the configuration map to apply
	 * @return a new Preferences object with the configuration applied
	 */
	public static Preferences createFrom(Map<String, Object> configuration) {
		return updateFrom(new Preferences(), configuration);
	}

	/**
	 * Creates a deep copy of this Preferences object.
	 *
	 * <p>Collections are deep copied to prevent shared state. Note that:</p>
	 * <ul>
	 * <li>The configuration map itself is copied, but values within it are not (shallow copy of values)</li>
	 * <li>RuntimeEnvironment objects are copied by reference (shared between original and clone)</li>
	 * <li>IPath objects are copied by reference (safe since IPath is immutable)</li>
	 * </ul>
	 *
	 * @return a new Preferences object with the same values as this one
	 */
	@Override
	public Preferences clone() {
		Preferences prefs = new Preferences();

		// Deep copy configuration map (note: values are shallow copied)
		if (this.configuration != null) {
			prefs.configuration = new HashMap<>(this.configuration);
		}

		// Copy immutable and primitive fields
		prefs.updateBuildConfigurationStatus = this.updateBuildConfigurationStatus;
		prefs.importGradleEnabled = this.importGradleEnabled;
		prefs.insertSpaces = this.insertSpaces;
		prefs.tabSize = this.tabSize;
		prefs.importGradleOfflineEnabled = this.importGradleOfflineEnabled;
		prefs.gradleWrapperEnabled = this.gradleWrapperEnabled;
		prefs.gradleVersion = this.gradleVersion;
		prefs.gradleHome = this.gradleHome;
		prefs.gradleJavaHome = this.gradleJavaHome;
		prefs.gradleUserHome = this.gradleUserHome;
		prefs.gradleAnnotationProcessingEnabled = this.gradleAnnotationProcessingEnabled;
		prefs.importMavenEnabled = this.importMavenEnabled;
		prefs.mavenOffline = this.mavenOffline;
		prefs.mavenDisableTestClasspathFlag = this.mavenDisableTestClasspathFlag;
		prefs.mavenDownloadSources = this.mavenDownloadSources;
		prefs.eclipseDownloadSources = this.eclipseDownloadSources;
		prefs.mavenUpdateSnapshots = this.mavenUpdateSnapshots;
		prefs.referencesCodeLensEnabled = this.referencesCodeLensEnabled;
		prefs.implementationsCodeLens = this.implementationsCodeLens;
		prefs.javaFormatEnabled = this.javaFormatEnabled;
		prefs.javaQuickFixShowAt = this.javaQuickFixShowAt;
		prefs.javaFormatOnTypeEnabled = this.javaFormatOnTypeEnabled;
		prefs.javaSaveActionsOrganizeImportsEnabled = this.javaSaveActionsOrganizeImportsEnabled;
		prefs.signatureHelpEnabled = this.signatureHelpEnabled;
		prefs.signatureHelpDescriptionEnabled = this.signatureHelpDescriptionEnabled;
		prefs.hoverJavadocEnabled = this.hoverJavadocEnabled;
		prefs.renameEnabled = this.renameEnabled;
		prefs.executeCommandEnabled = this.executeCommandEnabled;
		prefs.autobuildEnabled = this.autobuildEnabled;
		prefs.completionEnabled = this.completionEnabled;
		prefs.postfixCompletionEnabled = this.postfixCompletionEnabled;
		prefs.completionMatchCaseMode = this.completionMatchCaseMode;
		prefs.completionLazyResolveTextEditEnabled = this.completionLazyResolveTextEditEnabled;
		prefs.completionOverwrite = this.completionOverwrite;
		prefs.foldingRangeEnabled = this.foldingRangeEnabled;
		prefs.selectionRangeEnabled = this.selectionRangeEnabled;
		prefs.guessMethodArguments = this.guessMethodArguments;
		prefs.collapseCompletionItems = this.collapseCompletionItems;
		prefs.hashCodeEqualsTemplateUseJava7Objects = this.hashCodeEqualsTemplateUseJava7Objects;
		prefs.hashCodeEqualsTemplateUseInstanceof = this.hashCodeEqualsTemplateUseInstanceof;
		prefs.codeGenerationTemplateUseBlocks = this.codeGenerationTemplateUseBlocks;
		prefs.codeGenerationTemplateGenerateComments = this.codeGenerationTemplateGenerateComments;
		prefs.generateToStringTemplate = this.generateToStringTemplate;
		prefs.generateToStringCodeStyle = this.generateToStringCodeStyle;
		prefs.generateToStringSkipNullValues = this.generateToStringSkipNullValues;
		prefs.generateToStringListArrayContents = this.generateToStringListArrayContents;
		prefs.generateToStringLimitElements = this.generateToStringLimitElements;
		prefs.codeGenerationInsertionLocation = this.codeGenerationInsertionLocation;
		prefs.codeGenerationAddFinalForNewDeclaration = this.codeGenerationAddFinalForNewDeclaration;
		prefs.invisibleProjectOutputPath = this.invisibleProjectOutputPath;
		prefs.mavenUserSettings = this.mavenUserSettings;
		prefs.mavenGlobalSettings = this.mavenGlobalSettings;
		prefs.mavenLifecycleMappings = this.mavenLifecycleMappings;
		prefs.mavenNotCoveredPluginExecutionSeverity = this.mavenNotCoveredPluginExecutionSeverity;
		prefs.mavenDefaultMojoExecutionAction = this.mavenDefaultMojoExecutionAction;
		prefs.javaHome = this.javaHome;
		prefs.formatterUrl = this.formatterUrl;
		prefs.settingsUrl = this.settingsUrl;
		prefs.formatterProfileName = this.formatterProfileName;
		prefs.javaFormatComments = this.javaFormatComments;
		prefs.parallelBuildsCount = this.parallelBuildsCount;
		prefs.maxCompletionResults = this.maxCompletionResults;
		prefs.importOnDemandThreshold = this.importOnDemandThreshold;
		prefs.staticImportOnDemandThreshold = this.staticImportOnDemandThreshold;
		prefs.includeAccessors = this.includeAccessors;
		prefs.smartSemicolonDetection = this.smartSemicolonDetection;
		prefs.includeDecompiledSources = this.includeDecompiledSources;
		prefs.includeSourceMethodDeclarations = this.includeSourceMethodDeclarations;
		prefs.inlayHintsParameterMode = this.inlayHintsParameterMode;
		prefs.inlayHintsSuppressedWhenSameNameNumberedParameter = this.inlayHintsSuppressedWhenSameNameNumberedParameter;
		prefs.inlayHintsVariableTypesEnabled = this.inlayHintsVariableTypesEnabled;
		prefs.inlayHintsParameterTypesEnabled = this.inlayHintsParameterTypesEnabled;
		prefs.projectEncoding = this.projectEncoding;
		prefs.avoidVolatileChanges = this.avoidVolatileChanges;
		prefs.protobufSupportEnabled = this.protobufSupportEnabled;
		prefs.aspectjSupportEnabled = this.aspectjSupportEnabled;
		prefs.javacEnabled = this.javacEnabled;
		prefs.androidSupportEnabled = this.androidSupportEnabled;
		prefs.nullAnalysisMode = this.nullAnalysisMode;
		prefs.cleanUpActionsOnSaveEnabled = this.cleanUpActionsOnSaveEnabled;
		prefs.extractInterfaceReplaceEnabled = this.extractInterfaceReplaceEnabled;
		prefs.telemetryEnabled = this.telemetryEnabled;
		prefs.validateAllOpenBuffersOnChanges = this.validateAllOpenBuffersOnChanges;
		prefs.chainCompletionEnabled = this.chainCompletionEnabled;
		prefs.searchScope = this.searchScope;

		// Deep copy collections
		prefs.gradleArguments = this.gradleArguments != null ? new ArrayList<>(this.gradleArguments) : null;
		prefs.gradleJvmArguments = this.gradleJvmArguments != null ? new ArrayList<>(this.gradleJvmArguments) : null;
		prefs.javaImportExclusions = this.javaImportExclusions != null ? new LinkedList<>(this.javaImportExclusions) : null;
		prefs.invisibleProjectSourcePaths = this.invisibleProjectSourcePaths != null ? new ArrayList<>(this.invisibleProjectSourcePaths) : null;
		prefs.javaCompletionFavoriteMembers = this.javaCompletionFavoriteMembers != null ? new ArrayList<>(this.javaCompletionFavoriteMembers) : null;
		prefs.gradleWrapperList = this.gradleWrapperList != null ? new ArrayList<>(this.gradleWrapperList) : null;
		prefs.preferredContentProviderIds = this.preferredContentProviderIds != null ? new ArrayList<>(this.preferredContentProviderIds) : null;
		prefs.importOrder = this.importOrder != null ? new ArrayList<>(this.importOrder) : null;
		prefs.filteredTypes = this.filteredTypes != null ? new ArrayList<>(this.filteredTypes) : null;
		prefs.resourceFilters = this.resourceFilters != null ? new ArrayList<>(this.resourceFilters) : null;
		prefs.fileHeaderTemplate = this.fileHeaderTemplate != null ? new LinkedList<>(this.fileHeaderTemplate) : null;
		prefs.typeCommentTemplate = this.typeCommentTemplate != null ? new LinkedList<>(this.typeCommentTemplate) : null;
		prefs.inlayHintsExclusionList = this.inlayHintsExclusionList != null ? new ArrayList<>(this.inlayHintsExclusionList) : null;
		prefs.nonnullTypes = this.nonnullTypes != null ? new ArrayList<>(this.nonnullTypes) : null;
		prefs.nullableTypes = this.nullableTypes != null ? new ArrayList<>(this.nullableTypes) : null;
		prefs.nonnullbydefaultTypes = this.nonnullbydefaultTypes != null ? new ArrayList<>(this.nonnullbydefaultTypes) : null;
		prefs.cleanUpActions = this.cleanUpActions != null ? new ArrayList<>(this.cleanUpActions) : null;
		prefs.diagnosticFilter = this.diagnosticFilter != null ? new ArrayList<>(this.diagnosticFilter) : null;
		prefs.filesAssociations = this.filesAssociations != null ? new ArrayList<>(this.filesAssociations) : null;
		prefs.runtimes = this.runtimes != null ? new HashSet<>(this.runtimes) : null;

		// Deep copy complex objects
		if (this.referencedLibraries != null) {
			prefs.referencedLibraries = new ReferencedLibraries(
				new HashSet<>(this.referencedLibraries.getInclude()),
				new HashSet<>(this.referencedLibraries.getExclude()),
				new HashMap<>(this.referencedLibraries.getSources())
			);
		}

		// Copy collection fields (these are typically not modified after creation)
		prefs.rootPaths = this.rootPaths != null ? new ArrayList<>(this.rootPaths) : null;
		prefs.triggerFiles = this.triggerFiles != null ? new ArrayList<>(this.triggerFiles) : null;
		prefs.projectConfigurations = this.projectConfigurations != null ? new ArrayList<>(this.projectConfigurations) : null;

		return prefs;
	}

	/**
	 * Create an updated {@link Preferences} model from an existing preferences and a {@link Map} with partial configuration.
	 * Only the settings present in the configuration will be updated, all other settings will be preserved from the existing preferences.
	 *
	 * @param existing the existing preferences to update
	 * @param configuration the partial configuration with updated settings
	 * @return a new Preferences object with updated values
	 */
	@SuppressWarnings("unchecked")
	public static Preferences updateFrom(Preferences existing, Map<String, Object> configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("Configuration can not be null");
		}
		if (existing == null) {
			throw new IllegalArgumentException("Existing preferences can not be null");
		}

		// Start with a clone of existing preferences
		Preferences prefs = existing.clone();

		// Merge the partial configuration into the existing configuration
		if (!configuration.isEmpty()) {
			if (prefs.configuration == null) {
				prefs.configuration = new HashMap<>();
			}
			prefs.configuration.putAll(configuration);
		}

		// Now update only the fields that are present in the partial configuration
		if (getValue(configuration, CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY) != null) {
			String updateBuildConfiguration = getString(configuration, CONFIGURATION_UPDATE_BUILD_CONFIGURATION_KEY, null);
			prefs.setUpdateBuildConfigurationStatus(
					FeatureStatus.fromString(updateBuildConfiguration, existing.updateBuildConfigurationStatus));
		}

		if (getValue(configuration, IMPORT_GRADLE_ENABLED) != null) {
			boolean importGradleEnabled = getBoolean(configuration, IMPORT_GRADLE_ENABLED, existing.importGradleEnabled);
			prefs.setImportGradleEnabled(importGradleEnabled);
		}

		if (getValue(configuration, JAVA_CONFIGURATION_INSERTSPACES) != null) {
			boolean insertSpaces = getBoolean(configuration, JAVA_CONFIGURATION_INSERTSPACES, existing.insertSpaces);
			prefs.setInsertSpaces(insertSpaces);
		}

		if (getValue(configuration, JAVA_CONFIGURATION_TABSIZE) != null) {
			int tabSize = getInt(configuration, JAVA_CONFIGURATION_TABSIZE, existing.tabSize);
			prefs.setTabSize(tabSize);
		}

		if (getValue(configuration, IMPORT_GRADLE_OFFLINE_ENABLED) != null) {
			boolean importGradleOfflineEnabled = getBoolean(configuration, IMPORT_GRADLE_OFFLINE_ENABLED, existing.importGradleOfflineEnabled);
			prefs.setImportGradleOfflineEnabled(importGradleOfflineEnabled);
		}

		if (getValue(configuration, GRADLE_WRAPPER_ENABLED) != null) {
			boolean gradleWrapperEnabled = getBoolean(configuration, GRADLE_WRAPPER_ENABLED, existing.gradleWrapperEnabled);
			prefs.setGradleWrapperEnabled(gradleWrapperEnabled);
		}

		if (getValue(configuration, GRADLE_VERSION) != null) {
			String gradleVersion = getString(configuration, GRADLE_VERSION);
			prefs.setGradleVersion(gradleVersion);
		}

		if (getValue(configuration, GRADLE_ARGUMENTS) != null) {
			List<String> gradleArguments = getList(configuration, GRADLE_ARGUMENTS);
			prefs.setGradleArguments(gradleArguments);
		}

		if (getValue(configuration, GRADLE_JVM_ARGUMENTS) != null) {
			List<String> gradleJvmArguments = getList(configuration, GRADLE_JVM_ARGUMENTS);
			prefs.setGradleJvmArguments(gradleJvmArguments);
		}

		if (getValue(configuration, GRADLE_HOME) != null) {
			String gradleHome = getString(configuration, GRADLE_HOME);
			prefs.setGradleHome(gradleHome);
		}

		if (getValue(configuration, GRADLE_JAVA_HOME) != null) {
			String gradleJavaHome = getString(configuration, GRADLE_JAVA_HOME);
			prefs.setGradleJavaHome(gradleJavaHome);
		}

		if (getValue(configuration, GRADLE_USER_HOME) != null) {
			String gradleUserHome = getString(configuration, GRADLE_USER_HOME);
			prefs.setGradleUserHome(gradleUserHome);
		}

		if (getValue(configuration, GRADLE_ANNOTATION_PROCESSING_ENABLED) != null) {
			boolean gradleAnnotationProcessingEnabled = getBoolean(configuration, GRADLE_ANNOTATION_PROCESSING_ENABLED, existing.gradleAnnotationProcessingEnabled);
			prefs.setGradleAnnotationProcessingEnabled(gradleAnnotationProcessingEnabled);
		}

		if (getValue(configuration, IMPORT_MAVEN_ENABLED) != null) {
			boolean importMavenEnabled = getBoolean(configuration, IMPORT_MAVEN_ENABLED, existing.importMavenEnabled);
			prefs.setImportMavenEnabled(importMavenEnabled);
		}

		if (getValue(configuration, IMPORT_MAVEN_OFFLINE) != null) {
			boolean mavenOffline = getBoolean(configuration, IMPORT_MAVEN_OFFLINE, existing.mavenOffline);
			prefs.setMavenOffline(mavenOffline);
		}

		if (getValue(configuration, MAVEN_DISABLE_TEST_CLASSPATH_FLAG) != null) {
			boolean mavenDisableTestClasspathFlag = getBoolean(configuration, MAVEN_DISABLE_TEST_CLASSPATH_FLAG, existing.mavenDisableTestClasspathFlag);
			prefs.setMavenDisableTestClasspathFlag(mavenDisableTestClasspathFlag);
		}

		if (getValue(configuration, MAVEN_DOWNLOAD_SOURCES) != null) {
			boolean mavenDownloadSources = getBoolean(configuration, MAVEN_DOWNLOAD_SOURCES, existing.mavenDownloadSources);
			prefs.setMavenDownloadSources(mavenDownloadSources);
		}

		if (getValue(configuration, ECLIPSE_DOWNLOAD_SOURCES) != null) {
			boolean eclipseDownloadSources = getBoolean(configuration, ECLIPSE_DOWNLOAD_SOURCES, existing.eclipseDownloadSources);
			prefs.setEclipseDownloadSources(eclipseDownloadSources);
		}

		if (getValue(configuration, MAVEN_UPDATE_SNAPSHOTS) != null) {
			boolean updateSnapshots = getBoolean(configuration, MAVEN_UPDATE_SNAPSHOTS, existing.mavenUpdateSnapshots);
			prefs.setMavenUpdateSnapshots(updateSnapshots);
		}

		if (getValue(configuration, REFERENCES_CODE_LENS_ENABLED_KEY) != null) {
			boolean referenceCodelensEnabled = getBoolean(configuration, REFERENCES_CODE_LENS_ENABLED_KEY, existing.referencesCodeLensEnabled);
			prefs.setReferencesCodelensEnabled(referenceCodelensEnabled);
		}

		if (getValue(configuration, IMPLEMENTATIONS_CODE_LENS_KEY) != null) {
			String implementationCodeLens = getString(configuration, IMPLEMENTATIONS_CODE_LENS_KEY, existing.implementationsCodeLens);
			prefs.setImplementationCodelens(implementationCodeLens);
		}

		if (getValue(configuration, JAVA_FORMAT_ENABLED_KEY) != null) {
			boolean javaFormatEnabled = getBoolean(configuration, JAVA_FORMAT_ENABLED_KEY, existing.javaFormatEnabled);
			prefs.setJavaFormatEnabled(javaFormatEnabled);
		}

		if (getValue(configuration, QUICK_FIX_SHOW_AT) != null) {
			String javaQuickFixShowAt = getString(configuration, QUICK_FIX_SHOW_AT, existing.javaQuickFixShowAt);
			prefs.setJavaQuickFixShowAt(javaQuickFixShowAt);
		}

		if (getValue(configuration, JAVA_FORMAT_ON_TYPE_ENABLED_KEY) != null) {
			boolean javaFormatOnTypeEnabled = getBoolean(configuration, JAVA_FORMAT_ON_TYPE_ENABLED_KEY, existing.javaFormatOnTypeEnabled);
			prefs.setJavaFormatOnTypeEnabled(javaFormatOnTypeEnabled);
		}

		if (getValue(configuration, JAVA_SAVE_ACTIONS_ORGANIZE_IMPORTS_KEY) != null) {
			boolean javaSaveActionAutoOrganizeImportsEnabled = getBoolean(configuration, JAVA_SAVE_ACTIONS_ORGANIZE_IMPORTS_KEY, existing.javaSaveActionsOrganizeImportsEnabled);
			prefs.setJavaSaveActionAutoOrganizeImportsEnabled(javaSaveActionAutoOrganizeImportsEnabled);
		}

		if (getValue(configuration, SIGNATURE_HELP_ENABLED_KEY) != null) {
			boolean signatureHelpEnabled = getBoolean(configuration, SIGNATURE_HELP_ENABLED_KEY, existing.signatureHelpEnabled);
			prefs.setSignatureHelpEnabled(signatureHelpEnabled);
		}

		if (getValue(configuration, SIGNATURE_HELP_DESCRIPTION_ENABLED_KEY) != null) {
			boolean signatureDescriptionEnabled = getBoolean(configuration, SIGNATURE_HELP_DESCRIPTION_ENABLED_KEY, existing.signatureHelpDescriptionEnabled);
			prefs.setSignatureHelpDescriptionEnabled(signatureDescriptionEnabled);
		}

		if (getValue(configuration, JAVA_HOVER_JAVADOC_ENABLED_KEY) != null) {
			boolean hoverJavadocEnabled = getBoolean(configuration, JAVA_HOVER_JAVADOC_ENABLED_KEY, existing.hoverJavadocEnabled);
			prefs.setHoverJavadocEnabled(hoverJavadocEnabled);
		}

		if (getValue(configuration, RENAME_ENABLED_KEY) != null) {
			boolean renameEnabled = getBoolean(configuration, RENAME_ENABLED_KEY, existing.renameEnabled);
			prefs.setRenameEnabled(renameEnabled);
		}

		if (getValue(configuration, EXECUTE_COMMAND_ENABLED_KEY) != null) {
			boolean executeCommandEnable = getBoolean(configuration, EXECUTE_COMMAND_ENABLED_KEY, existing.executeCommandEnabled);
			prefs.setExecuteCommandEnabled(executeCommandEnable);
		}

		if (getValue(configuration, AUTOBUILD_ENABLED_KEY) != null) {
			boolean autobuildEnable = getBoolean(configuration, AUTOBUILD_ENABLED_KEY, existing.autobuildEnabled);
			prefs.setAutobuildEnabled(autobuildEnable);
		}

		if (getValue(configuration, COMPLETION_ENABLED_KEY) != null) {
			boolean completionEnable = getBoolean(configuration, COMPLETION_ENABLED_KEY, existing.completionEnabled);
			prefs.setCompletionEnabled(completionEnable);
		}

		if (getValue(configuration, POSTFIX_COMPLETION_KEY) != null) {
			boolean postfixEnabled = getBoolean(configuration, POSTFIX_COMPLETION_KEY, existing.postfixCompletionEnabled);
			prefs.setPostfixCompletionEnabled(postfixEnabled);
		}

		if (getValue(configuration, COMPLETION_MATCH_CASE_MODE_KEY) != null) {
			String completionMatchCaseMode = getString(configuration, COMPLETION_MATCH_CASE_MODE_KEY, null);
			prefs.setCompletionMatchCaseMode(CompletionMatchCaseMode.fromString(completionMatchCaseMode, existing.completionMatchCaseMode));
		}

		if (getValue(configuration, COMPLETION_LAZY_RESOLVE_TEXT_EDIT_ENABLED_KEY) != null) {
			boolean completionLazyResolveTextEditEnabled = getBoolean(configuration, COMPLETION_LAZY_RESOLVE_TEXT_EDIT_ENABLED_KEY, existing.completionLazyResolveTextEditEnabled);
			prefs.setCompletionLazyResolveTextEditEnabled(completionLazyResolveTextEditEnabled);
		}

		if (getValue(configuration, JAVA_COMPLETION_OVERWRITE_KEY) != null) {
			boolean completionOverwrite = getBoolean(configuration, JAVA_COMPLETION_OVERWRITE_KEY, existing.completionOverwrite);
			prefs.setCompletionOverwrite(completionOverwrite);
		}

		if (getValue(configuration, FOLDINGRANGE_ENABLED_KEY) != null) {
			boolean foldingRangeEnable = getBoolean(configuration, FOLDINGRANGE_ENABLED_KEY, existing.foldingRangeEnabled);
			prefs.setFoldingRangeEnabled(foldingRangeEnable);
		}

		if (getValue(configuration, SELECTIONRANGE_ENABLED_KEY) != null) {
			boolean selectionRangeEnabled = getBoolean(configuration, SELECTIONRANGE_ENABLED_KEY, existing.selectionRangeEnabled);
			prefs.setSelectionRangeEnabled(selectionRangeEnabled);
		}

		if (getValue(configuration, JAVA_COMPLETION_GUESS_METHOD_ARGUMENTS_KEY) != null) {
			Object guessMethodArguments = getValue(configuration, JAVA_COMPLETION_GUESS_METHOD_ARGUMENTS_KEY);
			if (guessMethodArguments instanceof Boolean b) {
				prefs.setGuessMethodArgumentsMode(b ? CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS :
						CompletionGuessMethodArgumentsMode.INSERT_PARAMETER_NAMES);
			} else {
				String guessMethodArgumentsMode = getString(configuration, JAVA_COMPLETION_GUESS_METHOD_ARGUMENTS_KEY, null);
				prefs.setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode.fromString(guessMethodArgumentsMode,
						existing.guessMethodArguments));
			}
		}

		if (getValue(configuration, JAVA_COMPLETION_COLLAPSE_KEY) != null) {
			boolean collapseCompletionItemsEnabled = getBoolean(configuration, JAVA_COMPLETION_COLLAPSE_KEY, existing.collapseCompletionItems);
			prefs.setCollapseCompletionItemsEnabled(collapseCompletionItemsEnabled);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEJAVA7OBJECTS) != null) {
			boolean hashCodeEqualsTemplateUseJava7Objects = getBoolean(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEJAVA7OBJECTS, existing.hashCodeEqualsTemplateUseJava7Objects);
			prefs.setHashCodeEqualsTemplateUseJava7Objects(hashCodeEqualsTemplateUseJava7Objects);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEINSTANCEOF) != null) {
			boolean hashCodeEqualsTemplateUseInstanceof = getBoolean(configuration, JAVA_CODEGENERATION_HASHCODEEQUALS_USEINSTANCEOF, existing.hashCodeEqualsTemplateUseInstanceof);
			prefs.setHashCodeEqualsTemplateUseInstanceof(hashCodeEqualsTemplateUseInstanceof);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_USEBLOCKS) != null) {
			boolean codeGenerationTemplateUseBlocks = getBoolean(configuration, JAVA_CODEGENERATION_USEBLOCKS, existing.codeGenerationTemplateUseBlocks);
			prefs.setCodeGenerationTemplateUseBlocks(codeGenerationTemplateUseBlocks);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_GENERATECOMMENTS) != null) {
			boolean codeGenerationTemplateGenerateComments = getBoolean(configuration, JAVA_CODEGENERATION_GENERATECOMMENTS, existing.codeGenerationTemplateGenerateComments);
			prefs.setCodeGenerationTemplateGenerateComments(codeGenerationTemplateGenerateComments);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_TOSTRING_TEMPLATE) != null) {
			String generateToStringTemplate = getString(configuration, JAVA_CODEGENERATION_TOSTRING_TEMPLATE);
			prefs.setGenerateToStringTemplate(generateToStringTemplate);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_TOSTRING_CODESTYLE) != null) {
			String generateToStringCodeStyle = getString(configuration, JAVA_CODEGENERATION_TOSTRING_CODESTYLE, existing.generateToStringCodeStyle);
			prefs.setGenerateToStringCodeStyle(generateToStringCodeStyle);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_TOSTRING_SKIPNULLVALUES) != null) {
			boolean generateToStringSkipNullValues = getBoolean(configuration, JAVA_CODEGENERATION_TOSTRING_SKIPNULLVALUES, existing.generateToStringSkipNullValues);
			prefs.setGenerateToStringSkipNullValues(generateToStringSkipNullValues);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_TOSTRING_LISTARRAYCONTENTS) != null) {
			boolean generateToStringListArrayContents = getBoolean(configuration, JAVA_CODEGENERATION_TOSTRING_LISTARRAYCONTENTS, existing.generateToStringListArrayContents);
			prefs.setGenerateToStringListArrayContents(generateToStringListArrayContents);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_TOSTRING_LIMITELEMENTS) != null) {
			int generateToStringLimitElements = getInt(configuration, JAVA_CODEGENERATION_TOSTRING_LIMITELEMENTS, existing.generateToStringLimitElements);
			prefs.setGenerateToStringLimitElements(generateToStringLimitElements);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_INSERTIONLOCATION) != null) {
			String insertionLocation = getString(configuration, JAVA_CODEGENERATION_INSERTIONLOCATION);
			prefs.setCodeGenerationInsertionLocation(insertionLocation);
		}

		if (getValue(configuration, JAVA_CODEGENERATION_ADD_FINAL_FOR_NEW_DECLARATION) != null) {
			String newFieldsFinal = getString(configuration, JAVA_CODEGENERATION_ADD_FINAL_FOR_NEW_DECLARATION);
			prefs.setCodeGenerationAddFinalForNewDeclaration(newFieldsFinal);
		}

		if (getValue(configuration, JAVA_IMPORT_EXCLUSIONS_KEY) != null) {
			List<String> javaImportExclusions = getList(configuration, JAVA_IMPORT_EXCLUSIONS_KEY, existing.javaImportExclusions);
			if (javaImportExclusions instanceof LinkedList) {
				prefs.setJavaImportExclusions(javaImportExclusions);
			} else {
				List<String> copy = new LinkedList<>(javaImportExclusions);
				prefs.setJavaImportExclusions(copy);
			}
		}

		if (getValue(configuration, JAVA_PROJECT_REFERENCED_LIBRARIES_KEY) != null) {
			Object referencedLibraries = getValue(configuration, JAVA_PROJECT_REFERENCED_LIBRARIES_KEY);
			if (referencedLibraries instanceof Map) {
				try {
					Map<String, Object> config = (Map<String, Object>) referencedLibraries;
					Set<String> include = new HashSet<>((List<String>) config.getOrDefault("include", new ArrayList<>()));
					Set<String> exclude = new HashSet<>((List<String>) config.getOrDefault("exclude", new ArrayList<>()));
					Map<String, String> sources = (Map<String, String>) config.getOrDefault("sources", new HashMap<>());
					prefs.setReferencedLibraries(new ReferencedLibraries(include, exclude, sources));
				} catch (Exception e) {
					prefs.setReferencedLibraries(existing.referencedLibraries);
				}
			} else { // referencedLibraries is a shortcut array to represent include patterns
				try {
					Set<String> include = new HashSet<>((List<String>) referencedLibraries);
					prefs.setReferencedLibraries(new ReferencedLibraries(include));
				} catch (Exception e) {
					prefs.setReferencedLibraries(existing.referencedLibraries);
				}
			}
		}

		if (getValue(configuration, JAVA_PROJECT_OUTPUT_PATH_KEY) != null) {
			String invisibleProjectOutputPath = getString(configuration, JAVA_PROJECT_OUTPUT_PATH_KEY, existing.invisibleProjectOutputPath);
			prefs.setInvisibleProjectOutputPath(invisibleProjectOutputPath);
		}

		if (getValue(configuration, JAVA_PROJECT_SOURCE_PATHS_KEY) != null) {
			List<String> invisibleProjectSourcePaths = getList(configuration, JAVA_PROJECT_SOURCE_PATHS_KEY, existing.invisibleProjectSourcePaths);
			prefs.setInvisibleProjectSourcePaths(invisibleProjectSourcePaths);
		}

		if (getValue(configuration, JAVA_COMPLETION_FAVORITE_MEMBERS_KEY) != null) {
			List<String> javaCompletionFavoriteMembers = getList(configuration, JAVA_COMPLETION_FAVORITE_MEMBERS_KEY, existing.javaCompletionFavoriteMembers);
			prefs.setJavaCompletionFavoriteMembers(javaCompletionFavoriteMembers);
		}

		if (getValue(configuration, JAVA_GRADLE_WRAPPER_SHA256_KEY) != null) {
			List<?> gradleWrapperList = getList(configuration, JAVA_GRADLE_WRAPPER_SHA256_KEY, JAVA_GRADLE_WRAPPER_SHA256_DEFAULT);
			prefs.setGradleWrapperList(gradleWrapperList);
		}

		if (getValue(configuration, MAVEN_USER_SETTINGS_KEY) != null) {
			String mavenUserSettings = getString(configuration, MAVEN_USER_SETTINGS_KEY, existing.mavenUserSettings);
			prefs.setMavenUserSettings(mavenUserSettings);
		}

		if (getValue(configuration, MAVEN_GLOBAL_SETTINGS_KEY) != null) {
			String mavenGlobalSettings = getString(configuration, MAVEN_GLOBAL_SETTINGS_KEY, existing.mavenGlobalSettings);
			prefs.setMavenGlobalSettings(mavenGlobalSettings);
		}

		if (getValue(configuration, MAVEN_LIFECYCLE_MAPPINGS_KEY) != null) {
			String mavenLifecycleMappings = getString(configuration, MAVEN_LIFECYCLE_MAPPINGS_KEY, existing.mavenLifecycleMappings);
			prefs.setMavenLifecycleMappings(mavenLifecycleMappings);
		}

		if (getValue(configuration, MAVEN_NOT_COVERED_PLUGIN_EXECUTION_SEVERITY) != null) {
			String mavenNotCoveredPluginExecution = getString(configuration, MAVEN_NOT_COVERED_PLUGIN_EXECUTION_SEVERITY, existing.mavenNotCoveredPluginExecutionSeverity);
			prefs.setMavenNotCoveredPluginExecutionSeverity(mavenNotCoveredPluginExecution);
		}

		if (getValue(configuration, MAVEN_DEFAULT_MOJO_EXECUTION_ACTION) != null) {
			String mavenDefaultMojoExecution = getString(configuration, MAVEN_DEFAULT_MOJO_EXECUTION_ACTION, existing.mavenDefaultMojoExecutionAction);
			prefs.setMavenDefaultMojoExecutionAction(mavenDefaultMojoExecution);
		}

		if (getValue(configuration, MEMBER_SORT_ORDER) != null) {
			String sortOrder = getString(configuration, MEMBER_SORT_ORDER, null);
			prefs.setMembersSortOrder(sortOrder);
		}

		if (getValue(configuration, PREFERRED_CONTENT_PROVIDER_KEY) != null) {
			List<String> preferredContentProviders = getList(configuration, PREFERRED_CONTENT_PROVIDER_KEY);
			prefs.setPreferredContentProviderIds(preferredContentProviders);
		}

		if (getValue(configuration, JAVA_HOME) != null) {
			String javaHome = getString(configuration, JAVA_HOME);
			prefs.setJavaHome(javaHome);
		}

		if (getValue(configuration, JAVA_FORMATTER_URL) != null) {
			String formatterUrl = getString(configuration, JAVA_FORMATTER_URL);
			prefs.setFormatterUrl(formatterUrl);
		}

		if (getValue(configuration, JAVA_SETTINGS_URL) != null) {
			String settingsUrl = getString(configuration, JAVA_SETTINGS_URL);
			prefs.setSettingsUrl(settingsUrl);
		}

		if (getValue(configuration, JAVA_RESOURCE_FILTERS) != null) {
			List<String> resourceFilters = getList(configuration, JAVA_RESOURCE_FILTERS, existing.resourceFilters);
			prefs.setResourceFilters(resourceFilters);
		}

		if (getValue(configuration, JAVA_FORMATTER_PROFILE_NAME) != null) {
			String formatterProfileName = getString(configuration, JAVA_FORMATTER_PROFILE_NAME);
			prefs.setFormatterProfileName(formatterProfileName);
		}

		if (getValue(configuration, JAVA_FORMAT_COMMENTS) != null) {
			boolean javaFormatComments = getBoolean(configuration, JAVA_FORMAT_COMMENTS, existing.javaFormatComments);
			prefs.setJavaFormatComments(javaFormatComments);
		}

		if (getValue(configuration, JAVA_IMPORT_ORDER_KEY) != null) {
			List<String> javaImportOrder = getList(configuration, JAVA_IMPORT_ORDER_KEY, existing.importOrder);
			prefs.setImportOrder(javaImportOrder);
		}

		if (getValue(configuration, JAVA_COMPLETION_FILTERED_TYPES_KEY) != null) {
			List<String> javaFilteredTypes = getList(configuration, JAVA_COMPLETION_FILTERED_TYPES_KEY, existing.filteredTypes);
			prefs.setFilteredTypes(javaFilteredTypes);
		}

		if (getValue(configuration, JAVA_MAX_CONCURRENT_BUILDS) != null) {
			int maxConcurrentBuilds = getInt(configuration, JAVA_MAX_CONCURRENT_BUILDS, existing.parallelBuildsCount);
			maxConcurrentBuilds = maxConcurrentBuilds >= 1 ? maxConcurrentBuilds : 1;
			prefs.setMaxBuildCount(maxConcurrentBuilds);
		}

		if (getValue(configuration, JAVA_COMPLETION_MAX_RESULTS_KEY) != null) {
			int maxCompletions = getInt(configuration, JAVA_COMPLETION_MAX_RESULTS_KEY, existing.maxCompletionResults);
			prefs.setMaxCompletionResults(maxCompletions);
		}

		if (getValue(configuration, IMPORTS_ONDEMANDTHRESHOLD) != null) {
			int onDemandThreshold = getInt(configuration, IMPORTS_ONDEMANDTHRESHOLD, existing.importOnDemandThreshold);
			prefs.setImportOnDemandThreshold(onDemandThreshold);
		}

		if (getValue(configuration, IMPORTS_STATIC_ONDEMANDTHRESHOLD) != null) {
			int staticOnDemandThreshold = getInt(configuration, IMPORTS_STATIC_ONDEMANDTHRESHOLD, existing.staticImportOnDemandThreshold);
			prefs.setStaticImportOnDemandThreshold(staticOnDemandThreshold);
		}

		if (getValue(configuration, JAVA_CONFIGURATION_RUNTIMES) != null) {
			List<?> runtimeList = getList(configuration, JAVA_CONFIGURATION_RUNTIMES, JAVA_CONFIGURATION_RUNTIMES_DEFAULT);
			Set<RuntimeEnvironment> runtimes = new HashSet<>();
			boolean[] hasDefault = { false };
			for (Object object : runtimeList) {
				if (object instanceof Map<?, ?> map) {
					RuntimeEnvironment runtime = new RuntimeEnvironment();
					map.forEach((k, v) -> {
						if (k instanceof String key) {
							switch (key) {
								case "name":
									if (v instanceof String value) {
										runtime.setName(value);
									}
									break;
								case "path":
									if (v instanceof String value) {
										runtime.setPath(ResourceUtils.expandPath(value));
									}
									break;
								case "javadoc":
									if (v instanceof String value) {
										runtime.setJavadoc(ResourceUtils.expandPath(value));
									}
									break;
								case "sources":
									if (v instanceof String value) {
										runtime.setSources(ResourceUtils.expandPath(value));
									}
									break;
								case "default":
									if (!hasDefault[0]) {
										if (v instanceof Boolean bool) {
											runtime.setDefault(bool);
										}
										hasDefault[0] = true;
									}
									break;
								default:
									break;
							}
						}
					});
					if (!runtimes.contains(runtime)) {
						if (runtime.isValid()) {
							runtimes.add(runtime);
						} else {
							JavaLanguageServerPlugin.logInfo("Runtime " + runtime + " is not valid.");
						}
					} else {
						JavaLanguageServerPlugin.logInfo("Multiple runtimes with name " + runtime.getName());
					}
				}
			}
			prefs.setRuntimes(runtimes);
		}

		if (getValue(configuration, JAVA_TEMPLATES_FILEHEADER) != null) {
			List<String> fileHeader = getList(configuration, JAVA_TEMPLATES_FILEHEADER);
			prefs.setFileHeaderTemplate(fileHeader);
		}

		if (getValue(configuration, JAVA_TEMPLATES_TYPECOMMENT) != null) {
			List<String> typeComment = getList(configuration, JAVA_TEMPLATES_TYPECOMMENT);
			prefs.setTypeCommentTemplate(typeComment);
		}

		if (getValue(configuration, JAVA_REFERENCES_INCLUDE_ACCESSORS) != null) {
			boolean includeAccessors = getBoolean(configuration, JAVA_REFERENCES_INCLUDE_ACCESSORS, existing.includeAccessors);
			prefs.setIncludeAccessors(includeAccessors);
		}

		if (getValue(configuration, JAVA_EDIT_SMARTSEMICOLON_DETECTION) != null) {
			boolean smartSemicolonDetection = getBoolean(configuration, JAVA_EDIT_SMARTSEMICOLON_DETECTION, existing.smartSemicolonDetection);
			prefs.setSmartSemicolonDetection(smartSemicolonDetection);
		}

		if (getValue(configuration, JAVA_REFERENCES_INCLUDE_DECOMPILED_SOURCES) != null) {
			boolean includeDecompiledSources = getBoolean(configuration, JAVA_REFERENCES_INCLUDE_DECOMPILED_SOURCES, existing.includeDecompiledSources);
			prefs.setIncludeDecompiledSources(includeDecompiledSources);
		}

		if (getValue(configuration, JAVA_SYMBOLS_INCLUDE_SOURCE_METHOD_DECLARATIONS) != null) {
			boolean includeSourceMethodDeclarations = getBoolean(configuration, JAVA_SYMBOLS_INCLUDE_SOURCE_METHOD_DECLARATIONS, existing.includeSourceMethodDeclarations);
			prefs.setIncludeSourceMethodDeclarations(includeSourceMethodDeclarations);
		}

		if (getValue(configuration, JAVA_INLAYHINTS_PARAMETERNAMES_ENABLED) != null) {
			String inlayHintsParameterMode = getString(configuration, JAVA_INLAYHINTS_PARAMETERNAMES_ENABLED, null);
			prefs.setInlayHintsParameterMode(InlayHintsParameterMode.fromString(inlayHintsParameterMode, existing.inlayHintsParameterMode));
		}

		if (getValue(configuration, JAVA_INLAYHINTS_PARAMETERNAMES_SUPPRESS_WHEN_SAME_NAME_NUMBERED) != null) {
			boolean inlayHintsSuppressedWhenSameNameNumberedParameter = getBoolean(configuration, JAVA_INLAYHINTS_PARAMETERNAMES_SUPPRESS_WHEN_SAME_NAME_NUMBERED, existing.inlayHintsSuppressedWhenSameNameNumberedParameter);
			prefs.setInlayHintsSuppressedWhenSameNameNumberedParameter(inlayHintsSuppressedWhenSameNameNumberedParameter);
		}

		if (getValue(configuration, JAVA_INLAYHINTS_PARAMETERNAMES_EXCLUSIONS) != null) {
			List<String> inlayHintsExclusionList = getList(configuration, JAVA_INLAYHINTS_PARAMETERNAMES_EXCLUSIONS, existing.inlayHintsExclusionList);
			prefs.setInlayHintsExclusionList(inlayHintsExclusionList);
		}

		if (getValue(configuration, JAVA_INLAYHINTS_VARIABLETYPES_ENABLED) != null) {
			boolean inlayHintsVariableTypesEnabled = getBoolean(configuration, JAVA_INLAYHINTS_VARIABLETYPES_ENABLED, existing.inlayHintsVariableTypesEnabled);
			prefs.setInlayHintsVariableTypesEnabled(inlayHintsVariableTypesEnabled);
		}

		if (getValue(configuration, JAVA_INLAYHINTS_PARAMETERTYPES_ENABLED) != null) {
			boolean inlayHintsParameterTypesEnabled = getBoolean(configuration, JAVA_INLAYHINTS_PARAMETERTYPES_ENABLED, existing.inlayHintsParameterTypesEnabled);
			prefs.setInlayHintsParameterTypesEnabled(inlayHintsParameterTypesEnabled);
		}

		if (getValue(configuration, JAVA_PROJECT_ENCODING) != null) {
			String projectEncoding = getString(configuration, JAVA_PROJECT_ENCODING, null);
			prefs.setProjectEncoding(ProjectEncodingMode.fromString(projectEncoding, existing.projectEncoding));
		}

		if (getValue(configuration, JAVA_CODEACTION_SORTMEMBER_AVOIDVOLATILECHANGES) != null) {
			boolean avoidVolatileChanges = getBoolean(configuration, JAVA_CODEACTION_SORTMEMBER_AVOIDVOLATILECHANGES, existing.avoidVolatileChanges);
			prefs.setAvoidVolatileChanges(avoidVolatileChanges);
		}

		if (getValue(configuration, JAVA_JDT_LS_PROTOBUF_SUPPORT_ENABLED) != null) {
			boolean protobufSupported = getBoolean(configuration, JAVA_JDT_LS_PROTOBUF_SUPPORT_ENABLED, existing.protobufSupportEnabled);
			prefs.setProtobufSupportEnabled(protobufSupported);
		}

		if (getValue(configuration, JAVA_JDT_LS_ASPECTJ_SUPPORT_ENABLED) != null) {
			boolean aspectjSupported = getBoolean(configuration, JAVA_JDT_LS_ASPECTJ_SUPPORT_ENABLED, existing.aspectjSupportEnabled);
			prefs.setAspectjSupportEnabled(aspectjSupported);
		}

		if (getValue(configuration, JAVA_JDT_LS_JAVAC_ENABLED) != null) {
			boolean javacEnabled = getBoolean(configuration, JAVA_JDT_LS_JAVAC_ENABLED, existing.javacEnabled);
			prefs.setJavacEnabled(javacEnabled);
		}

		if (getValue(configuration, JAVA_JDT_LS_ANDROID_SUPPORT_ENABLED) != null) {
			boolean androidSupported = getBoolean(configuration, JAVA_JDT_LS_ANDROID_SUPPORT_ENABLED, existing.androidSupportEnabled);
			prefs.setAndroidSupportEnabled(androidSupported);
		}

		if (getValue(configuration, JAVA_COMPILE_NULLANALYSIS_NONNULL) != null) {
			List<String> nonnullTypes = getList(configuration, JAVA_COMPILE_NULLANALYSIS_NONNULL, existing.nonnullTypes);
			prefs.setNonnullTypes(nonnullTypes);
		}

		if (getValue(configuration, JAVA_COMPILE_NULLANALYSIS_NULLABLE) != null) {
			List<String> nullableTypes = getList(configuration, JAVA_COMPILE_NULLANALYSIS_NULLABLE, existing.nullableTypes);
			prefs.setNullableTypes(nullableTypes);
		}

		if (getValue(configuration, JAVA_COMPILE_NULLANALYSIS_NONNULLBYDEFAULT) != null) {
			List<String> nonullbydefaultTypes = getList(configuration, JAVA_COMPILE_NULLANALYSIS_NONNULLBYDEFAULT, existing.nonnullbydefaultTypes);
			prefs.setNonnullbydefaultTypes(nonullbydefaultTypes);
		}

		if (getValue(configuration, JAVA_COMPILE_NULLANALYSIS_MODE) != null) {
			String nullAnalysisMode = getString(configuration, JAVA_COMPILE_NULLANALYSIS_MODE, null);
			prefs.setNullAnalysisMode(FeatureStatus.fromString(nullAnalysisMode, existing.nullAnalysisMode));
		}

		if (getValue(configuration, JAVA_CLEANUPS_ACTIONS_ON_SAVE_DEPRECATED) != null || getValue(configuration, JAVA_CLEANUPS_ACTIONS) != null) {
			List<String> cleanupActionsTemp = getList(configuration, JAVA_CLEANUPS_ACTIONS_ON_SAVE_DEPRECATED, Collections.emptyList());
			List<String> cleanupActions = getList(configuration, JAVA_CLEANUPS_ACTIONS, Collections.emptyList());
			if(cleanupActions.isEmpty() && !cleanupActionsTemp.isEmpty()) {
				cleanupActions = cleanupActionsTemp;
			}
			prefs.setCleanUpActions(cleanupActions);
		}

		if (getValue(configuration, JAVA_CLEANUPS_ACTIONS_ON_SAVE_CLEANUP) != null) {
			boolean cleanUpActionsOnSaveEnabled = getBoolean(configuration, JAVA_CLEANUPS_ACTIONS_ON_SAVE_CLEANUP, existing.cleanUpActionsOnSaveEnabled);
			prefs.setCleanUpActionsOnSaveEnabled(cleanUpActionsOnSaveEnabled);
		}

		if (getValue(configuration, JAVA_REFACTORING_EXTRACT_INTERFACE_REPLACE) != null) {
			boolean extractInterfaceReplaceEnabled = getBoolean(configuration, JAVA_REFACTORING_EXTRACT_INTERFACE_REPLACE, existing.extractInterfaceReplaceEnabled);
			prefs.setExtractInterfaceReplaceEnabled(extractInterfaceReplaceEnabled);
		}

		if (getValue(configuration, JAVA_TELEMETRY_ENABLED_KEY) != null) {
			boolean telemetryEnabled = getBoolean(configuration, JAVA_TELEMETRY_ENABLED_KEY, existing.telemetryEnabled);
			prefs.setTelemetryEnabled(telemetryEnabled);
		}

		if (getValue(configuration, JAVA_EDIT_VALIDATE_ALL_OPEN_BUFFERS_ON_CHANGES) != null) {
			boolean validateAllOpenBuffers = getBoolean(configuration, JAVA_EDIT_VALIDATE_ALL_OPEN_BUFFERS_ON_CHANGES, existing.validateAllOpenBuffersOnChanges);
			prefs.setValidateAllOpenBuffersOnChanges(validateAllOpenBuffers);
		}

		if (getValue(configuration, CHAIN_COMPLETION_KEY) != null) {
			boolean chainCompletionEnabled = getBoolean(configuration, CHAIN_COMPLETION_KEY, existing.chainCompletionEnabled);
			prefs.setChainCompletionEnabled(chainCompletionEnabled);
		}

		if (getValue(configuration, JAVA_DIAGNOSTIC_FILER) != null) {
			List<String> diagnosticFilter = getList(configuration, JAVA_DIAGNOSTIC_FILER, existing.diagnosticFilter);
			prefs.setDiagnosticFilter(diagnosticFilter);
		}

		if (getValue(configuration, JAVA_CONFIGURATION_ASSOCIATIONS) != null) {
			Object object = getValue(configuration, JAVA_CONFIGURATION_ASSOCIATIONS);
			Set<String> associations = new HashSet<>();
			if (object instanceof Map map) {
				try {
					Map<String, String> element = map;
					element.forEach((k, v) -> {
						// Java LS only support a small subset of the glob pattern syntax (*.xxx)
						if ("java".equals(v) && validateFilePattern(k)) {
							associations.add(k.substring(2));
						}
					});
				} catch (Exception e) {
					JavaLanguageServerPlugin.logException(e);
				}
			}
			prefs.setFilesAssociations(new ArrayList<>(associations));
		}

		if (getValue(configuration, JAVA_SEARCH_SCOPE) != null) {
			String searchScope = getString(configuration, JAVA_SEARCH_SCOPE, null);
			prefs.setSearchScope(SearchScope.fromString(searchScope, existing.searchScope));
		}

		return prefs;
	}

	public void setInlayHintsVariableTypesEnabled(boolean inlayHintsVariableTypesEnabled) {
		this.inlayHintsVariableTypesEnabled = inlayHintsVariableTypesEnabled;
	}

	public void setInlayHintsParameterTypesEnabled(boolean inlayHintsParameterTypesEnabled) {
		this.inlayHintsParameterTypesEnabled = inlayHintsParameterTypesEnabled;
	}

	private static boolean validateFilePattern(String filename) {
		if (filename != null && filename.startsWith("*.") && filename.length() > 2) {
			String ext = filename.substring(2);
			if (!ext.contains("?") && !ext.contains("*")) {
				return true;
			}
		}
		JavaLanguageServerPlugin.logInfo("Pattern '" + filename + "' is not supported.");
		return false;
	}

	/**
	 * Sets the new value of the enabled clean ups.
	 *
	 * @param enabledCleanUps
	 *            the new list of enabled clean ups
	 */
	public void setCleanUpActions(List<String> enabledCleanUps) {
		this.cleanUpActions = enabledCleanUps;
	}

	public void setCleanUpActionsOnSaveEnabled(boolean cleanup) {
		this.cleanUpActionsOnSaveEnabled = cleanup;
	}

	public Preferences setJavaHome(String javaHome) {
		this.javaHome = javaHome;
		return this;
	}

	public Preferences setGradleVersion(String gradleVersion) {
		this.gradleVersion = (gradleVersion == null || gradleVersion.isEmpty()) ? null : gradleVersion;
		return this;
	}

	public Preferences setGradleArguments(List<String> arguments) {
		this.gradleArguments = arguments == null ? new ArrayList<>() : arguments;
		return this;
	}

	public Preferences setGradleJvmArguments(List<String> jvmArguments) {
		this.gradleJvmArguments = jvmArguments == null ? new ArrayList<>() : jvmArguments;
		return this;
	}

	public Preferences setGradleHome(String gradleHome) {
		this.gradleHome = ResourceUtils.expandPath(gradleHome);
		return this;
	}

	public Preferences setGradleJavaHome(String gradleJavaHome) {
		this.gradleJavaHome = ResourceUtils.expandPath(gradleJavaHome);
		return this;
	}

	public Preferences setGradleUserHome(String gradleUserHome) {
		this.gradleUserHome = ResourceUtils.expandPath(gradleUserHome);
		return this;
	}

	public Preferences setFormatterUrl(String formatterUrl) {
		this.formatterUrl = ResourceUtils.expandPath(formatterUrl);
		return this;
	}

	public Preferences setSettingsUrl(String settingsUrl) {
		this.settingsUrl = ResourceUtils.expandPath(settingsUrl);
		return this;
	}

	public Preferences setResourceFilters(List<String> resourceFilters) {
		if (resourceFilters != null) {
			this.resourceFilters = resourceFilters.stream().filter((resource) -> {
				try {
					Pattern.compile(resource);
					return true;
				} catch (Exception e) {
					JavaLanguageServerPlugin.logInfo("Invalid preference: " + Preferences.JAVA_RESOURCE_FILTERS + "=" + resource);
					return false;
				}
			}).collect(Collectors.toList());
		} else {
			this.resourceFilters = Collections.emptyList();
		}
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
		IEclipsePreferences prefs = DefaultScope.INSTANCE.getNode(JavaManipulation.getPreferenceNodeId());
		String value = String.join(";", JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers());
		prefs.put(JavaManipulationPlugin.CODEASSIST_FAVORITE_STATIC_MEMBERS, value);
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

	public Preferences setImportGradleOfflineEnabled(boolean enabled) {
		this.importGradleOfflineEnabled = enabled;
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode("org.eclipse.buildship.core");
		boolean offlineMode = prefs.getBoolean(GRADLE_OFFLINE_MODE, false);
		if (offlineMode != enabled) {
			prefs.putBoolean(GRADLE_OFFLINE_MODE, enabled);
		}
		return this;
	}

	public Preferences setGradleWrapperEnabled(boolean enabled) {
		this.gradleWrapperEnabled = enabled;
		return this;
	}

	public Preferences setImportMavenEnabled(boolean enabled) {
		this.importMavenEnabled = enabled;
		return this;
	}

	public Preferences setMavenOffline(boolean enabled) {
		this.mavenOffline = enabled;
		return this;
	}

	public Preferences setMavenDisableTestClasspathFlag(boolean enabled) {
		this.mavenDisableTestClasspathFlag = enabled;
		return this;
	}

	public Preferences setMavenDownloadSources(boolean enabled) {
		this.mavenDownloadSources = enabled;
		return this;
	}

	public Preferences setMavenUpdateSnapshots(boolean enabled) {
		this.mavenUpdateSnapshots = enabled;
		return this;
	}

	private Preferences setSignatureHelpEnabled(boolean enabled) {
		this.signatureHelpEnabled = enabled;
		return this;
	}

	public void setSignatureHelpDescriptionEnabled(boolean signatureHelpDescriptionEnabled) {
		this.signatureHelpDescriptionEnabled = signatureHelpDescriptionEnabled;
	}

	private Preferences setHoverJavadocEnabled(boolean enabled) {
		this.hoverJavadocEnabled = enabled;
		return this;
	}

	private Preferences setImplementationCodelens(String implementationCodeLensOption) {
		this.implementationsCodeLens = implementationCodeLensOption;
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

	public boolean isPostfixCompletionEnabled() {
		return postfixCompletionEnabled;
	}

	public void setPostfixCompletionEnabled(boolean postfixCompletionEnabled) {
		this.postfixCompletionEnabled = postfixCompletionEnabled;
	}

	public CompletionMatchCaseMode getCompletionMatchCaseMode() {
		return completionMatchCaseMode;
	}

	public void setCompletionMatchCaseMode(CompletionMatchCaseMode completionMatchCaseMode) {
		this.completionMatchCaseMode = completionMatchCaseMode;
	}

	public boolean isCompletionLazyResolveTextEditEnabled() {
		return completionLazyResolveTextEditEnabled;
	}

	public void setCompletionLazyResolveTextEditEnabled(boolean completionLazyResolveTextEditEnabled) {
		this.completionLazyResolveTextEditEnabled = completionLazyResolveTextEditEnabled;
	}

	public Preferences setCompletionOverwrite(boolean completionOverwrite) {
		this.completionOverwrite = completionOverwrite;
		return this;
	}

	public Preferences setFoldingRangeEnabled(boolean enabled) {
		this.foldingRangeEnabled = enabled;
		return this;
	}

	public Preferences setSelectionRangeEnabled(boolean enabled) {
		this.selectionRangeEnabled = enabled;
		return this;
	}

	public Preferences setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode guessMethodArguments) {
		this.guessMethodArguments = guessMethodArguments;
		return this;
	}

	public Preferences setCollapseCompletionItemsEnabled(boolean enabled) {
		this.collapseCompletionItems = enabled;
		return this;
	}

	public Preferences setJavaFormatEnabled(boolean enabled) {
		this.javaFormatEnabled = enabled;
		return this;
	}

	public Preferences setJavaQuickFixShowAt(String value) {
		this.javaQuickFixShowAt = value;
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

	public Preferences setCodeGenerationTemplateUseBlocks(boolean codeGenerationTemplateUseBlocks) {
		this.codeGenerationTemplateUseBlocks = codeGenerationTemplateUseBlocks;
		return this;
	}

	public Preferences setCodeGenerationTemplateGenerateComments(boolean codeGenerationTemplateGenerateComments) {
		this.codeGenerationTemplateGenerateComments = codeGenerationTemplateGenerateComments;
		return this;
	}

	public Preferences setGenerateToStringTemplate(String generateToStringTemplate) {
		this.generateToStringTemplate = generateToStringTemplate;
		return this;
	}

	public Preferences setGenerateToStringCodeStyle(String generateToStringCodeStyle) {
		this.generateToStringCodeStyle = generateToStringCodeStyle;
		return this;
	}

	public Preferences setGenerateToStringSkipNullValues(boolean generateToStringSkipNullValues) {
		this.generateToStringSkipNullValues = generateToStringSkipNullValues;
		return this;
	}

	public Preferences setGenerateToStringListArrayContents(boolean generateToStringListArrayContents) {
		this.generateToStringListArrayContents = generateToStringListArrayContents;
		return this;
	}

	public Preferences setGenerateToStringLimitElements(int generateToStringLimitElements) {
		this.generateToStringLimitElements = generateToStringLimitElements;
		return this;
	}

	public Preferences setUpdateBuildConfigurationStatus(FeatureStatus status) {
		this.updateBuildConfigurationStatus = status;
		return this;
	}

	public Preferences setImportOrder(List<String> importOrder) {
		this.importOrder = (importOrder == null || importOrder.size() == 0) ? JAVA_IMPORT_ORDER_DEFAULT : importOrder;
		IEclipsePreferences pref = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		pref.put(CodeStyleConfiguration.ORGIMPORTS_IMPORTORDER, String.join(";", importOrder));
		return this;
	}

	public Preferences setFilteredTypes(List<String> filteredTypes) {
		this.filteredTypes = (filteredTypes == null) ? Collections.emptyList() : filteredTypes;
		IEclipsePreferences pref = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		pref.put(PreferenceConstantsCore.TYPEFILTER_ENABLED, String.join(";", filteredTypes));
		JavaLanguageServerPlugin.getInstance().getTypeFilter().dispose();
		return this;
	}

	public void setTelemetryEnabled(boolean telemetry) {
		this.telemetryEnabled = telemetry;
	}

	public Preferences setMaxBuildCount(int maxConcurrentBuilds) {
		this.parallelBuildsCount = maxConcurrentBuilds;
		return this;
	}

	public FeatureStatus getUpdateBuildConfigurationStatus() {
		return updateBuildConfigurationStatus;
	}

	public List<String> getJavaImportExclusions() {
		return javaImportExclusions;
	}

	public String[] getJavaCompletionFavoriteMembers() {
		Set<String> favorites = new LinkedHashSet<>(javaCompletionFavoriteMembers);
		favorites.addAll(DISCOVERED_STATIC_IMPORTS);
		return favorites.toArray(new String[0]);
	}

	public String getJavaHome() {
		return javaHome;
	}

	public String getGradleVersion() {
		return gradleVersion;
	}

	public List<String> getGradleArguments() {
		return gradleArguments == null ? new ArrayList<>() : gradleArguments;
	}

	public List<String> getGradleJvmArguments() {
		return gradleJvmArguments == null ? new ArrayList<>() : gradleJvmArguments;
	}

	public String getGradleHome() {
		return gradleHome;
	}

	public String getGradleJavaHome() {
		return gradleJavaHome;
	}

	public String getGradleUserHome() {
		return gradleUserHome;
	}

	public boolean isGradleAnnotationProcessingEnabled() {
		return gradleAnnotationProcessingEnabled;
	}

	public void setGradleAnnotationProcessingEnabled(boolean gradleAnnotationProcessingEnabled) {
		this.gradleAnnotationProcessingEnabled = gradleAnnotationProcessingEnabled;
	}

	public String getFormatterUrl() {
		return formatterUrl;
	}

	public URI getFormatterAsURI() {
		return asURI(formatterUrl);
	}

	public String getSettingsUrl() {
		return settingsUrl;
	}

	public URI getSettingsAsURI() {
		return asURI(settingsUrl);
	}

	private URI asURI(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		URI uri = null;
		try {
			uri = new URI(ResourceUtils.toClientUri(url));
			if (!uri.isAbsolute()) {
				uri = getURI(url);
			}
		} catch (URISyntaxException e1) {
			uri = getURI(url);
		}
		if (uri == null) {
			JavaLanguageServerPlugin.logInfo("Cannot resolve '" + url + "'.");
		}
		return uri;
	}

	private URI getURI(String path) {
		File file = findFile(path);
		if (file != null && file.isFile()) {
			return file.toURI();
		}
		return null;
	}

	private File findFile(String path) {
		File file = new File(path);
		if (file.exists()) {
			return file;
		}
		Collection<IPath> rootPaths = getRootPaths();
		if (rootPaths != null) {
			for (IPath rootPath : rootPaths) {
				File f = new File(rootPath.toOSString(), path);
				if (f.isFile()) {
					return f;
				}
			}
		}
		return null;
	}

	public List<String> getResourceFilters() {
		return resourceFilters;
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

	public boolean isImportGradleOfflineEnabled() {
		return importGradleOfflineEnabled;
	}

	public boolean isGradleWrapperEnabled() {
		return gradleWrapperEnabled;
	}

	public boolean isImportMavenEnabled() {
		return importMavenEnabled;
	}

	public boolean isMavenOffline() {
		return mavenOffline;
	}

	public boolean isMavenDisableTestClasspathFlag() {
		return mavenDisableTestClasspathFlag;
	}

	public boolean isMavenDownloadSources() {
		return mavenDownloadSources;
	}

	public boolean isMavenUpdateSnapshots() {
		return mavenUpdateSnapshots;
	}

	public String getImplementationsCodeLens() {
		return implementationsCodeLens;
	}

	public boolean isCodeLensEnabled() {
		return referencesCodeLensEnabled || !implementationsCodeLens.equals("none");
	}

	public boolean isJavaFormatEnabled() {
		return javaFormatEnabled;
	}

	public String getJavaQuickFixShowAt() {
		return javaQuickFixShowAt;
	}

	public boolean isJavaQuickFixShowAtLine() {
		return LINE.equals(javaQuickFixShowAt);
	}

	public boolean isJavaSaveActionsOrganizeImportsEnabled() {
		return javaSaveActionsOrganizeImportsEnabled;
	}

	public boolean isSignatureHelpEnabled() {
		return signatureHelpEnabled;
	}

	public boolean isSignatureHelpDescriptionEnabled() {
		return signatureHelpDescriptionEnabled;
	}

	public boolean isHoverJavadocEnabled() {
		return hoverJavadocEnabled;
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

	public boolean isSelectionRangeEnabled() {
		return selectionRangeEnabled;
	}

	public CompletionGuessMethodArgumentsMode getGuessMethodArgumentsMode() {
		return guessMethodArguments;
	}

	public boolean isCollapseCompletionItemsEnabled() {
		return collapseCompletionItems;
	}

	public boolean isHashCodeEqualsTemplateUseJava7Objects() {
		return hashCodeEqualsTemplateUseJava7Objects;
	}

	public boolean isHashCodeEqualsTemplateUseInstanceof() {
		return hashCodeEqualsTemplateUseInstanceof;
	}

	public boolean isCodeGenerationTemplateUseBlocks() {
		return codeGenerationTemplateUseBlocks;
	}

	public boolean isCodeGenerationTemplateGenerateComments() {
		return codeGenerationTemplateGenerateComments;
	}

	public String getGenerateToStringTemplate() {
		return generateToStringTemplate;
	}

	public String getGenerateToStringCodeStyle() {
		return generateToStringCodeStyle;
	}

	public boolean isGenerateToStringSkipNullValues() {
		return generateToStringSkipNullValues;
	}

	public boolean isGenerateToStringListArrayContents() {
		return generateToStringListArrayContents;
	}

	public int getGenerateToStringLimitElements() {
		return generateToStringLimitElements;
	}

	public String getCodeGenerationAddFinalForNewDeclaration() {
		return codeGenerationAddFinalForNewDeclaration;
	}

	public void setCodeGenerationAddFinalForNewDeclaration(String declValue) {
		this.codeGenerationAddFinalForNewDeclaration = declValue;
	}

	public String getCodeGenerationInsertionLocation() {
		return codeGenerationInsertionLocation;
	}

	public void setCodeGenerationInsertionLocation(String insertionLocation) {
		this.codeGenerationInsertionLocation = insertionLocation;
	}

	public Preferences setMavenUserSettings(String mavenUserSettings) {
		this.mavenUserSettings = ResourceUtils.expandPath(mavenUserSettings);
		return this;
	}

	public String getMavenUserSettings() {
		return mavenUserSettings;
	}

	public Preferences setMavenGlobalSettings(String mavenGlobalSettings) {
		this.mavenGlobalSettings = ResourceUtils.expandPath(mavenGlobalSettings);
		return this;
	}

	public String getMavenGlobalSettings() {
		return mavenGlobalSettings;
	}

	public Preferences setMavenLifecycleMappings(String mavenLifecycleMappings) {
		if (mavenLifecycleMappings == null || mavenLifecycleMappings.isBlank()) {
			Bundle bundle = Platform.getBundle("org.eclipse.m2e.core");
			if (bundle != null) {
				IPath stateLocation = Platform.getStateLocation(bundle);
				mavenLifecycleMappings = stateLocation.append(LIFECYCLE_MAPPING_METADATA_SOURCE_NAME).toString();
			}
		}
		this.mavenLifecycleMappings = ResourceUtils.expandPath(mavenLifecycleMappings);
		return this;
	}

	public String getMavenLifecycleMappings() {
		if (mavenLifecycleMappings == null) {
			setMavenLifecycleMappings(null);
		}
		return mavenLifecycleMappings;
	}

	public String getMavenNotCoveredPluginExecutionSeverity() {
		return mavenNotCoveredPluginExecutionSeverity;
	}

	public Preferences setMavenNotCoveredPluginExecutionSeverity(String mavenNotCoveredPluginExecutionSeverity) {
		this.mavenNotCoveredPluginExecutionSeverity = mavenNotCoveredPluginExecutionSeverity;
		return this;
	}

	public String getMavenDefaultMojoExecutionAction() {
		return mavenDefaultMojoExecutionAction;
	}

	public Preferences setMavenDefaultMojoExecutionAction(String mavenDefaultMojoExecutionAction) {
		if (mavenDefaultMojoExecutionAction == null) {
			mavenDefaultMojoExecutionAction = IGNORE;
		}
		switch (mavenDefaultMojoExecutionAction) {
			case IGNORE:
			case "execute":
			case "warn":
			case "error":
				break;
			default:
				mavenDefaultMojoExecutionAction = IGNORE;
				break;
		}
		this.mavenDefaultMojoExecutionAction = mavenDefaultMojoExecutionAction;
		return this;
	}

	public String[] getImportOrder() {
		return this.importOrder == null ? new String[0] : this.importOrder.toArray(new String[importOrder.size()]);
	}

	public String[] getFilteredTypes() {
		return this.filteredTypes == null ? new String[0] : this.filteredTypes.toArray(new String[filteredTypes.size()]);
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

	public Collection<IPath> getProjectConfigurations() {
		return projectConfigurations;
	}

	public void setProjectConfigurations(Collection<IPath> projectConfigurations) {
		this.projectConfigurations = projectConfigurations;
	}

	public boolean isJavaFormatOnTypeEnabled() {
		return javaFormatOnTypeEnabled;
	}

	public Preferences setJavaFormatOnTypeEnabled(boolean javaFormatOnTypeEnabled) {
		this.javaFormatOnTypeEnabled = javaFormatOnTypeEnabled;
		return this;
	}

	public int getMaxCompletionResults() {
		return maxCompletionResults;
	}

	/**
	 * Sets the maximum number of completion results (excluding snippets and Javadoc
	 * proposals). If maxCompletions is set to 0 or lower, then the completion limit
	 * is considered disabled, which could certainly severly impact performance in a
	 * negative way.
	 *
	 * @param maxCompletions
	 */
	public Preferences setMaxCompletionResults(int maxCompletions) {
		if (maxCompletions < 1) {
			this.maxCompletionResults = Integer.MAX_VALUE;
		} else {
			this.maxCompletionResults = maxCompletions;
		}
		return this;
	}

	public ReferencedLibraries getReferencedLibraries() {
		return referencedLibraries;
	}

	public Preferences setReferencedLibraries(ReferencedLibraries referencedLibraries) {
		this.referencedLibraries = referencedLibraries;
		return this;
	}

	public Set<RuntimeEnvironment> getRuntimes() {
		return runtimes;
	}

	public Preferences setRuntimes(Set<RuntimeEnvironment> runtimes) {
		this.runtimes = runtimes;
		return this;
	}

	public int getImportOnDemandThreshold() {
		return importOnDemandThreshold;
	}

	public Preferences setImportOnDemandThreshold(int importOnDemandThreshold) {
		if (importOnDemandThreshold <= 0) {
			this.importOnDemandThreshold = IMPORTS_ONDEMANDTHRESHOLD_DEFAULT;
		} else {
			this.importOnDemandThreshold = importOnDemandThreshold;
		}
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_ONDEMANDTHRESHOLD, String.valueOf(this.importOnDemandThreshold));
		return this;
	}

	public int getStaticImportOnDemandThreshold() {
		return staticImportOnDemandThreshold;
	}

	public Preferences setStaticImportOnDemandThreshold(int staticImportOnDemandThreshold) {
		if (staticImportOnDemandThreshold <= 0) {
			this.staticImportOnDemandThreshold = IMPORTS_STATIC_ONDEMANDTHRESHOLD_DEFAULT;
		} else {
			this.staticImportOnDemandThreshold = staticImportOnDemandThreshold;
		}
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_STATIC_ONDEMANDTHRESHOLD, String.valueOf(this.staticImportOnDemandThreshold));
		return this;
	}

	public Preferences setGradleWrapperList(List<?> gradleWrapperList) {
		this.gradleWrapperList = gradleWrapperList;
		return this;
	}

	public List<?> getGradleWrapperList() {
		return this.gradleWrapperList == null ? Collections.emptyList() : this.gradleWrapperList;
	}

	public List<String> getFileHeaderTemplate() {
		return fileHeaderTemplate;
	}

	public Preferences setFileHeaderTemplate(List<String> fileHeaderTemplate) {
		this.fileHeaderTemplate = fileHeaderTemplate;
		return this;
	}

	public List<String> getTypeCommentTemplate() {
		return typeCommentTemplate;
	}

	public Preferences setTypeCommentTemplate(List<String> typeCommentTemplate) {
		this.typeCommentTemplate = typeCommentTemplate;
		return this;
	}

	public Preferences setIncludeAccessors(boolean includeAccessors) {
		this.includeAccessors = includeAccessors;
		return this;
	}

	public boolean isIncludeAccessors() {
		return this.includeAccessors;
	}

	public Preferences setSmartSemicolonDetection(boolean smartSemicolonDetection) {
		this.smartSemicolonDetection = smartSemicolonDetection;
		return this;
	}

	public boolean isSmartSemicolonDetection() {
		return this.smartSemicolonDetection;
	}

	public boolean isEclipseDownloadSources() {
		return eclipseDownloadSources;
	}

	public Preferences setEclipseDownloadSources(boolean enabled) {
		this.eclipseDownloadSources = enabled;
		return this;
	}

	public String getInvisibleProjectOutputPath() {
		return invisibleProjectOutputPath;
	}

	public void setInvisibleProjectOutputPath(String invisibleProjectOutputPath) {
		this.invisibleProjectOutputPath = ResourceUtils.expandPath(invisibleProjectOutputPath);
	}

	public List<String> getInvisibleProjectSourcePaths() {
		return invisibleProjectSourcePaths;
	}

	public void setInvisibleProjectSourcePaths(List<String> invisibleProjectSourcePaths) {
		if (invisibleProjectSourcePaths != null) {
			this.invisibleProjectSourcePaths = new ArrayList<>();
			for (String path : invisibleProjectSourcePaths) {
				this.invisibleProjectSourcePaths.add(ResourceUtils.expandPath(path));
			}
		} else {
			this.invisibleProjectSourcePaths = invisibleProjectSourcePaths;
		}
	}

	public Preferences setIncludeDecompiledSources(boolean includeDecompiledSources) {
		this.includeDecompiledSources = includeDecompiledSources;
		return this;
	}

	public boolean isIncludeDecompiledSources() {
		return this.includeDecompiledSources;
	}

	public boolean isIncludeSourceMethodDeclarations() {
		return this.includeSourceMethodDeclarations;
	}

	public void setIncludeSourceMethodDeclarations(boolean includeSourceMethodDeclarations) {
		this.includeSourceMethodDeclarations = includeSourceMethodDeclarations;
	}

	public Preferences setInsertSpaces(boolean insertSpaces) {
		this.insertSpaces = insertSpaces;
		return this;
	}

	public Preferences setTabSize(int tabSize) {
		this.tabSize = tabSize;
		return this;
	}

	public boolean isInsertSpaces() {
		return insertSpaces;
	}

	public int getTabSize() {
		return tabSize;
	}

	public void updateTabSizeInsertSpaces(Hashtable<String, String> options) {
		if (options == null) {
			return;
		}
		if (tabSize > 0) {
			options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, String.valueOf(tabSize));
		}
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, insertSpaces ? JavaCore.SPACE : JavaCore.TAB);
	}

	public InlayHintsParameterMode getInlayHintsParameterMode() {
		return inlayHintsParameterMode;
	}

	public boolean isInlayHintsVariableTypesEnabled() {
		return inlayHintsVariableTypesEnabled;
	}

	public boolean isInlayHintsParameterTypesEnabled() {
		return inlayHintsParameterTypesEnabled;
	}

	public void setInlayHintsParameterMode(InlayHintsParameterMode inlayHintsParameterMode) {
		this.inlayHintsParameterMode = inlayHintsParameterMode;
	}

	public boolean isInlayHintsSuppressedWhenSameNameNumberedParameter() {
		return inlayHintsSuppressedWhenSameNameNumberedParameter;
	}

	public void setInlayHintsSuppressedWhenSameNameNumberedParameter(boolean inlayHintsSuppressedWhenSameNameNumberedParameter) {
		this.inlayHintsSuppressedWhenSameNameNumberedParameter = inlayHintsSuppressedWhenSameNameNumberedParameter;
	}

	public Preferences setProjectEncoding(ProjectEncodingMode projectEncoding) {
		this.projectEncoding = projectEncoding;
		return this;
	}

	public List<String> getInlayHintsExclusionList() {
		return inlayHintsExclusionList;
	}

	public void setInlayHintsExclusionList(List<String> inlayHintsExclusionList) {
		this.inlayHintsExclusionList = inlayHintsExclusionList;
	}

	public ProjectEncodingMode getProjectEncoding() {
		return this.projectEncoding;
	}

	public void setAvoidVolatileChanges(boolean avoidVolatileChanges) {
		this.avoidVolatileChanges = avoidVolatileChanges;
	}

	public boolean getAvoidVolatileChanges() {
		return this.avoidVolatileChanges;
	}

	public boolean isProtobufSupportEnabled() {
		return protobufSupportEnabled;
	}

	public void setProtobufSupportEnabled(boolean protobufSupportEnabled) {
		this.protobufSupportEnabled = protobufSupportEnabled;
	}

	public boolean isAspectjSupportEnabled() {
		return aspectjSupportEnabled;
	}

	public void setAspectjSupportEnabled(boolean aspectjSupportEnabled) {
		this.aspectjSupportEnabled = aspectjSupportEnabled;
	}

	public boolean isAndroidSupportEnabled() {
		return this.androidSupportEnabled;
	}

	public void setAndroidSupportEnabled(boolean androidSupportEnabled) {
		this.androidSupportEnabled = androidSupportEnabled;
	}

	public boolean isJavacEnabled() {
		return this.javacEnabled;
	}

	public void setJavacEnabled(boolean javacEnabled) {
		this.javacEnabled = javacEnabled;
	}

	public List<String> getNonnullTypes() {
		return this.nonnullTypes;
	}

	public void setNonnullTypes(List<String> nonnullTypes) {
		this.nonnullTypes = nonnullTypes;
	}

	public List<String> getNullableTypes() {
		return this.nullableTypes;
	}

	public void setNullableTypes(List<String> nullableTypes) {
		this.nullableTypes = nullableTypes;
	}

	public List<String> getNonnullbydefaultTypes() {
		return this.nonnullbydefaultTypes;
	}

	public void setNonnullbydefaultTypes(List<String> nonnullbydefaultTypes) {
		this.nonnullbydefaultTypes = nonnullbydefaultTypes;
	}

	public void setNullAnalysisMode(FeatureStatus nullAnalysisMode) {
		this.nullAnalysisMode = nullAnalysisMode;
	}

	public FeatureStatus getNullAnalysisMode() {
		return this.nullAnalysisMode;
	}

	public void setExtractInterfaceReplaceEnabled(boolean extractInterfaceReplaceEnabled) {
		this.extractInterfaceReplaceEnabled = extractInterfaceReplaceEnabled;
	}

	public boolean getExtractInterfaceReplaceEnabled() {
		return this.extractInterfaceReplaceEnabled;
	}

	public void setChainCompletionEnabled(boolean chainCompletionEnabled) {
		this.chainCompletionEnabled = chainCompletionEnabled;
	}

	public boolean isChainCompletionEnabled() {
		return this.chainCompletionEnabled;
	}

	/**
	 * update the null analysis options of all projects based on the null analysis mode
	 * Returns the list of enabled clean ups.
	 *
	 * @return the list of enabled clean ups
	 */
	public List<String> getCleanUpActions() {
		return this.cleanUpActions;
	}

	public boolean getCleanUpActionsOnSaveEnabled() {
		return this.cleanUpActionsOnSaveEnabled;
	}

	/**
	 * @return whether the options are changed or not
	 */
	public boolean updateAnnotationNullAnalysisOptions() {
		switch (this.getNullAnalysisMode()) {
			case automatic:
				return this.updateAnnotationNullAnalysisOptions(true);
			case interactive:
				if (this.hasAnnotationNullAnalysisTypes()) {
					String cmd = "java.compile.nullAnalysis.setMode";
					ActionableNotification updateNullAnalysisStatusNotification = new ActionableNotification().withSeverity(MessageType.Info)
							.withMessage("Null annotation types have been detected in the project. Do you wish to enable null analysis for this project?")
							.withCommands(Arrays.asList(new Command("Enable", cmd, Arrays.asList(FeatureStatus.automatic)), new Command("Disable", cmd, Arrays.asList(FeatureStatus.disabled))));
					JavaLanguageServerPlugin.getProjectsManager().getConnection().sendActionableNotification(updateNullAnalysisStatusNotification);
				}
				return false;
			default:
				return this.updateAnnotationNullAnalysisOptions(false);
		}
	}

	private boolean updateAnnotationNullAnalysisOptions(boolean enabled) {
		long start = System.currentTimeMillis();
		boolean isChanged = false;
		for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
			isChanged |= updateAnnotationNullAnalysisOptions(javaProject, enabled);
		}
		JavaLanguageServerPlugin.debugTrace("updateAnnotationNullAnalysisOptions (" + enabled + ") Took:" + (System.currentTimeMillis() - start));
		return isChanged;
	}

	/**
	 * update the null analysis options of given project
	 * @param javaProject the java project to update the annotation-based null analysis options
	 * @param enabled specific whether the null analysis is enabled
	 * @return whether the options of the given project are changed or not
	 */
	public boolean updateAnnotationNullAnalysisOptions(IJavaProject javaProject, boolean enabled) {
		if (javaProject.getElementName().equals(ProjectsManager.DEFAULT_PROJECT_NAME)) {
			return false;
		}
		Map<String, String> projectInheritOptions = javaProject.getOptions(true);
		if (projectInheritOptions == null) {
			return false;
		}
		Map<String, String> projectNullAnalysisOptions;
		if (enabled) {
			String nonnullType;
			String nullableType;
			String nonnullbydefaultTypes;
			if (!this.nonnullTypes.isEmpty() || !this.nullableTypes.isEmpty() || !this.nonnullbydefaultTypes.isEmpty()) {
				try {
					ClasspathResult result = ProjectCommand.getClasspathsFromJavaProject(javaProject, new ProjectCommand.ClasspathOptions());
					nonnullType = getAnnotationType(javaProject, this.nonnullTypes, nonnullClasspathStorage, result);
					nullableType = getAnnotationType(javaProject, this.nullableTypes, nullableClasspathStorage, result);
					nonnullbydefaultTypes = getAnnotationType(javaProject, this.nonnullbydefaultTypes, nonnullbydefaultClasspathStorage, result);
					if (nonnullbydefaultTypes == null && nonnullType != null && nullableType != null) {
						// there is not NonNullByDefault in org.jetbrains:annotations
						nonnullbydefaultTypes = "org.eclipse.jdt.annotation.NonNullByDefault";
					}
				} catch (CoreException | URISyntaxException e) {
					JavaLanguageServerPlugin.logException(e);
					nonnullType = null;
					nullableType = null;
					nonnullbydefaultTypes = null;
				}
			} else {
				nonnullType = null;
				nullableType = null;
				nonnullbydefaultTypes = null;
			}
			projectNullAnalysisOptions = generateProjectNullAnalysisOptions(nonnullType, nullableType, nonnullbydefaultTypes);
		} else {
			projectNullAnalysisOptions = generateProjectNullAnalysisOptions(null, null, null);
		}
		boolean shouldUpdate = !projectNullAnalysisOptions.entrySet().stream().allMatch(e -> e.getValue().equals(projectInheritOptions.get(e.getKey())));
		if (shouldUpdate) {
			// get existing project options
			Map<String, String> projectOptions = javaProject.getOptions(false);
			if (projectOptions != null) {
				projectOptions.putAll(projectNullAnalysisOptions);
				javaProject.setOptions(projectOptions);
			} else {
				return false;
			}
		}
		return shouldUpdate;
	}

	private boolean hasAnnotationNullAnalysisTypes() {
		if (this.nonnullTypes.isEmpty() && this.nullableTypes.isEmpty() && this.nonnullbydefaultTypes.isEmpty()) {
			return false;
		}
		for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
			if (javaProject.getElementName().equals(ProjectsManager.DEFAULT_PROJECT_NAME)) {
				continue;
			}
			try {
				ClasspathResult result = ProjectCommand.getClasspathsFromJavaProject(javaProject, new ProjectCommand.ClasspathOptions());
				String nonnullType = getAnnotationType(javaProject, this.nonnullTypes, nonnullClasspathStorage, result);
				String nullableType = getAnnotationType(javaProject, this.nullableTypes, nullableClasspathStorage, result);
				String nonnullbydefaultTypes = getAnnotationType(javaProject, this.nonnullbydefaultTypes, nonnullbydefaultClasspathStorage, result);
				if (nonnullType != null && nullableType != null && nonnullbydefaultTypes != null) {
					return true;
				}
			} catch (CoreException | URISyntaxException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		return false;
	}

	private String getAnnotationType(IJavaProject javaProject, List<String> annotationTypes, Map<String, List<String>> classpathStorage, ClasspathResult result) {
		if (!annotationTypes.isEmpty()) {
			try {
				for (String annotationType : annotationTypes) {
					if (classpathStorage.keySet().contains(annotationType)) {
						// for known types, check the classpath to achieve a better performance
						for (String classpath : result.classpaths) {
							IClasspathEntry classpathEntry = javaProject.getClasspathEntryFor(new Path(classpath));
							if (classpathEntry != null && classpathEntry.isTest()) {
								continue;
							}
							for (String classpathSubString : classpathStorage.get(annotationType)) {
								if (classpath.contains(classpathSubString)) {
									return annotationType;
								}
							}
						}
						String aType = findTypeInProject(javaProject, annotationType, classpathStorage);
						if (aType != null) {
							return aType;
						}
					} else {
						// for unknown types, try to find type in the project
						try {
							String aType = findTypeInProject(javaProject, annotationType, classpathStorage);
							if (aType != null) {
								return aType;
							}
						} catch (JavaModelException e) {
							continue;
						}
					}
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		return null;
	}

	private String findTypeInProject(IJavaProject javaProject, String annotationType, Map<String, List<String>> classpathStorage) throws JavaModelException {
		IType type = javaProject.findType(annotationType);
		if (type != null) {
			IJavaElement fragmentRoot = type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			IClasspathEntry classpathEntry = javaProject.getClasspathEntryFor(fragmentRoot.getPath());
			if (classpathEntry == null || !classpathEntry.isTest()) {
				String classpath = fragmentRoot.getPath().toOSString();
				if (classpathStorage.containsKey(annotationType)) {
					classpathStorage.get(annotationType).add(classpath);
				} else {
					classpathStorage.put(annotationType, new ArrayList<>(Arrays.asList(classpath)));
				}
				return annotationType;
			}
		}
		return null;
	}

	/**
	 * generates the null analysis options of the given nonnull type and nullable type
	 * @param nonnullType the given nonnull type
	 * @param nullableType the given nullable type
	 * @return the map contains the null analysis options, if both given types are null, will return default null analysis options
	 */
	private Map<String, String> generateProjectNullAnalysisOptions(String nonnullType, String nullableType, String nonnullbydefaultType) {
		Map<String, String> options = new HashMap<>();
		if (nonnullType == null || nullableType == null || nonnullbydefaultType == null) {
			options.put(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, "disabled");
			// set default values
			Hashtable<String, String> defaultOptions = JavaCore.getDefaultOptions();
			options.put(JavaCore.COMPILER_NONNULL_ANNOTATION_NAME, defaultOptions.get(JavaCore.COMPILER_NONNULL_ANNOTATION_NAME));
			options.put(JavaCore.COMPILER_NULLABLE_ANNOTATION_NAME, defaultOptions.get(JavaCore.COMPILER_NULLABLE_ANNOTATION_NAME));
			options.put(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME, defaultOptions.get(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME));
			options.put(JavaCore.COMPILER_PB_NULL_REFERENCE, defaultOptions.get(JavaCore.COMPILER_PB_NULL_REFERENCE));
			options.put(JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE, defaultOptions.get(JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE));
			options.put(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION, defaultOptions.get(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION));
			options.put(JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT, defaultOptions.get(JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT));
			options.put(JavaCore.COMPILER_PB_SYNTACTIC_NULL_ANALYSIS_FOR_FIELDS, defaultOptions.get(JavaCore.COMPILER_PB_SYNTACTIC_NULL_ANALYSIS_FOR_FIELDS));
		} else {
			options.put(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, "enabled");
			options.put(JavaCore.COMPILER_NONNULL_ANNOTATION_NAME, nonnullType != null ? nonnullType : "");
			options.put(JavaCore.COMPILER_NULLABLE_ANNOTATION_NAME, nullableType != null ? nullableType : "");
			options.put(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME, nonnullbydefaultType != null ? nonnullbydefaultType : "");
			options.put(JavaCore.COMPILER_PB_NULL_REFERENCE, "warning");
			options.put(JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE, "warning");
			options.put(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION, "warning");
			options.put(JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT, "warning");
			options.put(JavaCore.COMPILER_PB_MISSING_NONNULL_BY_DEFAULT_ANNOTATION, "ignore");
			options.put(JavaCore.COMPILER_PB_SYNTACTIC_NULL_ANALYSIS_FOR_FIELDS, JavaCore.ENABLED);
		}
		return options;
	}

	public boolean isTelemetryEnabled() {
		return telemetryEnabled;
	}

	public boolean isValidateAllOpenBuffersOnChanges() {
		return validateAllOpenBuffersOnChanges;
	}

	public void setValidateAllOpenBuffersOnChanges(boolean validateAllOpenBuffersOnChanges) {
		this.validateAllOpenBuffersOnChanges = validateAllOpenBuffersOnChanges;
	}

	public List<String> getDiagnosticFilter() {
		return this.diagnosticFilter;
	}

	public void setDiagnosticFilter(List<String> diagnosticFilter) {
		this.diagnosticFilter = diagnosticFilter;
	}

	public List<String> getFilesAssociations() {
		return filesAssociations;
	}

	public void setFilesAssociations(List<String> filesAssociations) {
		this.filesAssociations = filesAssociations;
	}

	public void setSearchScope(SearchScope value) {
		this.searchScope = value;
	}

	public SearchScope getSearchScope() {
		return searchScope;
	}
}
