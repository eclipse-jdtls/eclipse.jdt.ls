/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - extract to a base class
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.template.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.corext.template.java.CompilationUnitContext;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.CompletionUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.contentassist.SnippetUtils;
import org.eclipse.jdt.ls.core.internal.contentassist.SortTextHelper;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponse;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class PostfixTemplateEngine {
	private static String Switch_Name = "switch"; //$NON-NLS-1$
	private static String Switch_Default = "switch case statement"; //$NON-NLS-1$

	private static String NEW_RECORD_TEMPLATE_NAME = "new_record"; //$NON-NLS-1$
	private ASTNode currentNode;

	private ASTNode parentNode;

	private CompletionContext completionCtx;

	public void setASTNodes(ASTNode currentNode, ASTNode parentNode) {
		this.currentNode= currentNode;
		this.parentNode= parentNode;
	}

	public void setContext(CompletionContext context) {
		this.completionCtx= context;
	}

	/**
	 * Return the post fix completion items.
	 * @param document The IDocument instance.
	 * @param offset offset where the completion action happens.
	 * @param compilationUnit the compilation unit.
	 * @param completionItemDefaults the completion itemDefaults
	 */
	public List<CompletionItem> complete(IDocument document, int offset, ICompilationUnit compilationUnit, CompletionItemDefaults completionItemDefaults) {
		List<CompletionItem> res = new ArrayList<>();
		JavaPostfixContextType type = (JavaPostfixContextType) JavaLanguageServerPlugin.getInstance()
				.getTemplateContextRegistry().getContextType(JavaPostfixContextType.ID_ALL);
		JavaPostfixContext context = type.createContext(document, offset, 0, compilationUnit, currentNode, parentNode, completionCtx);
		int length = context.getEnd() - context.getStart();
		Range range = null;
		try {
			range = JDTUtils.toRange(compilationUnit, context.getStart(), length);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return res;
		}

		Template[] templates = JavaLanguageServerPlugin.getInstance().getTemplateStore().getTemplates(JavaPostfixContextType.ID_ALL);
		Template[] availableTemplates = Arrays.stream(templates).filter(context::canEvaluate).toArray(Template[]::new);
		boolean needsCheck = !isJava12OrHigherProject(compilationUnit);
		CompletionResponse response = new CompletionResponse();
		List<CompletionProposal> proposals = new ArrayList<>();
		int i = 0;
		for (Template template : availableTemplates) {
			if (!canEvaluate(context, template, needsCheck)) {
				continue;
			}
			final CompletionItem item = new CompletionItem();
			item.setLabel(template.getName());
			item.setKind(CompletionItemKind.Snippet);

			CompletionUtils.setInsertTextFormat(item, completionItemDefaults);
			CompletionUtils.setInsertTextMode(item, completionItemDefaults);

			String content = "";
			if (isCompletionLazyResolveTextEditEnabled()) {
				content = SnippetUtils.templateToSnippet(template.getPattern());
			} else {
				context.setActiveTemplateName(template.getName());
				content = evaluateGenericTemplate(context, template);
			}
			setTextEdit(item, content, completionItemDefaults);

			if (!getClientPreferences().isResolveAdditionalTextEditsSupport()) {
				setAdditionalTextEdit(item, compilationUnit, context, range, template);
			}

			if (isCompletionItemLabelDetailsSupport()) {
				CompletionItemLabelDetails itemLabelDetails = new CompletionItemLabelDetails();
				itemLabelDetails.setDescription(template.getDescription());
				item.setLabelDetails(itemLabelDetails);
			}

			if (!getClientPreferences().isCompletionResolveDetailSupport()) {
				item.setDetail(template.getDescription());
			}

			if (!getClientPreferences().isCompletionResolveDocumentSupport()) {
				item.setDocumentation(SnippetUtils.beautifyDocument(content));
			}

			// we hope postfix shows at the bottom of the completion list.
			item.setSortText(SortTextHelper.convertRelevance(0));

			Map<String, String> data = new HashMap<>(2);
			data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, String.valueOf(response.getId()));
			data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, String.valueOf(i++));
			item.setData(data);

			proposals.add(new PostfixCompletionProposal(template, context));
			res.add(item);
		}

		response.setProposals(proposals);
		response.setItems(res);
		response.setCommonData(CompletionResolveHandler.DATA_FIELD_URI, JDTUtils.toURI(compilationUnit));
		CompletionResponses.store(response);
		return res;
	}

	private void setTextEdit(final CompletionItem item, String content, CompletionItemDefaults completionItemDefaults) {
		if (getClientPreferences().isCompletionListItemDefaultsSupport() && completionItemDefaults.getEditRange() != null) {
			item.setTextEditText(content);
		} else {
			item.setInsertText(content);
		}
	}

	public static void setAdditionalTextEdit(final CompletionItem item, ICompilationUnit compilationUnit,
			JavaPostfixContext context, Range range, Template template) {
		List<TextEdit> additionalEdits = new ArrayList<>();
		// use additional test edit to remove the code that needs to be replaced
		additionalEdits.add(new TextEdit(range, ""));
		List<org.eclipse.text.edits.TextEdit> jdtTextEdits = context.getAdditionalTextEdits(template.getName());
		if (jdtTextEdits != null && !jdtTextEdits.isEmpty()) {
			for (org.eclipse.text.edits.TextEdit edit : jdtTextEdits) {
				TextEditConverter converter = new TextEditConverter(compilationUnit, edit);
				additionalEdits.addAll(converter.convert());
			}
		}
		item.setAdditionalTextEdits(additionalEdits);
	}

	private boolean isCompletionItemLabelDetailsSupport() {
		return JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isCompletionItemLabelDetailsSupport();
	}

	/**
	 * see
	 * org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine.isTemplateAllowed(Template,
	 * CompilationUnitContext)
	 */
	private boolean isTemplateAllowed(Template template, CompilationUnitContext context) {
		if (Switch_Name.equals(template.getName())) {
			if (Switch_Default.equals(template.getDescription())) {
				return true;
			}
			return false;
		}
		if (NEW_RECORD_TEMPLATE_NAME.equals(template.getName()) && JavaModelUtil.is16OrHigher(context.getJavaProject())) {
			return true;
		}
		return true;
	}

	/**
	 * see
	 * org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine.canEvaluate(CompilationUnitContext,
	 * Template, boolean)
	 */
	private boolean canEvaluate(CompilationUnitContext context, Template template, boolean needsCheck) {
		if (!needsCheck) {
			return context.canEvaluate(template);
		}
		if (isTemplateAllowed(template, context)) {
			return context.canEvaluate(template);
		}
		return false;
	}

	/**
	 * see
	 * org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine.isJava12OrHigherProject(ICompilationUnit)
	 */
	private boolean isJava12OrHigherProject(ICompilationUnit compUnit) {
		if (compUnit != null) {
			IJavaProject javaProject = compUnit.getJavaProject();
			return JavaModelUtil.is12OrHigher(javaProject);
		}
		return false;
	}

	/**
	 * @See org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateProposal#apply(org.eclipse.jface.text.ITextViewer, char, int, int)
	 */
	public static String evaluateGenericTemplate(JavaPostfixContext postfixContext, Template template) {
		TemplateBuffer buffer = null;
		try {
			buffer = postfixContext.evaluate(template);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return null;
		}
		if (buffer == null) {
			return null;
		}
		String content = buffer.getString();
		if (Strings.containsOnlyWhitespaces(content)) {
			return null;
		}
		return content;
	}

	private ClientPreferences getClientPreferences() {
		return JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences();
	}

	private boolean isCompletionLazyResolveTextEditEnabled() {
		return JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isCompletionLazyResolveTextEditEnabled();
	}
}
