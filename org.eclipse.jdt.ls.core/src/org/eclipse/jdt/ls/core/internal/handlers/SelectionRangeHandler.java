/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;

public class SelectionRangeHandler {

	public List<SelectionRange> selectionRange(SelectionRangeParams params, IProgressMonitor monitor) {
		if (params.getPositions() == null || params.getPositions().isEmpty()) {
			return Collections.emptyList();
		}

		ITypeRoot root = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (root == null) {
			return Collections.emptyList();
		}

		CompilationUnit ast = CoreASTProvider.getInstance().getAST(root, CoreASTProvider.WAIT_YES, monitor);

		// extra logic to check within the line comments and block comments, which are not parts of the AST
		@SuppressWarnings("unchecked")
		List<Comment> comments = new ArrayList<Comment>(ast.getCommentList());
		comments.removeIf(Javadoc.class::isInstance); // Javadoc nodes are already in the AST

		List<SelectionRange> $ = new ArrayList<>();
		for (Position pos : params.getPositions()) {
			try {
				int offset = JsonRpcHelpers.toOffset(root.getBuffer(), pos.getLine(), pos.getCharacter());
				ASTNode node = NodeFinder.perform(ast, offset, 0);
				if (node == null) {
					continue;
				}

				// find all the ancestors
				List<ASTNode> nodes = new ArrayList<>();
				while (node != null) {
					nodes.add(node);
					node = node.getParent();
				}

				// find all the ranges corresponding to the parent nodes
				SelectionRange selectionRange = null;
				ListIterator<ASTNode> iterator = nodes.listIterator(nodes.size());
				while (iterator.hasPrevious()) {
					node = iterator.previous();
					Range range = JDTUtils.toRange(root, node.getStartPosition(), node.getLength());
					selectionRange = new SelectionRange(range, selectionRange);
				}

				// find in comments
				ASTNode containingComment = containingComment(comments, offset);
				if (containingComment != null) {
					Range range = JDTUtils.toRange(root, containingComment.getStartPosition(), containingComment.getLength());
					selectionRange = new SelectionRange(range, selectionRange);
				}

				if (selectionRange != null) {
					$.add(selectionRange);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Failed to calculate selection range", e);
			}
		}

		return $;
	}

	/**
	 * Finds the comment that contains the specified position
	 *
	 * @param comments
	 * @param offset
	 * @return
	 */
	public ASTNode containingComment(List<Comment> comments, int offset) {
		for (Comment comment : comments) {
			ASTNode result = NodeFinder.perform(comment, offset, 0);
			if (result != null) {
				return result;
			}
		}

		return null;
	}
}
