/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.contentassist;

import static org.eclipse.jdt.ls.core.internal.contentassist.TypeProposalUtils.isImplicitImport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.CompletionUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.TextEdit;

/**
 * Utility to calculate the completion replacement string based on JDT Core
 * {@link CompletionProposal}. This class is based on the implementation of JDT
 * UI <code>AbstractJavaCompletionProposal</code> and its subclasses.
 *
 * @author aboyko
 *
 * Copied from Flux project.
 *
 */
public class CompletionProposalReplacementProvider {

	private static final String CURSOR_POSITION = "${0}";
	private static final char SPACE = ' ';
	private static final char LPAREN = '(';
	private static final char RPAREN = ')';
	private static final char SEMICOLON = ';';
	private static final char COMMA = ',';
	private static final char LESS = '<';
	private static final char GREATER = '>';
	private final ICompilationUnit compilationUnit;
	private final int offset;
	private final CompletionContext context;
	private ImportRewrite importRewrite;
	private final ClientPreferences client;
	private Preferences preferences;
	private String anonymousTypeNewBody;
	/**
	 * whether the provider is used during `completionItem/resolve` request.
	 */
	private boolean isResolvingRequest;

	public CompletionProposalReplacementProvider(ICompilationUnit compilationUnit, CompletionContext context, int offset,
			Preferences preferences, ClientPreferences clientPrefs, boolean isResolvingRequest) {
		super();
		this.compilationUnit = compilationUnit;
		this.context = context;
		this.offset = offset;
		this.preferences = preferences == null ? new Preferences() : preferences;
		this.client = clientPrefs;
		this.isResolvingRequest = isResolvingRequest;
	}

	/**
	 * Update the replacement.
	 * 
	 * When {@link #isResolvingRequest} is <code>true</code>, additionalTextEdits will also be resolved.
	 * @param proposal
	 * @param item
	 * @param trigger
	 */
	public void updateReplacement(CompletionProposal proposal, CompletionItem item, char trigger) {
		// reset importRewrite
		this.importRewrite = TypeProposalUtils.createImportRewrite(compilationUnit);

		List<org.eclipse.lsp4j.TextEdit> additionalTextEdits = new ArrayList<>();

		StringBuilder completionBuffer = new StringBuilder();
		InsertReplaceEdit insertReplaceEdit = new InsertReplaceEdit();
		if (isSupportingRequiredProposals(proposal)) {
			CompletionProposal[] requiredProposals = proposal.getRequiredProposals();
			if (requiredProposals != null) {
				for (CompletionProposal requiredProposal : requiredProposals) {
					switch (requiredProposal.getKind()) {
					case CompletionProposal.TYPE_IMPORT:
					case CompletionProposal.METHOD_IMPORT:
					case CompletionProposal.FIELD_IMPORT:
						appendImportProposal(completionBuffer, requiredProposal, proposal.getKind());
						break;
					case CompletionProposal.TYPE_REF:
						org.eclipse.lsp4j.TextEdit edit = toRequiredTypeEdit(requiredProposal, trigger, proposal.canUseDiamond(context));
						if (proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION
							|| proposal.getKind() == CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION
							|| proposal.getKind() == CompletionProposal.ANONYMOUS_CLASS_DECLARATION) {
								completionBuffer.append(edit.getNewText());
								setInsertReplaceRange(requiredProposal, insertReplaceEdit);
						} else {
							additionalTextEdits.add(edit);
						}
						break;
					default:
						/*
						 * In 3.3 we only support the above required proposals, see
						 * CompletionProposal#getRequiredProposals()
						 */
						Assert.isTrue(false);
					}
				}
			}
		}

		setInsertReplaceRange(proposal, insertReplaceEdit);

		switch (proposal.getKind()) {
			case CompletionProposal.METHOD_DECLARATION:
				appendMethodOverrideReplacement(completionBuffer, proposal);
				break;
			case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
				if (proposal instanceof GetterSetterCompletionProposal getterSetterProposal) {
					appendMethodPotentialReplacement(completionBuffer, getterSetterProposal);
				} else {
					appendReplacementString(completionBuffer, proposal);
				}
				break;
			case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
			case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
				appendAnonymousClass(completionBuffer, proposal, insertReplaceEdit);
				break;
			case CompletionProposal.LAMBDA_EXPRESSION:
				appendLambdaExpressionReplacement(completionBuffer, proposal);
				break;
			default:
				appendReplacementString(completionBuffer, proposal);
				break;
		}

		//select insertTextFormat.
		if (client.isCompletionSnippetsSupported()) {
			item.setInsertTextFormat(InsertTextFormat.Snippet);
		} else {
			item.setInsertTextFormat(InsertTextFormat.PlainText);
		}
		String text = completionBuffer.toString();
		if (insertReplaceEdit.getReplace() == null || insertReplaceEdit.getInsert() == null) {
			// fallback
			item.setInsertText(text);
		} else if (client.isCompletionInsertReplaceSupport()) {
			insertReplaceEdit.setNewText(text);
			item.setTextEdit(Either.forRight(insertReplaceEdit));
		} else if (preferences.isCompletionOverwrite()) {
			item.setTextEdit(Either.forLeft(new org.eclipse.lsp4j.TextEdit(insertReplaceEdit.getReplace(), text)));
		} else {
			item.setTextEdit(Either.forLeft(new org.eclipse.lsp4j.TextEdit(insertReplaceEdit.getInsert(), text)));
		}

		if (!isImportCompletion(proposal) && (!client.isResolveAdditionalTextEditsSupport() || isResolvingRequest)) {
			addImports(additionalTextEdits);
			if(!additionalTextEdits.isEmpty()){
				item.setAdditionalTextEdits(additionalTextEdits);
			}
		}
	}

	private void appendLambdaExpressionReplacement(StringBuilder completionBuffer, CompletionProposal proposal) {
		StringBuilder paramBuffer = new StringBuilder();
		appendGuessingCompletion(paramBuffer, proposal);
		boolean needParens = paramBuffer.indexOf(",") > -1 || paramBuffer.length() == 0;

		if (needParens) {
			completionBuffer.append(LPAREN);
		}
		completionBuffer.append(paramBuffer);
		if (needParens) {
			completionBuffer.append(RPAREN);
		}

		completionBuffer.append(" -> ");
		if(client.isCompletionSnippetsSupported()){
			completionBuffer.append(CURSOR_POSITION);
		}
	}

	private void appendAnonymousClass(StringBuilder completionBuffer, CompletionProposal proposal, InsertReplaceEdit edit) {
		IDocument document;
		try {
			IBuffer buffer = this.compilationUnit.getBuffer();
			document = JsonRpcHelpers.toDocument(buffer);
			char[] declarationKey = proposal.getDeclarationKey();
			if (declarationKey == null) {
				return;
			}
			IJavaProject javaProject = this.compilationUnit.getJavaProject();
			IJavaElement element = javaProject.findElement(new String(declarationKey), null);
			if (!(element instanceof IType)) {
				return;
			}
			int offset = proposal.getReplaceStart();

			if (this.anonymousTypeNewBody == null) {
				// calculate and format an empty new body
				AnonymousTypeCompletionProposal overrider = new AnonymousTypeCompletionProposal(compilationUnit, offset, client.isCompletionSnippetsSupported());
				this.anonymousTypeNewBody = overrider.updateReplacementString(document, offset);
			}
			String replacement = this.anonymousTypeNewBody;

			if (document.getLength() > offset) {
				if (proposal.getKind() == CompletionProposal.ANONYMOUS_CLASS_DECLARATION) {
					// update replacement range
					int length = 0;
					IRegion lineInfo = document.getLineInformationOfOffset(offset);
					int lineEnd = lineInfo.getOffset() + lineInfo.getLength();
					int pos = offset;
					char ch = document.getChar(pos);
					while (pos < lineEnd && pos < document.getLength() - 1 && !(ch == SEMICOLON || ch == COMMA)) {
						length++;
						pos++;
						ch = document.getChar(pos);
					}
					if (edit.getReplace() != null) {
						edit.getReplace().getEnd().setCharacter(edit.getReplace().getEnd().getCharacter() + length);
					}
					if (edit.getInsert() != null) {
						edit.getInsert().getEnd().setCharacter(edit.getInsert().getEnd().getCharacter() + length);
					}
					length = 1;
					pos = offset - 1;
					if (pos < document.getLength()) {
						int lineStart = lineInfo.getOffset();
						ch = document.getChar(pos);
						while (pos > lineStart && !(ch == LPAREN || ch == SEMICOLON || ch == COMMA)) {
							length++;
							pos--;
							ch = document.getChar(pos);
						}
					}
					if (edit.getReplace() != null) {
						edit.getReplace().getStart().setCharacter(edit.getReplace().getStart().getCharacter() - length);
					}
					if (edit.getInsert() != null) {
						edit.getInsert().getStart().setCharacter(edit.getInsert().getStart().getCharacter() - length);
					}
					replacement = checkReplacementEnd(document, replacement, offset);
				} else if (proposal.getKind() == CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION) {
					// update replacement range
					int pos = -1;
					if (document.getChar(offset) == LPAREN) {
						pos = offset;
					} else if (offset + 1 < document.getLength() && document.getChar(offset + 1) == LPAREN) {
						pos = offset + 1;
					}
					if (pos > 0 && pos < document.getLength() - 1) {
						IRegion lineInfo = document.getLineInformationOfOffset(offset);
						int length = 1;
						int lineEnd = lineInfo.getOffset() + lineInfo.getLength();
						char ch = document.getChar(pos);
						boolean hasClosed = false;
						while (pos < lineEnd && !(ch == RPAREN || ch == SEMICOLON || ch == COMMA)) {
							length++;
							pos++;
							ch = document.getChar(pos);
							if (ch == RPAREN) {
								hasClosed = true;
								break;
							}
						}
						if (!hasClosed) {
							length = 0;
							pos--;
						}
						if (length > 0) {
							if (edit.getReplace() != null) {
								edit.getReplace().getEnd().setCharacter(edit.getReplace().getEnd().getCharacter() + length);
							}
							if (edit.getInsert() != null) {
								edit.getInsert().getEnd().setCharacter(edit.getInsert().getEnd().getCharacter() + length);
							}
						}
					}
					int next = (pos > 0) ? pos + 1 : offset;
					replacement = checkReplacementEnd(document, replacement, next);
				}
			}
			completionBuffer.append(replacement);
		} catch (BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to compute anonymous class replacement", e);
		}
	}

	private String checkReplacementEnd(IDocument document, String replacement, int pos) throws BadLocationException {
		if (pos > 0 && pos < document.getLength()) {
			int nextChar = document.getChar(pos);
			while (pos > 0 && pos < document.getLength() - 1 && !(nextChar == LPAREN || nextChar == RPAREN || nextChar == SEMICOLON || nextChar == COMMA || Character.isJavaIdentifierStart(nextChar))) {
				pos++;
				nextChar = document.getChar(pos);
			}
			if (nextChar == COMMA || nextChar == SEMICOLON || nextChar == LPAREN) {
				if (replacement.endsWith(";")) {
					replacement = replacement.substring(0, replacement.length() - 1);
				}
			}
		}
		return replacement;
	}

	/**
	 * @param completionBuffer
	 * @param proposal
	 */
	private void appendMethodOverrideReplacement(StringBuilder completionBuffer, CompletionProposal proposal) {
		IDocument document;
		try {
			document = JsonRpcHelpers.toDocument(this.compilationUnit.getBuffer());
			String signature = String.valueOf(proposal.getSignature());
			String[] types = Stream.of(Signature.getParameterTypes(signature)).map(t -> Signature.toString(t))
					.toArray(String[]::new);
			String methodName = String.valueOf(proposal.getName());
			int offset = proposal.getReplaceStart();
			String completion = new String(proposal.getCompletion());
			OverrideCompletionProposal overrider = new OverrideCompletionProposal(compilationUnit, methodName, types,
					completion);
			String replacement = overrider.updateReplacementString(document, offset, importRewrite,
					client.isCompletionSnippetsSupported());
			if (replacement != null) {
				completionBuffer.append(replacement);
			}
		} catch (BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to compute override replacement", e);
		}
	}

	/**
	 * @param completionBuffer
	 * @param proposal
	 */
	private void appendMethodPotentialReplacement(StringBuilder completionBuffer, GetterSetterCompletionProposal proposal) {
		IDocument document;
		try {
			document = JsonRpcHelpers.toDocument(this.compilationUnit.getBuffer());
			int offset = proposal.getReplaceStart();
			String replacement = proposal.updateReplacementString(document, offset, importRewrite,
					client.isCompletionSnippetsSupported(), preferences.isCodeGenerationTemplateGenerateComments());
			completionBuffer.append(replacement);
		} catch (BadLocationException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to compute potential replacement", e);
		}
	}

	private void appendBody(StringBuilder completionBuffer) {
		if (client.isCompletionSnippetsSupported()) {
			String replace = CompletionUtils.sanitizeCompletion(completionBuffer.toString());
			completionBuffer.replace(0, completionBuffer.toString().length(), replace);
		}
		completionBuffer.append(" {\n\t");
		if (client.isCompletionSnippetsSupported())  {
			completionBuffer.append(CURSOR_POSITION);
			completionBuffer.append("\n}");
		}//if Snippets not supported, we leave an open bracket so users can type in directly
	}

	private Range toReplacementRange(CompletionProposal proposal){
		try {
			return JDTUtils.toRange(compilationUnit, proposal.getReplaceStart(), proposal.getReplaceEnd()-proposal.getReplaceStart());
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	private void setInsertReplaceRange(CompletionProposal proposal, InsertReplaceEdit edit){
		try {
			int start = proposal.getReplaceStart();
			int end = proposal.getReplaceEnd();
			if (edit.getReplace() == null) {
				Range replaceRange = JDTUtils.toRange(compilationUnit, start, end - start);
				edit.setReplace(replaceRange);
			}

			if (edit.getInsert() == null) {
				end = Math.min(end, offset);
				Range insertRange = JDTUtils.toRange(compilationUnit, start, end - start);
				edit.setInsert(insertRange);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	/**
	 * Adds imports collected by importRewrite to item
	 * @param item
	 */
	private void addImports(List<org.eclipse.lsp4j.TextEdit> additionalEdits) {
		if(this.importRewrite != null ){
			try {
				TextEdit edit =  this.importRewrite.rewriteImports(new NullProgressMonitor());
				TextEditConverter converter = new TextEditConverter(this.compilationUnit, edit);
				List<org.eclipse.lsp4j.TextEdit> edits = converter.convert();
				if (ChangeUtil.hasChanges(edits)) {
					additionalEdits.addAll(edits);
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Error adding imports",e);
			}
		}
	}

	private boolean isSupportingRequiredProposals(CompletionProposal proposal) {
		return proposal != null
				&& (proposal.getKind() == CompletionProposal.METHOD_REF
				|| proposal.getKind() == CompletionProposal.FIELD_REF
				|| proposal.getKind() == CompletionProposal.TYPE_REF
						|| proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION || proposal.getKind() == CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION
						|| proposal.getKind() == CompletionProposal.ANONYMOUS_CLASS_DECLARATION);
	}

	protected boolean hasArgumentList(CompletionProposal proposal) {
		if (CompletionProposal.METHOD_NAME_REFERENCE == proposal.getKind()) {
			return false;
		} else if (CompletionProposal.LAMBDA_EXPRESSION == proposal.getKind()){
			return true;
		}
		char[] completion= proposal.getCompletion();
		return !isInJavadoc() && completion.length > 0 && completion[completion.length - 1] == RPAREN;
	}

	private boolean isInJavadoc() {
		return context.isInJavadoc();
	}

	private void appendReplacementString(StringBuilder buffer, CompletionProposal proposal) {
		if (!hasArgumentList(proposal)) {
			String str = proposal.getKind() == CompletionProposal.TYPE_REF ? computeJavaTypeReplacementString(proposal) : String.valueOf(proposal.getCompletion());
			if (client.isCompletionSnippetsSupported()) {
				str = CompletionUtils.sanitizeCompletion(str);
				if (proposal.getKind() == CompletionProposal.PACKAGE_REF && str != null && str.endsWith(".*;")) {
					str = str.replace(".*;", ".${0:*};");
				}
			}
			buffer.append(str);
			return;
		}

		// we're inserting a method plus the argument list - respect formatter preferences
		appendMethodNameReplacement(buffer, proposal);
		final boolean addParen  = client.isCompletionSnippetsSupported();
		if(addParen) {
			buffer.append(LPAREN);
		}

		if (hasParameters(proposal)) {
			appendGuessingCompletion(buffer, proposal);
		}

		if(addParen){
			buffer.append(RPAREN);
			// add semicolons only if there are parentheses
			if (canAutomaticallyAppendSemicolon(proposal)) {
				buffer.append(SEMICOLON);
			}
		}
		if(proposal.getKind() == CompletionProposal.METHOD_DECLARATION){
			appendBody(buffer);
		}
	}

	private boolean hasParameters(CompletionProposal proposal) throws IllegalArgumentException {
		return hasArgumentList(proposal) &&
				Signature.getParameterCount(proposal.getSignature()) > 0;
	}

	private void appendMethodNameReplacement(StringBuilder buffer, CompletionProposal proposal) {
		if (proposal.getKind() == CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER) {
			String coreCompletion = String.valueOf(proposal.getCompletion());
			if (client.isCompletionSnippetsSupported()) {
				coreCompletion = CompletionUtils.sanitizeCompletion(coreCompletion);
			}
			//			String lineDelimiter = TextUtilities.getDefaultLineDelimiter(getTextViewer().getDocument());
			//			String replacement= CodeFormatterUtil.format(CodeFormatter.K_EXPRESSION, coreCompletion, 0, lineDelimiter, fInvocationContext.getProject());
			//			buffer.append(replacement.substring(0, replacement.lastIndexOf('.') + 1));
			buffer.append(coreCompletion);
		}

		if (proposal.getKind() != CompletionProposal.CONSTRUCTOR_INVOCATION) {
			String str = new String(proposal.getName());
			if (client.isCompletionSnippetsSupported()) {
				str = CompletionUtils.sanitizeCompletion(str);
			}
			buffer.append(str);
		}

	}

	private void appendGuessingCompletion(StringBuilder buffer, CompletionProposal proposal) {
		char[][] parameterNames;
		try {
			parameterNames = proposal.findParameterNames(null);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			char[] signature = SignatureUtil.fix83600(proposal.getSignature());
			parameterNames = CompletionEngine.createDefaultParameterNames(Signature.getParameterCount(signature));
			proposal.setParameterNames(parameterNames);
		}

		int count= parameterNames.length;

		if(client.isCompletionSnippetsSupported()){
			String[] choices = null;
			boolean guessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager() != null && JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGuessMethodArguments();
			if (guessMethodArguments && (proposal.getKind() == CompletionProposal.METHOD_REF || proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION || proposal.getKind() == CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER)) {
				try {
					choices = guessParameters(parameterNames, proposal);
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
			}
			for (int i= 0; i < count; i++) {
				if (i != 0) {
					buffer.append(COMMA);
					buffer.append(SPACE);
				}
				char[] argument;
				if (choices != null) {
					argument = choices[i].toCharArray();
				} else {
					argument = parameterNames[i];
				}
				if (client.isCompletionSnippetsSupported()) {
					String replace = new String(argument);
					replace = CompletionUtils.sanitizeCompletion(replace);
					argument = replace.toCharArray();
				}
				buffer.append("${");
				buffer.append(Integer.toString(i+1));
				buffer.append(":");
				buffer.append(argument);
				buffer.append("}");
			}
		}
	}

	private String[] guessParameters(char[][] parameterNames, CompletionProposal proposal) throws JavaModelException {
		int count = parameterNames.length;
		String[] result = new String[count];
		String[] parameterTypes = getParameterTypes(proposal);
		IJavaElement[][] assignableElements = getAssignableElements(proposal);
		ParameterGuesser guesser = new ParameterGuesser(compilationUnit);
		for (int i = count - 1; i >= 0; i--) {
			String paramName = new String(parameterNames[i]);
			String argumentProposal = guesser.parameterProposals(parameterTypes[i], paramName, assignableElements[i]);
			if (argumentProposal != null) {
				result[i] = argumentProposal;
			} else {
				result[i] = paramName;
			}
		}
		return result;
	}

	/**
	 * Originally copied from
	 * org.eclipse.jdt.internal.ui.text.java.ParameterGuessingProposal.getParameterTypes()
	 */
	private String[] getParameterTypes(CompletionProposal proposal) {
		char[] signature = SignatureUtil.fix83600(proposal.getSignature());
		char[][] types = Signature.getParameterTypes(signature);

		String[] ret = new String[types.length];
		for (int i = 0; i < types.length; i++) {
			ret[i] = new String(Signature.toCharArray(types[i]));
		}
		return ret;
	}

	/*
	 * Orginally copied from org.eclipse.jdt.internal.ui.text.java.ParameterGuessingProposal.getAssignableElements()
	 */
	private IJavaElement[][] getAssignableElements(CompletionProposal proposal) {
		char[] signature = SignatureUtil.fix83600(proposal.getSignature());
		char[][] types = Signature.getParameterTypes(signature);

		IJavaElement[][] assignableElements = new IJavaElement[types.length][];
		for (int i = 0; i < types.length; i++) {
			assignableElements[i] = context.getVisibleElements(new String(types[i]));
		}
		return assignableElements;
	}

	private final boolean canAutomaticallyAppendSemicolon(CompletionProposal proposal) {
		return !proposal.isConstructor() && CharOperation.equals(new char[] { Signature.C_VOID }, Signature.getReturnType(proposal.getSignature()));
	}

	private org.eclipse.lsp4j.TextEdit toRequiredTypeEdit(CompletionProposal typeProposal, char trigger, boolean canUseDiamond) {

		StringBuilder buffer = new StringBuilder();
		appendReplacementString(buffer, typeProposal);

		if (compilationUnit == null /*|| getContext() != null && getContext().isInJavadoc()*/) {
			Range range = toReplacementRange(typeProposal);
			return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
		}

		IJavaProject project= compilationUnit.getJavaProject();
		if (!shouldProposeGenerics(project)){
			Range range = toReplacementRange(typeProposal);
			return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
		}

		char[] completion= typeProposal.getCompletion();
		// don't add parameters for import-completions nor for proposals with an empty completion (e.g. inside the type argument list)
		if (completion.length > 0 && (completion[completion.length - 1] == SEMICOLON || completion[completion.length - 1] == '.')) {
			Range range = toReplacementRange(typeProposal);
			return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
		}

		/*
		 * Add parameter types
		 */
		boolean onlyAppendArguments;
		try {
			onlyAppendArguments= typeProposal.getCompletion().length == 0 && offset > 0 && compilationUnit.getBuffer().getChar(offset - 1) == '<';
		} catch (JavaModelException e) {
			onlyAppendArguments= false;
		}
		if (onlyAppendArguments || shouldAppendArguments(typeProposal, trigger)) {
			String[] typeArguments = computeTypeArgumentProposals(typeProposal);
			if(typeArguments.length > 0){
				if (canUseDiamond){
					buffer.append("<>"); //$NON-NLS-1$
				} else {
					appendParameterList(buffer,typeArguments, onlyAppendArguments);
				}
			}
		}
		Range range = toReplacementRange(typeProposal);
		return new org.eclipse.lsp4j.TextEdit(range, buffer.toString());
	}

	private final boolean shouldProposeGenerics(IJavaProject project) {
		String sourceVersion;
		if (project != null) {
			sourceVersion= project.getOption(JavaCore.COMPILER_SOURCE, true);
		} else {
			sourceVersion= JavaCore.getOption(JavaCore.COMPILER_SOURCE);
		}

		return !isVersionLessThan(sourceVersion, JavaCore.VERSION_1_5);
	}

	public static boolean isVersionLessThan(String version1, String version2) {
		if (JavaCore.VERSION_CLDC_1_1.equals(version1)) {
			version1= JavaCore.VERSION_1_1 + 'a';
		}
		if (JavaCore.VERSION_CLDC_1_1.equals(version2)) {
			version2= JavaCore.VERSION_1_1 + 'a';
		}
		return version1.compareTo(version2) < 0;
	}

	private IJavaElement resolveJavaElement(IJavaProject project, CompletionProposal proposal) throws JavaModelException {
		char[] signature= proposal.getSignature();
		String typeName= SignatureUtil.stripSignatureToFQN(String.valueOf(signature));
		return project.findType(typeName);
	}

	private String[] computeTypeArgumentProposals(CompletionProposal proposal) {
		try {
			IType type = (IType) resolveJavaElement(
					compilationUnit.getJavaProject(), proposal);
			if (type == null) {
				return new String[0];
			}

			ITypeParameter[] parameters = type.getTypeParameters();
			if (parameters.length == 0) {
				return new String[0];
			}

			String[] arguments = new String[parameters.length];

			ITypeBinding expectedTypeBinding = getExpectedTypeForGenericParameters();
			if (expectedTypeBinding != null && expectedTypeBinding.isParameterizedType()) {
				// in this case, the type arguments we propose need to be compatible
				// with the corresponding type parameters to declared type

				IType expectedType= (IType) expectedTypeBinding.getJavaElement();

				IType[] path= TypeProposalUtils.computeInheritancePath(type, expectedType);
				if (path == null) {
					// proposed type does not inherit from expected type
					// the user might be looking for an inner type of proposed type
					// to instantiate -> do not add any type arguments
					return new String[0];
				}

				int[] indices= new int[parameters.length];
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					indices[paramIdx]= TypeProposalUtils.mapTypeParameterIndex(path, path.length - 1, paramIdx);
				}

				// for type arguments that are mapped through to the expected type's
				// parameters, take the arguments of the expected type
				ITypeBinding[] typeArguments= expectedTypeBinding.getTypeArguments();
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					if (indices[paramIdx] != -1) {
						// type argument is mapped through
						ITypeBinding binding= typeArguments[indices[paramIdx]];
						arguments[paramIdx]= computeTypeProposal(binding, parameters[paramIdx]);
					}
				}
			}

			// for type arguments that are not mapped through to the expected type,
			// take the lower bound of the type parameter
			for (int i = 0; i < arguments.length; i++) {
				if (arguments[i] == null) {
					arguments[i] = computeTypeProposal(parameters[i]);
				}
			}
			return arguments;
		} catch (JavaModelException e) {
			return new String[0];
		}
	}

	private String computeTypeProposal(ITypeParameter parameter) throws JavaModelException {
		String[] bounds= parameter.getBounds();
		String elementName= parameter.getElementName();
		if (bounds.length == 1 && !"java.lang.Object".equals(bounds[0])) {
			return Signature.getSimpleName(bounds[0]);
		} else {
			return elementName;
		}
	}

	private String computeTypeProposal(ITypeBinding binding, ITypeParameter parameter) throws JavaModelException {
		final String name = TypeProposalUtils.getTypeQualifiedName(binding);
		if (binding.isWildcardType()) {

			if (binding.isUpperbound()) {
				// replace the wildcard ? with the type parameter name to get "E extends Bound" instead of "? extends Bound"
				//				String contextName= name.replaceFirst("\\?", parameter.getElementName()); //$NON-NLS-1$
				// upper bound - the upper bound is the bound itself
				return binding.getBound().getName();
			}

			// no or upper bound - use the type parameter of the inserted type, as it may be more
			// restrictive (eg. List<?> list= new SerializableList<Serializable>())
			return computeTypeProposal(parameter);
		}

		// not a wildcard but a type or type variable - this is unambigously the right thing to insert
		return name;
	}

	private StringBuilder appendParameterList(StringBuilder buffer, String[] typeArguments, boolean onlyAppendArguments) {
		if (typeArguments != null && typeArguments.length > 0) {
			if (!onlyAppendArguments) {
				buffer.append(LESS);
			}
			StringBuilder separator= new StringBuilder(3);
			separator.append(COMMA);

			for (int i= 0; i != typeArguments.length; i++) {
				if (i != 0) {
					buffer.append(separator);
				}

				buffer.append(typeArguments[i]);
			}

			if (!onlyAppendArguments) {
				buffer.append(GREATER);
			}
		}
		return buffer;
	}


	private boolean shouldAppendArguments(CompletionProposal proposal,
			char trigger) {
		/*
		 * No argument list if there were any special triggers (for example a
		 * period to qualify an inner type).
		 */
		if (trigger != '\0' && trigger != '<' && trigger != LPAREN) {
			return false;
		}

		/*
		 * No argument list if the completion is empty (already within the
		 * argument list).
		 */
		char[] completion = proposal.getCompletion();
		if (completion.length == 0) {
			return false;
		}

		/*
		 * No argument list if there already is a generic signature behind the
		 * name.
		 */
		try {
			IDocument document = JsonRpcHelpers.toDocument(this.compilationUnit.getBuffer());
			IRegion region= document.getLineInformationOfOffset(proposal.getReplaceEnd());
			String line= document.get(region.getOffset(),region.getLength());

			int index= proposal.getReplaceEnd() - region.getOffset();
			while (index != line.length() && Character.isUnicodeIdentifierPart(line.charAt(index))) {
				++index;
			}

			if (index == line.length()) {
				return true;
			}

			char ch= line.charAt(index);
			return ch != '<';

		} catch (BadLocationException | JavaModelException e) {
			return true;
		}

	}

	private StringBuilder appendImportProposal(StringBuilder buffer, CompletionProposal proposal, int coreKind) {
		int proposalKind= proposal.getKind();
		String qualifiedTypeName= null;
		char[] qualifiedType= null;
		if (proposalKind == CompletionProposal.TYPE_IMPORT) {
			qualifiedType= proposal.getSignature();
			qualifiedTypeName= String.valueOf(Signature.toCharArray(qualifiedType));
		} else if (proposalKind == CompletionProposal.METHOD_IMPORT || proposalKind == CompletionProposal.FIELD_IMPORT) {
			qualifiedType= Signature.getTypeErasure(proposal.getDeclarationSignature());
			qualifiedTypeName= String.valueOf(Signature.toCharArray(qualifiedType));
		} else {
			/*
			 * In 3.3 we only support the above import proposals, see
			 * CompletionProposal#getRequiredProposals()
			 */
			Assert.isTrue(false);
		}

		/* Add imports if the preference is on. */
		if (importRewrite != null) {
			if (proposalKind == CompletionProposal.TYPE_IMPORT) {
				String simpleType= importRewrite.addImport(qualifiedTypeName, null);
				if (coreKind == CompletionProposal.METHOD_REF) {
					buffer.append(simpleType);
					buffer.append(COMMA);
					return buffer;
				}
			} else {
				String res= importRewrite.addStaticImport(qualifiedTypeName, String.valueOf(proposal.getName()), proposalKind == CompletionProposal.FIELD_IMPORT, null);
				int dot= res.lastIndexOf('.');
				if (dot != -1) {
					buffer.append(importRewrite.addImport(res.substring(0, dot), null));
					buffer.append('.');
					return buffer;
				}
			}
			return buffer;
		}

		// Case where we don't have an import rewrite (see allowAddingImports)

		if (compilationUnit != null && isImplicitImport(Signature.getQualifier(qualifiedTypeName), compilationUnit)) {
			/* No imports for implicit imports. */

			if (proposal.getKind() == CompletionProposal.TYPE_IMPORT && coreKind == CompletionProposal.FIELD_REF) {
				return buffer;
			}
			qualifiedTypeName= String.valueOf(Signature.getSignatureSimpleName(qualifiedType));
		}
		buffer.append(qualifiedTypeName);
		buffer.append('.');
		return buffer;
	}

	private ITypeBinding getExpectedTypeForGenericParameters() {
		char[][] chKeys= context.getExpectedTypesKeys();
		if (chKeys == null || chKeys.length == 0) {
			return null;
		}

		String[] keys= new String[chKeys.length];
		for (int i= 0; i < keys.length; i++) {
			keys[i]= String.valueOf(chKeys[0]);
		}

		final ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(compilationUnit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);

		final Map<String, IBinding> bindings= new HashMap<>();
		ASTRequestor requestor= new ASTRequestor() {
			@Override
			public void acceptBinding(String bindingKey, IBinding binding) {
				bindings.put(bindingKey, binding);
			}
		};
		parser.createASTs(new ICompilationUnit[0], keys, requestor, null);

		if (bindings.size() > 0) {
			return (ITypeBinding) bindings.get(keys[0]);
		}

		return null;
	}

	private String computeJavaTypeReplacementString(CompletionProposal proposal) {
		String replacement = String.valueOf(proposal.getCompletion());

		/* No import rewriting ever from within the import section. */
		if (isImportCompletion(proposal)) {
			return replacement;
		}

		/*
		 * Always use the simple name for non-formal javadoc references to
		 * types.
		 */
		// TODO fix
		if (proposal.getKind() == CompletionProposal.TYPE_REF
				&& context.isInJavadocText()) {
			return SignatureUtil.getSimpleTypeName(proposal);
		}

		String qualifiedTypeName = SignatureUtil.getQualifiedTypeName(proposal);

		// Type in package info must be fully qualified.
		if (compilationUnit != null
				&& TypeProposalUtils.isPackageInfo(compilationUnit)) {
			return qualifiedTypeName;
		}

		if (qualifiedTypeName.indexOf('.') == -1 && replacement.length() > 0) {
			// default package - no imports needed
			return qualifiedTypeName;
		}

		/*
		 * If the user types in the qualification, don't force import rewriting
		 * on him - insert the qualified name.
		 */
		String prefix="";
		try{
			IDocument document = JsonRpcHelpers.toDocument(this.compilationUnit.getBuffer());
			prefix = document.get(proposal.getReplaceStart(), proposal.getReplaceEnd() - proposal.getReplaceStart());
		}catch(BadLocationException | JavaModelException e){

		}
		int dotIndex = prefix.lastIndexOf('.');
		// match up to the last dot in order to make higher level matching still
		// work (camel case...)
		if (dotIndex != -1
				&& qualifiedTypeName.toLowerCase().startsWith(
						prefix.substring(0, dotIndex + 1).toLowerCase())) {
			return qualifiedTypeName;
		}

		/*
		 * The replacement does not contain a qualification (e.g. an inner type
		 * qualified by its parent) - use the replacement directly.
		 */
		if (replacement.indexOf('.') == -1) {
			if (isInJavadoc())
			{
				return SignatureUtil.getSimpleTypeName(proposal); // don't use
			}
			// the
			// braces
			// added for
			// javadoc
			// link
			// proposals
			return replacement;
		}

		/* Add imports if the preference is on. */
		if (importRewrite != null) {
			ImportRewriteContext context = null;
			// Only get more context-aware result during 'completionItem/resolve' request.
			// This is because 'ContextSensitiveImportRewriteContext.findInContext()'' is a very
			// heavy operation. If we do that when listing the completion items, the performance
			// will downgrade a lot.
			if (isResolvingRequest) {
				CompilationUnit cu = SharedASTProviderCore.getAST(compilationUnit, SharedASTProviderCore.WAIT_NO, new NullProgressMonitor());
				if (cu != null) {
					context = new ContextSensitiveImportRewriteContext(cu, this.offset, this.importRewrite);
				}
			}
			
			return importRewrite.addImport(qualifiedTypeName, context);
		}

		// fall back for the case we don't have an import rewrite (see
		// allowAddingImports)

		/* No imports for implicit imports. */
		if (compilationUnit != null
				&& TypeProposalUtils.isImplicitImport(
						Signature.getQualifier(qualifiedTypeName),
						compilationUnit)) {
			return Signature.getSimpleName(qualifiedTypeName);
		}


		/* Default: use the fully qualified type name. */
		return qualifiedTypeName;
	}

	private boolean isImportCompletion(CompletionProposal proposal) {
		char[] completion = proposal.getCompletion();
		if (completion.length == 0) {
			return false;
		}

		char last = completion[completion.length - 1];
		/*
		 * Proposals end in a semicolon when completing types in normal imports
		 * or when completing static members, in a period when completing types
		 * in static imports.
		 */
		return last == SEMICOLON || last == '.';
	}

}
