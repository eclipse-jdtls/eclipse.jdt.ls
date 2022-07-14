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
package org.eclipse.jdt.ls.core.internal.codemanipulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.util.CompilationUnitSorter;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Patches org.eclipse.jdt.internal.core.SortElementsOperation for partial members sorting
 */
public class PartialSortMembersOperation {

	public static final String CONTAINS_MALFORMED_NODES = "malformed"; //$NON-NLS-1$
	private Comparator comparator;
	private IJavaElement[] elements;

	public PartialSortMembersOperation(IJavaElement[] elements, Comparator comparator) {
		this.elements = elements;
		this.comparator = comparator;
	}

	boolean checkMalformedNodes(ASTNode node) {
		Object property = node.getProperty(CONTAINS_MALFORMED_NODES);
		if (property == null) return false;
		return ((Boolean) property).booleanValue();
	}

	protected boolean isMalformed(ASTNode node) {
		return (node.getFlags() & ASTNode.MALFORMED) != 0;
	}

	/**
	 * Calculates the required text edits to sort the <code>unit</code>
	 * @param group
	 * @return the edit or null if no sorting is required
	 */
	public TextEdit calculateEdit(org.eclipse.jdt.core.dom.CompilationUnit unit, List<ASTNode> selectedNodes, TextEditGroup group) throws JavaModelException {
		ICompilationUnit cu= (ICompilationUnit)this.elements[0];
		String content= cu.getBuffer().getContents();
		ASTRewrite rewrite= sortCompilationUnit(unit, selectedNodes, group);
		if (rewrite == null) {
			return null;
		}
		Document document= new Document(content);
		return rewrite.rewriteAST(document, cu.getOptions(true));
	}

	/**
	 * org.eclipse.jdt.internal.core.SortElementsOperation.sortCompilationUnit
	 */
	private ASTRewrite sortCompilationUnit(org.eclipse.jdt.core.dom.CompilationUnit ast, List<ASTNode> selectedNodes, final TextEditGroup group) {
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.CompilationUnit compilationUnit) {
				List types = compilationUnit.types();
				boolean contains_malformed_nodes = false;
				for (Iterator iter = types.iterator(); iter.hasNext();) {
					AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) iter.next();
					typeDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, Integer.valueOf(typeDeclaration.getStartPosition()));
					contains_malformed_nodes |= isMalformed(typeDeclaration);
				}
				compilationUnit.setProperty(CONTAINS_MALFORMED_NODES, contains_malformed_nodes);
				return true;
			}
			@Override
			public boolean visit(AnnotationTypeDeclaration annotationTypeDeclaration) {
				List bodyDeclarations = annotationTypeDeclaration.bodyDeclarations();
				boolean contains_malformed_nodes = false;
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, Integer.valueOf(bodyDeclaration.getStartPosition()));
					contains_malformed_nodes |= isMalformed(bodyDeclaration);
				}
				annotationTypeDeclaration.setProperty(CONTAINS_MALFORMED_NODES, contains_malformed_nodes);
				return true;
			}

			@Override
			public boolean visit(AnonymousClassDeclaration anonymousClassDeclaration) {
				List bodyDeclarations = anonymousClassDeclaration.bodyDeclarations();
				boolean contains_malformed_nodes = false;
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, Integer.valueOf(bodyDeclaration.getStartPosition()));
					contains_malformed_nodes |= isMalformed(bodyDeclaration);
				}
				anonymousClassDeclaration.setProperty(CONTAINS_MALFORMED_NODES, contains_malformed_nodes);
				return true;
			}

			@Override
			public boolean visit(TypeDeclaration typeDeclaration) {
				List bodyDeclarations = typeDeclaration.bodyDeclarations();
				boolean contains_malformed_nodes = false;
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, Integer.valueOf(bodyDeclaration.getStartPosition()));
					contains_malformed_nodes |= isMalformed(bodyDeclaration);
				}
				typeDeclaration.setProperty(CONTAINS_MALFORMED_NODES, contains_malformed_nodes);
				return true;
			}

			@Override
			public boolean visit(EnumDeclaration enumDeclaration) {
				List bodyDeclarations = enumDeclaration.bodyDeclarations();
				boolean contains_malformed_nodes = false;
				for (Iterator iter = bodyDeclarations.iterator(); iter.hasNext();) {
					BodyDeclaration bodyDeclaration = (BodyDeclaration) iter.next();
					bodyDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, Integer.valueOf(bodyDeclaration.getStartPosition()));
					contains_malformed_nodes |= isMalformed(bodyDeclaration);
				}
				List enumConstants = enumDeclaration.enumConstants();
				for (Iterator iter = enumConstants.iterator(); iter.hasNext();) {
					EnumConstantDeclaration enumConstantDeclaration = (EnumConstantDeclaration) iter.next();
					enumConstantDeclaration.setProperty(CompilationUnitSorter.RELATIVE_ORDER, Integer.valueOf(enumConstantDeclaration.getStartPosition()));
					contains_malformed_nodes |= isMalformed(enumConstantDeclaration);
				}
				enumDeclaration.setProperty(CONTAINS_MALFORMED_NODES, contains_malformed_nodes);
				return true;
			}
		});

		final ASTRewrite rewriter= ASTRewrite.create(ast.getAST());
		final boolean[] hasChanges= new boolean[] {false};

		ast.accept(new ASTVisitor() {

			private void sortElements(List elements, ListRewrite listRewrite) {
				if (elements.size() == 0)
					return;

				final List myCopy = new ArrayList();
				myCopy.addAll(elements);
				Collections.sort(myCopy, PartialSortMembersOperation.this.comparator);

				for (int i = 0; i < elements.size(); i++) {
					ASTNode oldNode= (ASTNode) elements.get(i);
					ASTNode newNode= (ASTNode) myCopy.get(i);
					if (oldNode != newNode) {
						listRewrite.replace(oldNode, rewriter.createMoveTarget(newNode), group);
						hasChanges[0]= true;
					}
				}
			}

			@Override
			public boolean visit(org.eclipse.jdt.core.dom.CompilationUnit compilationUnit) {
				if (checkMalformedNodes(compilationUnit)) {
					return true; // abort sorting of current element
				}

				sortElements(selectedNodes.stream().filter(node -> compilationUnit.types().contains(node)).collect(Collectors.toList()), rewriter.getListRewrite(compilationUnit, org.eclipse.jdt.core.dom.CompilationUnit.TYPES_PROPERTY));
				return true;
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration annotationTypeDeclaration) {
				if (checkMalformedNodes(annotationTypeDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(selectedNodes.stream().filter(node -> annotationTypeDeclaration.bodyDeclarations().contains(node)).collect(Collectors.toList()), rewriter.getListRewrite(annotationTypeDeclaration, AnnotationTypeDeclaration.BODY_DECLARATIONS_PROPERTY));
				return true;
			}

			@Override
			public boolean visit(AnonymousClassDeclaration anonymousClassDeclaration) {
				if (checkMalformedNodes(anonymousClassDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(selectedNodes.stream().filter(node -> anonymousClassDeclaration.bodyDeclarations().contains(node)).collect(Collectors.toList()), rewriter.getListRewrite(anonymousClassDeclaration, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY));
				return true;
			}

			@Override
			public boolean visit(TypeDeclaration typeDeclaration) {
				if (checkMalformedNodes(typeDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(selectedNodes.stream().filter(node -> typeDeclaration.bodyDeclarations().contains(node)).collect(Collectors.toList()), rewriter.getListRewrite(typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY));
				return true;
			}

			@Override
			public boolean visit(EnumDeclaration enumDeclaration) {
				if (checkMalformedNodes(enumDeclaration)) {
					return true; // abort sorting of current element
				}

				sortElements(selectedNodes.stream().filter(node -> enumDeclaration.bodyDeclarations().contains(node)).collect(Collectors.toList()), rewriter.getListRewrite(enumDeclaration, EnumDeclaration.BODY_DECLARATIONS_PROPERTY));
				sortElements(selectedNodes.stream().filter(node -> enumDeclaration.bodyDeclarations().contains(node)).collect(Collectors.toList()), rewriter.getListRewrite(enumDeclaration, EnumDeclaration.ENUM_CONSTANTS_PROPERTY));
				return true;
			}
		});

		if (!hasChanges[0])
			return null;

		return rewriter;
	}
}
