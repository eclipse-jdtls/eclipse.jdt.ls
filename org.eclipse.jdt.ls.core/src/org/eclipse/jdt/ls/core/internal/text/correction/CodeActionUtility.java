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
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class CodeActionUtility {

	/**
	 * find a ASTNode with the given type from the coveredNodes and the coveringNode.
	 * If there are multiple results, return the first one.
	 */
	public static ASTNode findASTNode(List<ASTNode> coveredNodes, ASTNode coveringNode, Class<?> nodeType) {
		if (!ASTNode.class.isAssignableFrom(nodeType)) {
			return null;
		}
		if (coveredNodes.size() > 0) {
			for (ASTNode node : coveredNodes) {
				if (nodeType.isInstance(node)) {
					return node;
				}
				ASTNode inferredNode = inferASTNode(node, nodeType);
				if (inferredNode != null) {
					return inferredNode;
				}
			}
		} else if (coveringNode != null) {
			return inferASTNode(coveringNode, nodeType);
		}
		return null;
	}

	/**
	 * infer a ASTNode with the given type from a ASTNode.
	 */
	public static ASTNode inferASTNode(ASTNode node, Class<?> nodeType) {
		if (node == null || !ASTNode.class.isAssignableFrom(nodeType)) {
			return null;
		}
		while (node != null && !nodeType.isInstance(node) && !(node instanceof Statement) && !(node instanceof BodyDeclaration)) {
			node = node.getParent();
		}
		return nodeType.isInstance(node) ? node : null;
	}

	public static List<String> getFieldNames(List<ASTNode> coveredNodes, ASTNode coveringNode) {
		if (coveredNodes.size() > 0) {
			// find field names in covered nodes
			List<String> names = new ArrayList<>();
			for (ASTNode node : coveredNodes) {
				names.addAll(getFieldNamesFromASTNode(node));
			}
			return names;
		} else if (coveringNode != null) {
			// infer field name in covering node
			return getFieldNamesFromASTNode(coveringNode);
		}
		return Collections.emptyList();
	}

	public static List<String> getFieldNamesFromASTNode(ASTNode node) {
		if (node instanceof SimpleName) {
			ASTNode parent = node.getParent();
			if (parent instanceof VariableDeclarationFragment) {
				return CodeActionUtility.getFieldNamesFromASTNode(parent);
			}
		} else if (node instanceof VariableDeclarationFragment) {
			SimpleName name = ((VariableDeclarationFragment) node).getName();
			if (name != null) {
				return Arrays.asList(name.getIdentifier());
			}
		} else if (node instanceof FieldDeclaration) {
			List<String> names = new ArrayList<>();
			List<VariableDeclarationFragment> fragments = ((FieldDeclaration) node).fragments();
			for (VariableDeclarationFragment fragment : fragments) {
				names.addAll(CodeActionUtility.getFieldNamesFromASTNode(fragment));
			}
			return names;
		}
		return Collections.emptyList();
	}

	public static List<String> getVariableNamesFromASTNode(ASTNode node) {
		if (node instanceof VariableDeclaration) {
			SimpleName name = ((VariableDeclaration) node).getName();
			if (name != null) {
				return Arrays.asList(name.getIdentifier());
			}
		} else if (node instanceof VariableDeclarationStatement) {
			List<String> names = new ArrayList<>();
			List<VariableDeclarationFragment> fragments = ((VariableDeclarationStatement) node).fragments();
			for (VariableDeclarationFragment fragment : fragments) {
				names.addAll(CodeActionUtility.getFieldNamesFromASTNode(fragment));
			}
			return names;
		}
		return Collections.emptyList();
	}

	public static String getTypeName(AbstractTypeDeclaration node) {
		SimpleName name = node.getName();
		if (name != null) {
			return name.getIdentifier();
		}
		return null;
	}
}
