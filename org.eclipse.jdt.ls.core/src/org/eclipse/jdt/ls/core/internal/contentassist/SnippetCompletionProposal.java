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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
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
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.preferences.CodeGenerationTemplate;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;

import com.google.common.collect.Sets;

public class SnippetCompletionProposal {
	private static final String CLASS_SNIPPET_LABEL = "class";
	private static final String INTERFACE_SNIPPET_LABEL = "interface";
	private static final String CLASS_KEYWORD = "class";
	private static final String INTERFACE_KEYWORD = "interface";
	private static final Set<String> UNSUPPORTED_RESOURCES = Sets.newHashSet("module-info.java", "package-info.java");

	private static String PACKAGEHEADER = "package_header";
	private static String CURSOR = "cursor";

	public static List<CompletionItem> getSnippets(ICompilationUnit cu, CompletionContext completionContext, IProgressMonitor monitor) {
		if (cu == null) {
			throw new IllegalArgumentException("Compilation unit must not be null"); //$NON-NLS-1$
		}
		char[] completionToken = completionContext.getToken();
		boolean isInterfacePrefix = true;
		boolean isClassPrefix = true;
		if (completionToken != null && completionToken.length > 0) {
			String prefix = new String(completionToken);
			isInterfacePrefix = INTERFACE_KEYWORD.startsWith(prefix);
			isClassPrefix = CLASS_KEYWORD.startsWith(prefix);
		}
		if (!isInterfacePrefix && !isClassPrefix) {
			return Collections.emptyList();
		}
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		//This check might need to be pushed back to the different get*Snippet() methods, depending on future features
		if (!isSnippetStringSupported() || UNSUPPORTED_RESOURCES.contains(cu.getResource().getName())) {
			return Collections.emptyList();
		}
		boolean needsPublic = needsPublic(cu, completionContext, monitor);
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		List<CompletionItem> res = new ArrayList<>(2);
		if (isClassPrefix) {
			CompletionItem classSnippet = getClassSnippet(cu, completionContext, needsPublic, monitor);
			if (classSnippet != null) {
				res.add(classSnippet);
			}
		}
		if (isInterfacePrefix) {
			CompletionItem interfaceSnippet = getInterfaceSnippet(cu, completionContext, needsPublic, monitor);
			if (interfaceSnippet != null) {
				res.add(interfaceSnippet);
			}
		}
		return res;
	}

	private static boolean accept(ICompilationUnit cu, CompletionContext completionContext, boolean acceptClass) {
		if (completionContext != null && completionContext.isExtended()) {
			if (completionContext.isInJavadoc()) {
				return false;
			}
			if (completionContext instanceof InternalCompletionContext) {
				InternalCompletionContext internalCompletionContext = (InternalCompletionContext) completionContext;
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
						org.eclipse.jdt.core.dom.ASTNode astNode = ASTNodeSearchUtil.getAstNode(ast, completionContext.getTokenStart(), completionContext.getTokenEnd() - completionContext.getTokenStart() + 1);
						return (astNode == null || (astNode.getParent() instanceof ExpressionStatement));
					}
					return true;
				}
			}
		}
		return false;
	}

	private static boolean needsPublic(ICompilationUnit cu, CompletionContext completionContext, IProgressMonitor monitor) {
		if (completionContext != null && completionContext.isExtended()) {
			if (completionContext.isInJavadoc()) {
				return false;
			}
			if (completionContext instanceof InternalCompletionContext) {
				InternalCompletionContext internalCompletionContext = (InternalCompletionContext) completionContext;
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
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
					if (node instanceof CompletionOnSingleNameReference) {
						CompilationUnit ast = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
						if (monitor.isCanceled()) {
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

	private static CompletionItem getClassSnippet(ICompilationUnit cu, CompletionContext completionContext, boolean needsPublic, IProgressMonitor monitor) {
		if (!accept(cu, completionContext, true)) {
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
			if (needsPublic) {
				classSnippetItem.setInsertText(getSnippetContent(cu, CodeGenerationTemplate.CLASSSNIPPET_PUBLIC, cu.findRecommendedLineSeparator(), true));
			} else {
				classSnippetItem.setInsertText(getSnippetContent(cu, CodeGenerationTemplate.CLASSSNIPPET_DEFAULT, cu.findRecommendedLineSeparator(), true));
			}
			setFields(classSnippetItem, cu);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e.getStatus());
			return null;
		}
		return classSnippetItem;
	}

	private static CompletionItem getInterfaceSnippet(ICompilationUnit cu, CompletionContext completionContext, boolean needsPublic, IProgressMonitor monitor) {
		if (!accept(cu, completionContext, false)) {
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
			if (needsPublic) {
				interfaceSnippetItem.setInsertText(getSnippetContent(cu, CodeGenerationTemplate.INTERFACESNIPPET_PUBLIC, cu.findRecommendedLineSeparator(), true));
			} else {
				interfaceSnippetItem.setInsertText(getSnippetContent(cu, CodeGenerationTemplate.INTERFACESNIPPET_DEFAULT, cu.findRecommendedLineSeparator(), true));
			}
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

	private static String getSnippetContent(ICompilationUnit cu, CodeGenerationTemplate templateSetting, String lineDelimiter, boolean snippetStringSupport) throws CoreException {
		Template template = templateSetting.createTemplate(cu.getJavaProject());
		if (template == null) {
			return null;
		}
		CodeTemplateContext context = new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);

		IPackageDeclaration[] packageDeclarations = cu.getPackageDeclarations();
		String packageName = cu.getParent().getElementName();
		String packageHeader = ((packageName != null && !packageName.isEmpty()) && (packageDeclarations == null || packageDeclarations.length == 0)) ? "package " + packageName + ";\n\n" : "";
		context.setVariable(PACKAGEHEADER, packageHeader);
		String typeName = JavaCore.removeJavaLikeExtension(cu.getElementName());
		List<IType> types = Arrays.asList(cu.getAllTypes());
		int postfix = 0;
		while (types != null && !types.isEmpty() && types.stream().filter(isTypeExists(typeName)).findFirst().isPresent()) {
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

}
