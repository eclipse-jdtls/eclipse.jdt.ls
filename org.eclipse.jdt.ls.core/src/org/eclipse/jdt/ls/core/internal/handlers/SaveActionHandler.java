/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     btstream - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.commands.OrganizeImportsCommand;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;

public class SaveActionHandler {

	private PreferenceManager preferenceManager;
	private OrganizeImportsCommand organizeImportsCommand;

	public SaveActionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		this.organizeImportsCommand = new OrganizeImportsCommand();
	}

	public List<TextEdit> willSaveWaitUntil(WillSaveTextDocumentParams params, IProgressMonitor monitor) {
		List<TextEdit> edit = new ArrayList<>();

		if (monitor.isCanceled()) {
			return edit;
		}

		String documentUri = params.getTextDocument().getUri();

		if (preferenceManager.getPreferences().isJavaSaveActionOrganizeImportsEnabled()) {
			edit.addAll(handleSaveActionOrganizeImports(documentUri, monitor));
		}

		return edit;
	}

	private List<TextEdit> handleSaveActionOrganizeImports(String uri, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return new ArrayList<>();
		}
		WorkspaceEdit organizedResult = organizeImportsCommand.organizeImportsInFile(uri);
		List<TextEdit> edit = organizedResult.getChanges().get(uri);
		edit = edit == null ? new ArrayList<>() : edit;
		return edit;
	}

}
