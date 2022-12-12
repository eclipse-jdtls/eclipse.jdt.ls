/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.template.java;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jface.text.IDocument;

/**
 * Copied from org.eclipse.jdt.internal.ui.text.template.contentassist.PostfixCompletionProposalComputer
 * UI related code is removed.
 */
public class PostfixCompletionProposalComputer {

	private PostfixTemplateEngine postfixCompletionTemplateEngine;

	public PostfixTemplateEngine computeCompletionEngine(CompletionContext jdtContext, IDocument document, int offset) {
		if (jdtContext != null && jdtContext.isExtended()) {
			if (postfixCompletionTemplateEngine == null) {
				postfixCompletionTemplateEngine = new PostfixTemplateEngine();
			}
			updateTemplateEngine(jdtContext);
			return postfixCompletionTemplateEngine;
		}
		return null;
	}

	private void updateTemplateEngine(CompletionContext jdtContext) {
		IJavaElement enclosingElement= jdtContext.getEnclosingElement();
		if (enclosingElement == null) {
			return;
		}

		int tokenLength= jdtContext.getToken() != null ? jdtContext.getToken().length : 0;
		int invOffset= jdtContext.getOffset() - tokenLength - 1;

		ICompilationUnit cu= (ICompilationUnit) enclosingElement.getAncestor(IJavaElement.COMPILATION_UNIT);
		CompilationUnit cuRoot= SharedASTProviderCore.getAST(cu, SharedASTProviderCore.WAIT_NO, null);
		if (cuRoot == null) {
			cuRoot= (CompilationUnit) createPartialParser(cu, invOffset).createAST(null);
		}

		if (enclosingElement instanceof IMember) {
			ISourceRange sr;
			try {
				sr= ((IMember) enclosingElement).getSourceRange();
				if (sr == null) {
					return;
				}
			} catch (JavaModelException e) {
				return;
			}

			ASTNode completionNode= NodeFinder.perform(cuRoot, sr);
			if (completionNode == null) {
				return;
			}

			ASTNode[] bestNode= new ASTNode[] { completionNode };
			completionNode.accept(new ASTVisitor() {
				@Override
				public boolean visit(StringLiteral node) {
					int start= node.getStartPosition();
					if (invOffset > start && start >= bestNode[0].getStartPosition()) {
						bestNode[0]= node;
					}
					return true;
				}

				@Override
				public boolean visit(ExpressionStatement node) {
					int start= node.getStartPosition();
					if (invOffset > start && start >= bestNode[0].getStartPosition()) {
						bestNode[0]= node;
					}
					return true;
				}

				@Override
				public boolean visit(SimpleName node) {
					int start= node.getStartPosition();
					if (invOffset > start && start >= bestNode[0].getStartPosition()) {
						bestNode[0]= node;
					}
					return true;
				}

				@Override
				public boolean visit(QualifiedName node) {
					int start= node.getStartPosition();
					if (invOffset > start && start >= bestNode[0].getStartPosition()) {
						bestNode[0]= node;
					}
					return true;
				}

				@Override
				public boolean visit(BooleanLiteral node) {
					int start= node.getStartPosition();
					if (invOffset > start && start >= bestNode[0].getStartPosition()) {
						bestNode[0]= node;
					}
					return true;
				}

				@Override
				public boolean visit(Javadoc node) {
					int start= node.getStartPosition();
					if (invOffset > start && start >= bestNode[0].getStartPosition()) {
						bestNode[0]= node;
					}
					return false;
				}

				@Override
				public boolean visit(MethodInvocation node) {
					return visit((Expression)node);
				}

				@Override
				public boolean visit(SuperMethodInvocation node) {
					return visit((Expression)node);
				}

				@Override
				public boolean visit(ClassInstanceCreation node) {
					return visit((Expression)node);
				}

				/**
				 * Does NOT override {@link ASTVisitor}
				 * Handle {@link MethodInvocation}, {@link SuperMethodInvocation}
				 * and {@link ClassInstanceCreation}
				 */
				public boolean visit(Expression node) {
					/*
					 * Do not consider a method invocation node as the best node
					 * if it is RECOVERED. A recovered node may in fact be open
					 * 'System.out.println(...' and the best node may be within
					 * the invocation.
					 *
					 * See PostFixCompletionTest#testConcatenatedShorthandIfStatement()
					 */
					if ((node.getFlags() & ASTNode.RECOVERED) == 0) {
						int start= node.getStartPosition();
						int end= start + node.getLength() - 1;
						if (invOffset > start && invOffset == end + 1) {
							bestNode[0]= node;
							return false;
						}
					}
					return true;
				}
			});

			completionNode= bestNode[0];
			ASTNode completionNodeParent= findBestMatchingParentNode(completionNode);
			postfixCompletionTemplateEngine.setASTNodes(completionNode, completionNodeParent);
			postfixCompletionTemplateEngine.setContext(jdtContext);
		}
	}

	/**
	 * This method determines the best matching parent {@link ASTNode} of the given {@link ASTNode}.
	 * Consider the following example for the definition of <i>best matching parent</i>:<br/>
	 * <code>("two" + 2).var$</code> has <code>"two"</code> as completion {@link ASTNode}. The
	 * parent node is <code>"two" + 2</code> which will result in a syntactically incorrect result,
	 * if the template is applied, because the parentheses aren't taken into account.
	 *
	 * @param node The current {@link ASTNode}
	 * @return {@link ASTNode} which either is the parent of the given node or another predecessor
	 *         {@link ASTNode} in the abstract syntax tree.
	 */
	private ASTNode findBestMatchingParentNode(ASTNode node) {
		ASTNode result= node.getParent();
		if (result instanceof InfixExpression) {
			ASTNode completionNodeGrandParent= result.getParent();
			int safeGuard= 0;
			while (completionNodeGrandParent instanceof ParenthesizedExpression && safeGuard++ < 64) {
				result= completionNodeGrandParent;
				completionNodeGrandParent= result.getParent();
			}
		}
		if (node instanceof SimpleName && result instanceof SimpleType) {
			ASTNode completionNodeGrandParent= result.getParent();
			if (completionNodeGrandParent instanceof ClassInstanceCreation) {
				result= completionNodeGrandParent;
			}
		}
		return result;
	}

	private static ASTParser createPartialParser(ICompilationUnit cu, int position) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setProject(cu.getJavaProject());
		parser.setSource(cu);
		parser.setFocalPosition(position);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return parser;
	}
}
