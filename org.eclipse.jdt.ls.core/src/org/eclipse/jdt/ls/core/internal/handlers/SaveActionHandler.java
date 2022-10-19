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
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.cleanup.CleanUpRegistry;
import org.eclipse.jdt.ls.core.internal.commands.OrganizeImportsCommand;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;

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

		if (preferenceManager.getPreferences().isJavaSaveActionsOrganizeImportsEnabled()) {
			edit.addAll(handleSaveActionOrganizeImports(documentUri, monitor));
		}

		Preferences preferences = preferenceManager.getPreferences();
		List<TextEdit> cleanUpEdits = cleanUpRegistry.getEditsForAllActiveCleanUps(params.getTextDocument(), preferences.getCleanUpActionsOnSave(), monitor);
		edit.addAll(cleanUpEdits);

		return edit;
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
