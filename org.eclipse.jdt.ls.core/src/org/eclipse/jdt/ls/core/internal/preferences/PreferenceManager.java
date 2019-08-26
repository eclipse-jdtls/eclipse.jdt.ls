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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.MembersOrderPreferenceCacheCommon;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.preferences.MavenPreferenceConstants;
import org.eclipse.m2e.core.internal.preferences.ProblemSeverity;
import org.eclipse.text.templates.ContextTypeRegistry;
import org.eclipse.text.templates.TemplatePersistenceData;
import org.eclipse.text.templates.TemplateReaderWriter;
import org.eclipse.text.templates.TemplateStoreCore;

/**
 * Preference manager
 *
 * @author Gorkem Ercan
 * @author Fred Bricon
 *
 */
public class PreferenceManager {

	private Preferences preferences ;
	private static final String M2E_APT_ID = "org.jboss.tools.maven.apt";
	private static final String CUSTOM_CODE_TEMPLATES = IConstants.PLUGIN_ID + ".custom_code_templates";
	private ClientPreferences clientPreferences;
	private IMavenConfiguration mavenConfig;
	private ListenerList<IPreferencesChangeListener> preferencesChangeListeners;
	private IEclipsePreferences eclipsePrefs;

	public PreferenceManager() {
		preferences = new Preferences();
		preferencesChangeListeners = new ListenerList<>();
		eclipsePrefs = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		initialize();
	}

	/**
	 * Initialize default preference values of used bundles to match server
	 * functionality.
	 */
	public static void initialize() {
		// Update JavaCore options
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		javaCoreOptions.put(JavaCore.COMPILER_RELEASE, JavaCore.ENABLED);
		javaCoreOptions.put(DefaultCodeFormatterConstants.FORMATTER_USE_ON_OFF_TAGS, DefaultCodeFormatterConstants.TRUE);
		javaCoreOptions.put(JavaCore.COMPILER_PB_UNHANDLED_WARNING_TOKEN, JavaCore.IGNORE);
		JavaCore.setOptions(javaCoreOptions);

		// Initialize default preferences
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put("org.eclipse.jdt.ui.typefilter.enabled", "");
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_IMPORTORDER, String.join(";", Preferences.JAVA_IMPORT_ORDER_DEFAULT));
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_ONDEMANDTHRESHOLD, "99");
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_STATIC_ONDEMANDTHRESHOLD, "99");
		defEclipsePrefs.put(MembersOrderPreferenceCacheCommon.APPEARANCE_MEMBER_SORT_ORDER, "T,SF,SI,SM,F,I,C,M"); //$NON-NLS-1$

		defEclipsePrefs.put(StubUtility.CODEGEN_KEYWORD_THIS, Boolean.FALSE.toString());
		defEclipsePrefs.put(StubUtility.CODEGEN_IS_FOR_GETTERS, Boolean.TRUE.toString());
		defEclipsePrefs.put(StubUtility.CODEGEN_EXCEPTION_VAR_NAME, "e"); //$NON-NLS-1$
		defEclipsePrefs.put(StubUtility.CODEGEN_ADD_COMMENTS, Boolean.FALSE.toString());

		IEclipsePreferences m2eAptPrefs = DefaultScope.INSTANCE.getNode(M2E_APT_ID);
		if (m2eAptPrefs != null) {
			m2eAptPrefs.put(M2E_APT_ID + ".mode", "jdt_apt");
		}
		initializeMavenPreferences();

		// Initialize templates
		Template [] templates = new Template [] {
				CodeGenerationTemplate.FIELDCOMMENT.createTemplate(null),
				CodeGenerationTemplate.METHODCOMMENT.createTemplate(null),
				CodeGenerationTemplate.CONSTRUCTORCOMMENT.createTemplate(null),
				CodeGenerationTemplate.CONSTRUCTORBODY.createTemplate(null),
				CodeGenerationTemplate.DELEGATECOMMENT.createTemplate(null),
				CodeGenerationTemplate.OVERRIDECOMMENT.createTemplate(null),
				CodeGenerationTemplate.TYPECOMMENT.createTemplate(null),
				CodeGenerationTemplate.GETTERCOMMENT.createTemplate(null),
				CodeGenerationTemplate.SETTERCOMMENT.createTemplate(null),
				CodeGenerationTemplate.GETTERBODY.createTemplate(null),
				CodeGenerationTemplate.SETTERBOY.createTemplate(null),
				CodeGenerationTemplate.CATCHBODY.createTemplate(null),
				CodeGenerationTemplate.METHODBODY.createTemplate(null)
		};

		TemplatePersistenceData[] templateData = Arrays.asList(templates).stream()
				.map(t -> new TemplatePersistenceData(t, true, t.getDescription()))
				.collect(Collectors.toList()).toArray(new TemplatePersistenceData[0]);

		TemplateReaderWriter trw = new TemplateReaderWriter();
		try (Writer wrt = new StringWriter()) {
			trw.save(templateData, wrt);
			defEclipsePrefs.put(CUSTOM_CODE_TEMPLATES, wrt.toString());
		} catch (IOException e) {
		}

		ContextTypeRegistry registry = new ContextTypeRegistry();
		// Register standard context types from JDT
		CodeTemplateContextType.registerContextTypes(registry);
		// Register additional context types
		registry.addContextType(new CodeTemplateContextType(CodeTemplatePreferences.CLASSSNIPPET_CONTEXTTYPE));
		registry.addContextType(new CodeTemplateContextType(CodeTemplatePreferences.INTERFACESNIPPET_CONTEXTTYPE));

		TemplateStoreCore tscore = new TemplateStoreCore(defEclipsePrefs, CUSTOM_CODE_TEMPLATES);
		try {
			tscore.load();
		} catch (IOException e) {
		}

		JavaManipulation.setCodeTemplateStore(tscore);
		JavaManipulation.setCodeTemplateContextRegistry(registry);
	}

	private static void initializeMavenPreferences() {
		IEclipsePreferences store = InstanceScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);
		store.put(MavenPreferenceConstants.P_OUT_OF_DATE_PROJECT_CONFIG_PB, ProblemSeverity.warning.toString());
	}

	public void update(Preferences preferences) {
		if(preferences == null){
			throw new IllegalArgumentException("Preferences can not be null");
		}
		preferencesChanged(this.preferences, preferences);
		this.preferences = preferences;

		String newMavenSettings = preferences.getMavenUserSettings();
		String oldMavenSettings = getMavenConfiguration().getUserSettingsFile();
		if (!Objects.equals(newMavenSettings, oldMavenSettings)) {
			try {
				getMavenConfiguration().setUserSettingsFile(newMavenSettings);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("failed to set Maven settings", e);
				preferences.setMavenUserSettings(oldMavenSettings);
			}
		}

		updateParallelBuild(this.preferences.getMaxConcurrentBuilds());
		// TODO serialize preferences
	}

	private void preferencesChanged(Preferences oldPreferences, Preferences newPreferences) {
		for (final IPreferencesChangeListener listener : preferencesChangeListeners) {
			ISafeRunnable job = new ISafeRunnable() {
				@Override
				public void handleException(Throwable e) {
					JavaLanguageServerPlugin.log(new CoreException(StatusFactory.newErrorStatus(e.getMessage(), e)));
				}

				@Override
				public void run() throws Exception {
					listener.preferencesChange(oldPreferences, newPreferences);
				}
			};
			SafeRunner.run(job);
		}
	}

	private void updateParallelBuild(int maxConcurrentBuilds) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		if (description.getMaxConcurrentBuilds() == maxConcurrentBuilds) {
			return;
		}

		description.setMaxConcurrentBuilds(maxConcurrentBuilds);
		try {
			workspace.setDescription(description);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problems setting maxConcurrentBuilds from workspace.", e);
		}

		String stringValue = maxConcurrentBuilds != 1 ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
		IEclipsePreferences pref = InstanceScope.INSTANCE.getNode(IMavenConstants.PLUGIN_ID);
		pref.put(MavenPreferenceConstants.P_BUILDER_USE_NULL_SCHEDULING_RULE, stringValue);
		pref = InstanceScope.INSTANCE.getNode(JavaCore.PLUGIN_ID);
	}

	/**
	 * Workspace wide preferences
	 */
	public Preferences getPreferences() {
		return preferences;
	}

	/**
	 * Resource specific preferences
	 */
	public Preferences getPreferences(IResource resource) {
		return preferences;
	}

	public ClientPreferences getClientPreferences() {
		return clientPreferences;
	}

	/**
	 * @param clientCapabilities
	 *                                       the clientCapabilities to set
	 * @param extendedClientCapabilities
	 */
	public void updateClientPrefences(ClientCapabilities clientCapabilities, Map<String, Object> extendedClientCapabilities) {
		this.clientPreferences = new ClientPreferences(clientCapabilities, extendedClientCapabilities);
	}

	public IMavenConfiguration getMavenConfiguration() {
		if (mavenConfig == null) {
			mavenConfig = MavenPlugin.getMavenConfiguration();
		}
		return mavenConfig;
	}

	/**
	 * public for testing purposes
	 */
	public void setMavenConfiguration(IMavenConfiguration mavenConfig) {
		this.mavenConfig = mavenConfig;
	}

	public static Preferences getPrefs(IResource resource) {
		return JavaLanguageServerPlugin.getPreferencesManager().getPreferences(resource);
	}

	public static CodeGenerationSettings getCodeGenerationSettings(IResource resource) {
		IJavaProject project = JavaCore.create(resource.getProject());

		CodeGenerationSettings res = new CodeGenerationSettings();
		res.overrideAnnotation = true;
		res.createComments = false;
		// TODO indentation settings should be retrieved from client/external
		// settings?
		res.tabWidth = CodeFormatterUtil.getTabWidth(project);
		res.indentWidth = CodeFormatterUtil.getIndentWidth(project);
		return res;
	}

	/**
	 * Register the given listener for notification of preferences changes. Calling
	 * this method multiple times with the same listener has no effect. The given
	 * listener argument must not be <code>null</code>.
	 *
	 * @param listener
	 *            the preferences change listener to register
	 */
	public void addPreferencesChangeListener(IPreferencesChangeListener listener) {
		preferencesChangeListeners.add(listener);

	}

	/**
	 * De-register the given listener from receiving notification of preferences
	 * changes. Calling this method multiple times with the same listener has no
	 * effect. The given listener argument must not be <code>null</code>.
	 *
	 * @param listener
	 *            the preference change listener to remove
	 */
	public void removePreferencesChangeListener(IPreferencesChangeListener listener) {
		preferencesChangeListeners.remove(listener);
	}

	/**
	 * @return Get the workspace runtime preferences for Eclipse
	 */
	public IEclipsePreferences getEclipsePreferences() {
		return eclipsePrefs;
	}

	/**
	 * Checks whether the client supports class file contents
	 */
	public boolean isClientSupportsClassFileContent() {
		return getClientPreferences() != null && getClientPreferences().isClassFileContentSupported();
	}

	/**
	 * Checks whether the client supports markdown in completion
	 */
	public boolean isClientSupportsCompletionDocumentationMarkDown() {
		return getClientPreferences() != null && getClientPreferences().isSupportsCompletionDocumentationMarkdown();
	}

}
