/*******************************************************************************
 * Copyright (c) 2018-2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *     Red Hat, Inc. - added record snippet
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.codeassist.InternalCompletionContext;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnFieldType;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnKeyword2;
import org.eclipse.jdt.internal.codeassist.complete.CompletionOnSingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContext;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.corext.dom.TokenScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.template.java.JavaContextType;
import org.eclipse.jdt.ls.core.internal.corext.template.java.JavaPostfixContextType;
import org.eclipse.jdt.ls.core.internal.corext.template.java.PostfixCompletionProposalComputer;
import org.eclipse.jdt.ls.core.internal.corext.template.java.PostfixTemplateEngine;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponse;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.preferences.CodeGenerationTemplate;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.templates.DocumentTemplateContext;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SnippetCompletionProposal extends CompletionProposal {
	private static final String CLASS_SNIPPET_LABEL = "class";
	private static final String INTERFACE_SNIPPET_LABEL = "interface";
	private static final String RECORD_SNIPPET_LABEL = "record";
	private static final String CLASS_KEYWORD = "class";
	private static final String INTERFACE_KEYWORD = "interface";
	private static final String RECORD_KEYWORD = "record";

	private static String PACKAGEHEADER = "package_header";
	private static String CURSOR = "cursor";

	private Template template;

	public SnippetCompletionProposal(Template template) {
		this.template = template;
	}

	public Template getTemplate() {
		return this.template;
	}

	private static class SnippetCompletionContext {
		private ICompilationUnit cu;
		private Boolean needsPublic;
		private CompletionContext completionContext;
		private String packageHeader;
		private String recommendedLineSeprator;

		SnippetCompletionContext(ICompilationUnit cu, CompletionContext completionContext) {
			this.cu = cu;
			this.completionContext = completionContext;
		}

		ICompilationUnit getCompilationUnit() {
			return cu;
		}

		boolean needsPublic(IProgressMonitor monitor) {
			if (needsPublic == null) {
				needsPublic = needsPublic(cu, getCompletionContext(), monitor);
			}
			return needsPublic;
		}

		private static boolean needsPublic(ICompilationUnit cu, CompletionContext completionContext, IProgressMonitor monitor) {
			if (completionContext != null && completionContext.isExtended()) {
				if (completionContext.isInJavadoc()) {
					return false;
				}
				if (completionContext instanceof InternalCompletionContext internalCompletionContext) {
					ASTNode node = internalCompletionContext.getCompletionNode();
					if (node instanceof CompletionOnKeyword2 || node instanceof CompletionOnFieldType || node instanceof CompletionOnSingleNameReference) {
						if (completionContext.getEnclosingElement() instanceof IMethod) {
							return false;
						}
						try {
							TokenScanner scanner = new TokenScanner(cu);
							int curr = scanner.readNext(0, true);
							int previous = curr;
							while (scanner.getCurrentEndOffset() < completionContext.getTokenStart()) {
								previous = curr;
								if (monitor.isCanceled()) {
									return false;
								}
								if (curr == ITerminalSymbols.TokenNameEOF) {
									break;
								}
								try {
									curr = scanner.readNext(true);
								} catch (CoreException e) {
									// ignore
								}
							}
							if (scanner.isModifier(previous)) {
								return false;
							}
						} catch (CoreException e) {
							if (e.getStatus().getCode() != TokenScanner.END_OF_FILE) {
								JavaLanguageServerPlugin.logException(e.getMessage(), e);
							}
						}
						if (node instanceof CompletionOnSingleNameReference) {
							CompilationUnit ast = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
							if (ast == null || monitor.isCanceled()) {
								return false;
							}
							org.eclipse.jdt.core.dom.ASTNode astNode = ASTNodeSearchUtil.getAstNode(ast, completionContext.getOffset(), 1);
							if (astNode == null) {
								return false;
							}
							while (astNode != null) {
								if (astNode instanceof Initializer) {
									return false;
								}
								astNode = astNode.getParent();
							}
						}
						return true;
					}
				}
			}
			return false;
		}

		CompletionContext getCompletionContext() {
			return completionContext;
		}

		String getPackageHeader() throws JavaModelException {
			if (packageHeader == null) {
				IPackageDeclaration[] packageDeclarations = cu.getPackageDeclarations();
				String packageName = cu.getParent().getElementName();
				packageHeader = ((packageName != null && !packageName.isEmpty()) && (packageDeclarations == null || packageDeclarations.length == 0)) ? "package " + packageName + ";\n\n" : "";
			}
			return packageHeader;
		}

		String getRecommendedLineSeprator() throws JavaModelException {
			if (recommendedLineSeprator == null) {
				recommendedLineSeprator = cu.findRecommendedLineSeparator();
			}
			return recommendedLineSeprator;
		}

	}

	public static List<CompletionItem> getSnippets(ICompilationUnit cu, CompletionProposalRequestor collector, IProgressMonitor monitor) throws JavaModelException {
		CompletionContext completionContext = collector.getContext();
		if (cu == null) {
			throw new IllegalArgumentException("Compilation unit must not be null"); //$NON-NLS-1$
		}

		List<CompletionItem> res = new ArrayList<>();
		SnippetCompletionContext scc = new SnippetCompletionContext(cu, completionContext);
		res.addAll(getGenericSnippets(scc, collector.getCompletionItemDefaults()));
		res.addAll(getTypeDefinitionSnippets(scc, monitor, collector.getCompletionItemDefaults()));
		res.addAll(getPostfixSnippets(scc, collector.getCompletionItemDefaults()));

		return res;
	}

	/**
	 * Return the list of postfix completion items.
	 * @param scc Snippet completion context.
	 */
	private static List<CompletionItem> getPostfixSnippets(SnippetCompletionContext scc, CompletionItemDefaults completionItemDefaults) {
		if (!isPostfixSupported()) {
			return Collections.emptyList();
		}

		CompletionContext jdtCtx = scc.getCompletionContext();
		try {
			IDocument document = JsonRpcHelpers.toDocument(scc.getCompilationUnit().getBuffer());
			if (canResolvePostfix(jdtCtx, document)) {
				PostfixCompletionProposalComputer computer = new PostfixCompletionProposalComputer();
				PostfixTemplateEngine engine = computer.computeCompletionEngine(jdtCtx, document, jdtCtx.getOffset());
				if (engine != null) {
					return engine.complete(document, jdtCtx.getOffset(), scc.getCompilationUnit(), completionItemDefaults);
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return Collections.emptyList();
	}

	private static boolean isPostfixSupported() {
		return JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isPostfixCompletionEnabled();
	}

	/**
	 * Check if it's able to resolve the postfix completion. The postfix completion
	 * will only happen when:
	 * <ul>
	 * <li>The completion is triggered by the trigger character '.'</li>
	 * <li>The postfix template start with the completion token </li>
	 * </ul>
	 */
	private static boolean canResolvePostfix(CompletionContext jdtCtx, IDocument document) throws BadLocationException {
		char[] token = jdtCtx.getToken();
		if (token == null) {
			return false;
		}

		int tokenStart = jdtCtx.getOffset() - token.length - 1;
		if (tokenStart < 0) {
			return false;
		}

		String tokenSequence = document.get(tokenStart, jdtCtx.getOffset() - tokenStart);
		if (!tokenSequence.startsWith(".")) {
			return false;
		}

		Template[] templates = JavaLanguageServerPlugin.getInstance().getTemplateStore().getTemplates(JavaPostfixContextType.ID_ALL);
		return Arrays.stream(templates).anyMatch(t -> t.getName().toLowerCase().startsWith((new String(token)).toLowerCase()));
	}

	private static List<CompletionItem> getGenericSnippets(SnippetCompletionContext scc, CompletionItemDefaults completionItemDefaults) throws JavaModelException {
		CompletionResponse response = new CompletionResponse();
		response.setContext(scc.getCompletionContext());
		response.setOffset(scc.getCompletionContext().getOffset());

		List<CompletionItem> res = new ArrayList<>();
		CompletionContext completionContext = scc.getCompletionContext();
		char[] completionToken = completionContext.getToken();
		if (completionToken == null) {
			return Collections.emptyList();
		}
		int tokenLocation = completionContext.getTokenLocation();
		JavaContextType contextType = (JavaContextType) JavaLanguageServerPlugin.getInstance().getTemplateContextRegistry().getContextType(JavaContextType.ID_STATEMENTS);
		if (contextType == null) {
			return Collections.emptyList();
		}
		ICompilationUnit cu = scc.getCompilationUnit();
		IDocument document = new Document(cu.getSource());
		DocumentTemplateContext javaContext = contextType.createContext(document, completionContext.getOffset(), completionToken.length, cu);
		Template[] templates = null;
		if ((tokenLocation & CompletionContext.TL_STATEMENT_START) != 0) {
			templates = JavaLanguageServerPlugin.getInstance().getTemplateStore().getTemplates(JavaContextType.ID_STATEMENTS);
		} else {
			// We only support statement templates for now.
		}

		if (templates == null || templates.length == 0) {
			return Collections.emptyList();
		}

		String uri = JDTUtils.toURI(cu);
		Template[] availableTemplates = Arrays.stream(templates).filter(t -> javaContext.canEvaluate(t)).toArray(Template[]::new);
		List<CompletionProposal> proposals = new ArrayList<>();
		for (int i = 0; i < availableTemplates.length; i++) {
			Template template = availableTemplates[i];
			final CompletionItem item = new CompletionItem();
			item.setLabel(template.getName());
			item.setKind(CompletionItemKind.Snippet);

			if (isCompletionListItemDefaultsSupport() &&
				completionItemDefaults.getInsertTextFormat() != null &&
				completionItemDefaults.getInsertTextFormat() == InsertTextFormat.Snippet
			) {
				item.setInsertTextFormat(null);
			} else {
				item.setInsertTextFormat(InsertTextFormat.Snippet);
			}

			if (isCompletionLazyResolveTextEditEnabled()) {
				String insertText = SnippetUtils.templateToSnippet(template.getPattern());
				if (isCompletionListItemDefaultsSupport() && completionItemDefaults.getEditRange() != null) {
					item.setTextEditText(insertText);
				} else {
					item.setInsertText(insertText);
				}
			} else {
				String content = SnippetCompletionProposal.evaluateGenericTemplate(cu, completionContext, template);
				if (isCompletionListItemDefaultsSupport() && completionItemDefaults.getEditRange() != null) {
					item.setTextEditText(content);
				} else {
					setTextEdit(completionContext, cu, item, content);
				}
			}

			item.setDetail(template.getDescription());
			if (isCompletionItemLabelDetailsSupport()){
				CompletionItemLabelDetails itemLabelDetails = new CompletionItemLabelDetails();
				itemLabelDetails.setDescription(template.getDescription());
				item.setLabelDetails(itemLabelDetails);
			}

			Map<String, String> data = new HashMap<>(2);
			data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, String.valueOf(response.getId()));
			data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, String.valueOf(i));
			item.setData(data);

			proposals.add(i, new SnippetCompletionProposal(template));
			res.add(item);
		}

		response.setProposals(proposals);
		response.setItems(res);
		response.setCommonData(CompletionResolveHandler.DATA_FIELD_URI, uri);
		CompletionResponses.store(response);
		return res;
	}

	/**
	 * Set the text edit for the completion item.
	 * @param completionContext the completion context
	 * @param cu the compilation unit
	 * @param item the completion item
	 * @param content the new text of the text edit
	 * @throws JavaModelException
	 */
	public static void setTextEdit(CompletionContext completionContext, ICompilationUnit cu, final CompletionItem item, String content) throws JavaModelException {
		int length = completionContext.getTokenEnd() - completionContext.getTokenStart() + 1;
		Range range = JDTUtils.toRange(cu, completionContext.getTokenStart(), length);
		TextEdit textEdit = new TextEdit(range, content);
		item.setTextEdit(Either.forLeft(textEdit));
	}

	private static boolean isCompletionItemLabelDetailsSupport() {
		return JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isCompletionItemLabelDetailsSupport();
	}

	public static String evaluateGenericTemplate(ICompilationUnit cu, CompletionContext completionContext, Template template) {
		JavaContextType contextType = (JavaContextType) JavaLanguageServerPlugin.getInstance().getTemplateContextRegistry().getContextType(JavaContextType.ID_STATEMENTS);
		char[] completionToken = completionContext.getToken();
		if (contextType == null || completionToken == null) {
			return null;
		}

		TemplateBuffer buffer = null;
		try {
			IDocument document = new Document(cu.getSource());
			DocumentTemplateContext javaContext = contextType.createContext(document, completionContext.getOffset(), completionToken.length, cu);
			buffer = javaContext.evaluate(template);
		} catch (JavaModelException | BadLocationException | TemplateException e) {
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

	private static List<CompletionItem> getTypeDefinitionSnippets(SnippetCompletionContext scc, IProgressMonitor monitor, CompletionItemDefaults completionItemDefaults) throws JavaModelException {
		char[] completionToken = scc.getCompletionContext().getToken();
		boolean isInterfacePrefix = true;
		boolean isClassPrefix = true;
		boolean isRecordPrefix = true;
		if (completionToken != null && completionToken.length > 0) {
			String prefix = new String(completionToken);
			isInterfacePrefix = INTERFACE_KEYWORD.startsWith(prefix);
			isClassPrefix = CLASS_KEYWORD.startsWith(prefix);
			isRecordPrefix = RECORD_KEYWORD.startsWith(prefix);
		}
		if (!isInterfacePrefix && !isClassPrefix && !isRecordPrefix) {
			return Collections.emptyList();
		}
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		List<CompletionItem> res = new ArrayList<>(3);
		if (isClassPrefix) {
			CompletionItem classSnippet = getClassSnippet(scc, monitor);
			if (classSnippet != null) {
				res.add(classSnippet);
			}
		}
		if (isInterfacePrefix) {
			CompletionItem interfaceSnippet = getInterfaceSnippet(scc, monitor);
			if (interfaceSnippet != null) {
				res.add(interfaceSnippet);
			}
		}
		if (isRecordPrefix) {
			CompletionItem recordSnippet = getRecordSnippet(scc, monitor);
			if (recordSnippet != null) {
				res.add(recordSnippet);
			}
		}

		for (CompletionItem item : res) {
			setFields(item, scc.getCompilationUnit(), completionItemDefaults);
		}
		return res;
	}

	private static boolean accept(ICompilationUnit cu, CompletionContext completionContext, boolean acceptClass) {
		if (completionContext != null && completionContext.isExtended()) {
			if (completionContext.isInJavadoc()) {
				return false;
			}
			if (completionContext instanceof InternalCompletionContext internalCompletionContext) {
				ASTNode node = internalCompletionContext.getCompletionNode();
				if (node instanceof CompletionOnKeyword2) {
					return true;
				}
				if (node instanceof CompletionOnFieldType) {
					return true;
				}
				if (acceptClass && node instanceof CompletionOnSingleNameReference) {
					if (completionContext.getEnclosingElement() instanceof IMethod) {
						CompilationUnit ast = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
						if (ast == null) {
							return true;
						}
						org.eclipse.jdt.core.dom.ASTNode astNode = ASTNodeSearchUtil.getAstNode(ast, completionContext.getTokenStart(), completionContext.getTokenEnd() - completionContext.getTokenStart() + 1);
						return (astNode == null || (astNode.getParent() instanceof ExpressionStatement));
					}
					return true;
				}
			}
		}
		return false;
	}

	private static CompletionItem getClassSnippet(SnippetCompletionContext scc, IProgressMonitor monitor) {
		ICompilationUnit cu = scc.getCompilationUnit();
		if (!accept(cu, scc.getCompletionContext(), true)) {
			return null;
		}
		if (monitor.isCanceled()) {
			return null;
		}
		final CompletionItem classSnippetItem = new CompletionItem();
		classSnippetItem.setLabel(CLASS_SNIPPET_LABEL);
		classSnippetItem.setFilterText(CLASS_SNIPPET_LABEL);
		classSnippetItem.setSortText(SortTextHelper.convertRelevance(1));

		try {
			CodeGenerationTemplate template = (scc.needsPublic(monitor)) ? CodeGenerationTemplate.CLASSSNIPPET_PUBLIC : CodeGenerationTemplate.CLASSSNIPPET_DEFAULT;
			classSnippetItem.setInsertText(getSnippetContent(scc, template, true));
			if (isCompletionListItemDefaultsSupport()) {
				classSnippetItem.setTextEditText(getSnippetContent(scc, template, true));
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			return null;
		}
		return classSnippetItem;
	}

	private static CompletionItem getInterfaceSnippet(SnippetCompletionContext scc, IProgressMonitor monitor) {
		ICompilationUnit cu = scc.getCompilationUnit();
		if (!accept(cu, scc.completionContext, false)) {
			return null;
		}
		if (monitor.isCanceled()) {
			return null;
		}
		final CompletionItem interfaceSnippetItem = new CompletionItem();
		interfaceSnippetItem.setFilterText(INTERFACE_SNIPPET_LABEL);
		interfaceSnippetItem.setLabel(INTERFACE_SNIPPET_LABEL);
		interfaceSnippetItem.setSortText(SortTextHelper.convertRelevance(0));

		try {
			CodeGenerationTemplate template = ((scc.needsPublic(monitor))) ? CodeGenerationTemplate.INTERFACESNIPPET_PUBLIC : CodeGenerationTemplate.INTERFACESNIPPET_DEFAULT;
			interfaceSnippetItem.setInsertText(getSnippetContent(scc, template, true));
			if (isCompletionListItemDefaultsSupport()) {
				interfaceSnippetItem.setTextEditText(getSnippetContent(scc, template, true));
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			return null;
		}
		return interfaceSnippetItem;
	}

	private static CompletionItem getRecordSnippet(SnippetCompletionContext scc, IProgressMonitor monitor) {
		ICompilationUnit cu = scc.getCompilationUnit();
		IJavaProject javaProject = cu.getJavaProject();
		if (javaProject == null) {
			return null;
		}
		String version = javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, true);
		if (JavaModelUtil.isVersionLessThan(version, JavaCore.VERSION_14)) {
			//not checking if preview features are enabled, as Java 14+ might support records without preview flag
			return null;
		}
		if (!accept(cu, scc.getCompletionContext(), false /* Or should it be true???*/)) {
			return null;
		}
		if (monitor.isCanceled()) {
			return null;
		}
		final CompletionItem recordSnippet = new CompletionItem();
		recordSnippet.setFilterText(RECORD_SNIPPET_LABEL);
		recordSnippet.setLabel(RECORD_SNIPPET_LABEL);
		recordSnippet.setSortText(SortTextHelper.convertRelevance(0));

		try {
			CodeGenerationTemplate template = (scc.needsPublic(monitor)) ? CodeGenerationTemplate.RECORDSNIPPET_PUBLIC : CodeGenerationTemplate.RECORDSNIPPET_DEFAULT;
			recordSnippet.setInsertText(getSnippetContent(scc, template, true));
			if (isCompletionListItemDefaultsSupport()) {
				recordSnippet.setTextEditText(getSnippetContent(scc, template, true));
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			return null;
		}
		return recordSnippet;
	}

	private static void setFields(CompletionItem ci, ICompilationUnit cu, CompletionItemDefaults completionItemDefaults) {
		ci.setKind(CompletionItemKind.Snippet);
		ci.setInsertTextFormat(InsertTextFormat.Snippet);
		if (completionItemDefaults.getInsertTextFormat() != null && completionItemDefaults.getInsertTextFormat() == InsertTextFormat.Snippet){
			ci.setInsertTextFormat(null);
		}
		ci.setDocumentation(SnippetUtils.beautifyDocument(ci.getInsertText()));
	}

	private static String getSnippetContent(SnippetCompletionContext scc, CodeGenerationTemplate templateSetting, boolean snippetStringSupport) throws CoreException {
		ICompilationUnit cu = scc.getCompilationUnit();
		Template template = templateSetting.createTemplate();
		if (template == null) {
			return null;
		}
		CodeTemplateContext context = new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), scc.getRecommendedLineSeprator());

		context.setVariable(PACKAGEHEADER, scc.getPackageHeader());
		String typeName = JavaCore.removeJavaLikeExtension(cu.getElementName());
		List<IType> types = Arrays.asList(cu.getAllTypes());
		int postfix = 0;
		while (!types.isEmpty() && types.stream().filter(isTypeExists(typeName)).findFirst().isPresent()) {
			typeName = "Inner" + JavaCore.removeJavaLikeExtension(cu.getElementName()) + (postfix == 0 ? "" : "_" + postfix);
			postfix++;
		}
		if (postfix > 0 && snippetStringSupport) {
			context.setVariable(CodeTemplateContextType.TYPENAME, "${1:" + typeName + "}");
		} else {
			context.setVariable(CodeTemplateContextType.TYPENAME, typeName);
		}
		context.setVariable(CURSOR, snippetStringSupport ? "${0}" : "");

		// TODO Consider making evaluateTemplate public in StubUtility
		TemplateBuffer buffer;
		try {
			buffer = context.evaluate(template);
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		if (buffer == null) {
			return null;
		}
		String str = buffer.getString();
		if (Strings.containsOnlyWhitespaces(str)) {
			return null;
		}
		return str;
	}

	private static Predicate<IType> isTypeExists(String typeName) {
		return type -> type.getElementName().equals(typeName);
	}

	private static boolean isCompletionListItemDefaultsSupport() {
		return JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isCompletionListItemDefaultsSupport();
	}
	
	private static boolean isCompletionLazyResolveTextEditEnabled() {
		return JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isCompletionLazyResolveTextEditEnabled();
	}
}
