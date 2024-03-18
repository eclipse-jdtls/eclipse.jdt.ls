/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     btstream - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.cleanup.CleanUpRegistry;
import org.eclipse.jdt.ls.core.internal.commands.OrganizeImportsCommand;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.osgi.service.prefs.BackingStoreException;

public class SaveActionHandler {

	private PreferenceManager preferenceManager;
	private OrganizeImportsCommand organizeImportsCommand;
	private CleanUpRegistry cleanUpRegistry;

	public SaveActionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		this.organizeImportsCommand = new OrganizeImportsCommand();
		this.cleanUpRegistry = new CleanUpRegistry();
	}

	public List<TextEdit> willSaveWaitUntil(WillSaveTextDocumentParams params, IProgressMonitor monitor) {
		List<TextEdit> edit = new ArrayList<>();

		if (monitor.isCanceled()) {
			return edit;
		}

		String documentUri = params.getTextDocument().getUri();

		Preferences preferences = preferenceManager.getPreferences();
		IEclipsePreferences jdtUiPreferences = getJdtUiProjectPreferences(documentUri);
		boolean canUseInternalSettings = preferenceManager.getClientPreferences().canUseInternalSettings();
		if (preferences.isJavaSaveActionsOrganizeImportsEnabled() ||
				(canUseInternalSettings && jdtUiPreferences != null && jdtUiPreferences.getBoolean("sp_" + CleanUpConstants.ORGANIZE_IMPORTS, false))) {
			edit.addAll(handleSaveActionOrganizeImports(documentUri, monitor));
		}

		LinkedHashSet<String> cleanUpIds = new LinkedHashSet<>();

		List<String> lspCleanups = Collections.emptyList();
		if (preferences.getCleanUpActionsOnSaveEnabled()) {
			lspCleanups = preferences.getCleanUpActions();
		}
		Collection<String> jdtSettingCleanups = getCleanupsFromJDTUIPreferences(jdtUiPreferences);

		cleanUpIds.addAll(canUseInternalSettings ? jdtSettingCleanups : lspCleanups);
		cleanUpIds.remove(BaseDocumentLifeCycleHandler.RENAME_FILE_TO_TYPE);
		List<TextEdit> cleanUpEdits = cleanUpRegistry.getEditsForAllActiveCleanUps(params.getTextDocument(), new ArrayList<>(cleanUpIds), monitor);
		edit.addAll(cleanUpEdits);
		return edit;
	}

	public WorkspaceEdit performManualCleanupActions(TextDocumentIdentifier doc, IProgressMonitor monitor) {
		List<TextEdit> edit = new ArrayList<>();

		if (monitor.isCanceled()) {
			return null;
		}

		String documentUri = doc.getUri();
		Preferences preferences = preferenceManager.getPreferences();
		IEclipsePreferences jdtUiPreferences = getJdtUiProjectPreferences(documentUri);
		boolean canUseInternalSettings = preferenceManager.getClientPreferences().canUseInternalSettings();
		LinkedHashSet<String> cleanUpIds = new LinkedHashSet<>();

		List<String> lspCleanups = preferences.getCleanUpActions();
		Collection<String> jdtSettingCleanups = getCleanupsFromJDTUIPreferences(jdtUiPreferences);

		cleanUpIds.addAll(canUseInternalSettings ? jdtSettingCleanups : lspCleanups);
		cleanUpIds.remove(BaseDocumentLifeCycleHandler.RENAME_FILE_TO_TYPE);
		List<TextEdit> cleanUpEdits = cleanUpRegistry.getEditsForAllActiveCleanUps(doc, new ArrayList<>(cleanUpIds), monitor);
		edit.addAll(cleanUpEdits);
		Map<String, List<TextEdit>> editMap = new HashMap<>();
		editMap.put(doc.getUri(), edit);
		WorkspaceEdit finalEdit = new WorkspaceEdit(editMap);
		return finalEdit;
	}

	private Collection<String> getCleanupsFromJDTUIPreferences(IEclipsePreferences jdtUIPrefs) {
		if (jdtUIPrefs == null) {
			return List.of();
		}
		try {
			if (jdtUIPrefs.getBoolean("editor_save_participant_org.eclipse.jdt.ui.postsavelistener.cleanup", false)) { // cleanup on save enabled ( AbstractSaveParticipantPreferenceConfiguration.isEnabled() )
				return Arrays.stream(jdtUIPrefs.keys()) //
					.filter(key -> key.startsWith("sp_")) // save participant ( CleanUpPreferenceUtil.SAVE_PARTICIPANT_KEY_PREFIX )
					.filter(key -> jdtUIPrefs.getBoolean(key, false)) // enabled ones
					.map(key -> key.substring(3)) // remove "sp_" prefix
					.toList();
			}
		} catch (BackingStoreException e) {
			JavaLanguageServerPlugin.logException(e);
		}
		return List.of();
	}

	private IEclipsePreferences getJdtUiProjectPreferences(String documentUri) {
		ICompilationUnit compilationUnit = JDTUtils.resolveCompilationUnit(documentUri);
		if (compilationUnit != null) {
			IJavaProject javaProject = compilationUnit.getJavaProject();
			try {
				if (javaProject.getCorrespondingResource() instanceof IProject project) {
					ProjectScope scope = new ProjectScope(project);
					return scope.getNode(JavaLanguageServerPlugin.JDT_UI_PLUGIN);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
		return null;
	}

	private List<TextEdit> handleSaveActionOrganizeImports(String documentUri, IProgressMonitor monitor) {
		String uri = ResourceUtils.fixURI(JDTUtils.toURI(documentUri));
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		WorkspaceEdit organizedResult = organizeImportsCommand.organizeImportsInFile(uri);
		List<TextEdit> edit = organizedResult.getChanges().get(uri);
		edit = edit == null ? Collections.emptyList() : edit;
		return edit;
	}

}
