/*******************************************************************************
 * Copyright (c) 2016-2020 Red Hat Inc. and others.
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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.MembersOrderPreferenceCacheCommon;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettingsConstants;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jdt.ls.core.internal.handlers.FormatterHandler;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.lsp4j.ClientCapabilities;
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
	private static final String CUSTOM_CODE_TEMPLATES = IConstants.PLUGIN_ID + ".custom_code_templates";
	private ClientPreferences clientPreferences;
	private ListenerList<IPreferencesChangeListener> preferencesChangeListeners;
	private IEclipsePreferences eclipsePrefs;
	private static Map<String, Template> templates = new LinkedHashMap<>();

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
		initializeJavaCoreOptions();

		// Initialize default preferences
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put("org.eclipse.jdt.ui.typefilter.enabled", "");
		defEclipsePrefs.put(CodeStyleConfiguration.ORGIMPORTS_IMPORTORDER, String.join(";", Preferences.JAVA_IMPORT_ORDER_DEFAULT));
		defEclipsePrefs.put(MembersOrderPreferenceCacheCommon.APPEARANCE_MEMBER_SORT_ORDER, JavaLanguageServerPlugin.DEFAULT_MEMBER_SORT_ORDER); //$NON-NLS-1$
		defEclipsePrefs.put(MembersOrderPreferenceCacheCommon.APPEARANCE_VISIBILITY_SORT_ORDER, JavaLanguageServerPlugin.DEFAULT_VISIBILITY_SORT_ORDER);
		defEclipsePrefs.put(CodeGenerationSettingsConstants.CODEGEN_USE_OVERRIDE_ANNOTATION, Boolean.TRUE.toString());

		defEclipsePrefs.put(StubUtility.CODEGEN_KEYWORD_THIS, Boolean.FALSE.toString());
		defEclipsePrefs.put(StubUtility.CODEGEN_IS_FOR_GETTERS, Boolean.TRUE.toString());
		defEclipsePrefs.put(StubUtility.CODEGEN_EXCEPTION_VAR_NAME, "e"); //$NON-NLS-1$
		defEclipsePrefs.put(StubUtility.CODEGEN_ADD_COMMENTS, Boolean.FALSE.toString());

		ContextTypeRegistry registry = new ContextTypeRegistry();
		// Register standard context types from JDT
		CodeTemplateContextType.registerContextTypes(registry);
		// Register additional context types
		registry.addContextType(new CodeTemplateContextType(CodeTemplatePreferences.CLASSSNIPPET_CONTEXTTYPE));
		registry.addContextType(new CodeTemplateContextType(CodeTemplatePreferences.INTERFACESNIPPET_CONTEXTTYPE));
		registry.addContextType(new CodeTemplateContextType(CodeTemplatePreferences.RECORDSNIPPET_CONTEXTTYPE));

		// These should be upstreamed into CodeTemplateContextType & GlobalVariables
		TemplateContextType tmp = registry.getContextType(CodeTemplateContextType.TYPECOMMENT_CONTEXTTYPE);
		tmp.addResolver(new CodeTemplatePreferences.Month());
		tmp.addResolver(new CodeTemplatePreferences.ShortMonth());
		tmp.addResolver(new CodeTemplatePreferences.Day());
		tmp.addResolver(new CodeTemplatePreferences.Hour());
		tmp.addResolver(new CodeTemplatePreferences.Minute());

		JavaManipulation.setCodeTemplateContextRegistry(registry);

		// Initialize templates
		templates.put(CodeTemplatePreferences.CODETEMPLATE_FIELDCOMMENT, CodeGenerationTemplate.FIELDCOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_METHODCOMMENT, CodeGenerationTemplate.METHODCOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORCOMMENT, CodeGenerationTemplate.CONSTRUCTORCOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORBODY, CodeGenerationTemplate.CONSTRUCTORBODY.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_DELEGATECOMMENT, CodeGenerationTemplate.DELEGATECOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_OVERRIDECOMMENT, CodeGenerationTemplate.OVERRIDECOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_TYPECOMMENT, CodeGenerationTemplate.TYPECOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_GETTERCOMMENT, CodeGenerationTemplate.GETTERCOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_SETTERCOMMENT, CodeGenerationTemplate.SETTERCOMMENT.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_GETTERBODY, CodeGenerationTemplate.GETTERBODY.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_SETTERBODY, CodeGenerationTemplate.SETTERBOY.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_CATCHBODY, CodeGenerationTemplate.CATCHBODY.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_METHODBODY, CodeGenerationTemplate.METHODBODY.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_NEWTYPE, CodeGenerationTemplate.NEWTYPE.createTemplate());
		templates.put(CodeTemplatePreferences.CODETEMPLATE_FILECOMMENT, CodeGenerationTemplate.FILECOMMENT.createTemplate());
		reloadTemplateStore();
	}

	public static void initializeJavaCoreOptions() {
		Hashtable<String, String> javaCoreOptions = JavaCore.getOptions();
		javaCoreOptions.put(JavaCore.CODEASSIST_VISIBILITY_CHECK, JavaCore.ENABLED);
		javaCoreOptions.put(JavaCore.COMPILER_RELEASE, JavaCore.ENABLED);
		javaCoreOptions.putAll(FormatterHandler.getJavaLSDefaultFormatterSettings());
		javaCoreOptions.put(JavaCore.COMPILER_PB_UNHANDLED_WARNING_TOKEN, JavaCore.IGNORE);
		javaCoreOptions.put(JavaCore.COMPILER_PB_REDUNDANT_SUPERINTERFACE, JavaCore.WARNING);
		javaCoreOptions.put(JavaCore.CODEASSIST_SUBWORD_MATCH, JavaCore.DISABLED);
		javaCoreOptions.put(JavaCore.COMPILER_PB_MISSING_SERIAL_VERSION, JavaCore.IGNORE);
		// workaround for https://github.com/redhat-developer/vscode-java/issues/718
		javaCoreOptions.put(JavaCore.CORE_CIRCULAR_CLASSPATH, JavaCore.WARNING);
		javaCoreOptions.put(JavaCore.COMPILER_IGNORE_UNNAMED_MODULE_FOR_SPLIT_PACKAGE, JavaCore.ENABLED);
		JavaCore.setOptions(javaCoreOptions);
	}

	private static void reloadTemplateStore() {
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		TemplatePersistenceData[] templateData = templates.values().stream()
			.map(t -> new TemplatePersistenceData(t, true, t.getDescription()))
			.collect(Collectors.toList()).toArray(new TemplatePersistenceData[0]);

		TemplateReaderWriter trw = new TemplateReaderWriter();
		try (Writer wrt = new StringWriter()) {
			trw.save(templateData, wrt);
			defEclipsePrefs.put(CUSTOM_CODE_TEMPLATES, wrt.toString());
		} catch (IOException e) {
		}

		TemplateStoreCore tscore = new TemplateStoreCore(defEclipsePrefs, CUSTOM_CODE_TEMPLATES);
		try {
			tscore.load();
		} catch (IOException e) {
		}

		JavaManipulation.setCodeTemplateStore(tscore);
	}

	private static boolean updateTemplate(String templateId, String content) {
		Template template = templates.get(templateId);
		if ((StringUtils.isEmpty(content) && template == null)
				|| (template != null && Objects.equals(content, template.getPattern()))) {
			return false;
		}

		CodeGenerationTemplate codeTemplate = CodeGenerationTemplate.getValueById(templateId);
		if (codeTemplate == null) {
			return false;
		}

		templates.put(templateId, codeTemplate.createTemplate(content));
		return true;
	}

	public void update(Preferences preferences) {
		if(preferences == null){
			throw new IllegalArgumentException("Preferences can not be null");
		}
		Preferences oldPreferences = this.preferences;
		this.preferences = preferences;
		preferencesChanged(oldPreferences, preferences); // listener will get latest preference from getPreferences()
		// Update the templates according to the new preferences.
		boolean templateChanged = false;
		List<String> fileHeader = preferences.getFileHeaderTemplate();
		String content = fileHeader == null ? "" : String.join("\n", fileHeader);
		templateChanged |= updateTemplate(CodeTemplatePreferences.CODETEMPLATE_FILECOMMENT, content);

		List<String> typeComment = preferences.getTypeCommentTemplate();
		content = typeComment == null ? "" : String.join("\n", typeComment);
		templateChanged |= updateTemplate(CodeTemplatePreferences.CODETEMPLATE_TYPECOMMENT, content);
		if (templateChanged) {
			reloadTemplateStore();
		}
		Hashtable<String, String> options = JavaCore.getOptions();
		preferences.updateTabSizeInsertSpaces(options);
		JavaCore.setOptions(options);
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

	public static CodeGenerationSettings getCodeGenerationSettings(ICompilationUnit cu) {
		CodeGenerationSettings res = new CodeGenerationSettings();
		res.overrideAnnotation = true;
		res.createComments = false;
		res.tabWidth = CodeFormatterUtil.getTabWidth(cu);
		res.indentWidth = CodeFormatterUtil.getIndentWidth(cu);
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
