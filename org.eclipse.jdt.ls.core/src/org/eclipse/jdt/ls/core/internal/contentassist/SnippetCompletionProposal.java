/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.preferences.CodeGenerationTemplate;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import com.google.common.collect.Sets;

public class SnippetCompletionProposal {
	private static final String CLASS_SNIPPET_LABEL = "class";
	private static final String INTERFACE_SNIPPET_LABEL = "interface";
	private static final Set<String> UNSUPPORTED_RESOURCES = Sets.newHashSet("module-info.java", "package-info.java");

	public static List<CompletionItem> getSnippets(ICompilationUnit cu) {
		if (cu == null) {
			throw new IllegalArgumentException("Compilation unit must not be null"); //$NON-NLS-1$
		}
		//This check might need to be pushed back to the different get*Snippet() methods, depending on future features
		if (UNSUPPORTED_RESOURCES.contains(cu.getResource().getName())) {
			return Collections.emptyList();
		}
		List<CompletionItem> res = new ArrayList<>(2);
		CompletionItem classSnippet = getClassSnippet(cu);
		if (classSnippet != null) {
			res.add(classSnippet);
		}
		CompletionItem interfaceSnippet = getInterfaceSnippet(cu);
		if (interfaceSnippet != null) {
			res.add(interfaceSnippet);
		}
		return res;
	}

	private static CompletionItem getClassSnippet(ICompilationUnit cu) {
		final CompletionItem classSnippetItem = new CompletionItem();
		classSnippetItem.setLabel(CLASS_SNIPPET_LABEL);
		classSnippetItem.setFilterText(CLASS_SNIPPET_LABEL);
		classSnippetItem.setSortText(SortTextHelper.convertRelevance(1));

		try {
			classSnippetItem.setInsertText(StubUtility.getSnippetContent(cu, CodeGenerationTemplate.CLASSSNIPPET, cu.findRecommendedLineSeparator(), isSnippetStringSupported()));
			setFields(classSnippetItem, cu);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			return null;
		}
		return classSnippetItem;
	}

	private static CompletionItem getInterfaceSnippet(ICompilationUnit cu) {
		final CompletionItem interfaceSnippetItem = new CompletionItem();
		interfaceSnippetItem.setFilterText(INTERFACE_SNIPPET_LABEL);
		interfaceSnippetItem.setLabel(INTERFACE_SNIPPET_LABEL);
		interfaceSnippetItem.setSortText(SortTextHelper.convertRelevance(0));

		try {
			interfaceSnippetItem.setInsertText(StubUtility.getSnippetContent(cu, CodeGenerationTemplate.INTERFACESNIPPET, cu.findRecommendedLineSeparator(), isSnippetStringSupported()));
			setFields(interfaceSnippetItem, cu);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			return null;
		}
		return interfaceSnippetItem;
	}

	private static boolean isSnippetStringSupported() {
		return JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences() != null
				&& JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isCompletionSnippetsSupported();
	}

	private static void setFields(CompletionItem ci, ICompilationUnit cu) {
		ci.setKind(CompletionItemKind.Snippet);
		ci.setInsertTextFormat(InsertTextFormat.Snippet);
		ci.setDocumentation(ci.getInsertText());
		Map<String, String> data = new HashMap<>(3);
		data.put(CompletionResolveHandler.DATA_FIELD_URI, JDTUtils.toURI(cu));
		data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, "0");
		data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, "0");
		ci.setData(data);
	}
}
