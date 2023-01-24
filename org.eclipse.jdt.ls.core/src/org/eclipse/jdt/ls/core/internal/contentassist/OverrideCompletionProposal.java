/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Code copied from org.eclipse.jdt.internal.ui.text.java.OverrideCompletionProposal
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contentassist;

import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.IndentManipulation;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.text.edits.MalformedTreeException;

/**
 * Generates Override completion proposals.
 */
public class OverrideCompletionProposal {

	private IJavaProject fJavaProject;
	private String fMethodName;
	private String[] fParamTypes;
	private ICompilationUnit fCompilationUnit;
	private String replacementString;

	public OverrideCompletionProposal(ICompilationUnit cu, String methodName, String[] paramTypes, String completionProposal) {
		this.fCompilationUnit = cu;
		Assert.isNotNull(cu.getJavaProject());
		Assert.isNotNull(methodName);
		Assert.isNotNull(paramTypes);
		Assert.isNotNull(cu);

		fParamTypes= paramTypes;
		fMethodName= methodName;
		fJavaProject= cu.getJavaProject();
		StringBuilder buffer = new StringBuilder();
		buffer.append(completionProposal);
		buffer.append(" {};");
		replacementString = buffer.toString();
	}

	private CompilationUnit getRecoveredAST(IDocument document, int offset, Document recoveredDocument) {
		CompilationUnit ast = CoreASTProvider.getInstance().getAST(fCompilationUnit, CoreASTProvider.WAIT_YES, null);
		if (ast != null) {
			recoveredDocument.set(document.get());
			return ast;
		}

		char[] content= document.get().toCharArray();

		// clear prefix to avoid compile errors
		int index= offset - 1;
		while (index >= 0 && Character.isJavaIdentifierPart(content[index])) {
			content[index]= ' ';
			index--;
		}

		recoveredDocument.set(new String(content));

		final ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setSource(content);
		parser.setUnitName(fCompilationUnit.getElementName());
		parser.setProject(fCompilationUnit.getJavaProject());
		return (CompilationUnit) parser.createAST(new NullProgressMonitor());
	}

	/*
	 * @see JavaTypeCompletionProposal#updateReplacementString(IDocument,char,int,ImportRewrite)
	 */
	public String updateReplacementString(IDocument document, int offset, ImportRewrite importRewrite,
			boolean snippetStringSupport)
					throws CoreException, BadLocationException {
		Document recoveredDocument= new Document();
		CompilationUnit unit= getRecoveredAST(document, offset, recoveredDocument);
		ImportRewriteContext context = new ContextSensitiveImportRewriteContext(unit, offset, importRewrite);

		ITypeBinding declaringType= null;
		ChildListPropertyDescriptor descriptor= null;
		ASTNode node= NodeFinder.perform(unit, offset, 1);
		node= ASTResolving.findParentType(node);
		String result = null;
		if (node instanceof AnonymousClassDeclaration anonymousClassDeclaration) {
			declaringType = anonymousClassDeclaration.resolveBinding();
			descriptor= AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY;
		} else if (node instanceof AbstractTypeDeclaration declaration) {
			descriptor= declaration.getBodyDeclarationsProperty();
			declaringType= declaration.resolveBinding();
		}
		if (declaringType != null) {
			ASTRewrite rewrite= ASTRewrite.create(unit.getAST());
			IMethodBinding methodToOverride= Bindings.findMethodInHierarchy(declaringType, fMethodName, fParamTypes);
			if (methodToOverride == null && declaringType.isInterface()) {
				methodToOverride= Bindings.findMethodInType(node.getAST().resolveWellKnownType("java.lang.Object"), fMethodName, fParamTypes); //$NON-NLS-1$
			}
			if (methodToOverride != null) {
				CodeGenerationSettings settings = PreferenceManager.getCodeGenerationSettings(fCompilationUnit);
				MethodDeclaration stub = StubUtility2Core.createImplementationStubCore(fCompilationUnit, rewrite, importRewrite,
						context, methodToOverride, null, declaringType, settings, declaringType.isInterface(), !declaringType.isInterface(), node,
						snippetStringSupport);
				ListRewrite rewriter= rewrite.getListRewrite(node, descriptor);
				rewriter.insertFirst(stub, null);

				ITrackedNodePosition position= rewrite.track(stub);
				try {
					Map<String, String> options = fCompilationUnit.getOptions(true);

					rewrite.rewriteAST(recoveredDocument, options).apply(recoveredDocument);

					String generatedCode = recoveredDocument.get(position.getStartPosition(), position.getLength());

					String indentAt = getIndentAt(recoveredDocument, position.getStartPosition(), settings);
					int generatedIndent = IndentManipulation.measureIndentUnits(indentAt, settings.tabWidth,
							settings.indentWidth);
					// Kinda fishy but empirical data shows Override needs to change indent by at
					// least 1
					generatedIndent = Math.max(1, generatedIndent);

					// Cancel generated code indent
					String delimiter = TextUtilities.getDefaultLineDelimiter(document);
					result = IndentManipulation.changeIndent(generatedCode, generatedIndent, settings.tabWidth,
							settings.indentWidth, "", delimiter);

				} catch (MalformedTreeException | BadLocationException exception) {
					JavaLanguageServerPlugin.logException("Unable to compute override proposal", exception);
				}
			}
		}
		if (result == null) {
			return replacementString;
		}
		return result;
	}

	private static String getIndentAt(IDocument document, int offset, CodeGenerationSettings settings) {
		try {
			IRegion region= document.getLineInformationOfOffset(offset);
			return IndentManipulation.extractIndentString(document.get(region.getOffset(), region.getLength()), settings.tabWidth, settings.indentWidth);
		} catch (BadLocationException e) {
			return ""; //$NON-NLS-1$
		}
	}

}
