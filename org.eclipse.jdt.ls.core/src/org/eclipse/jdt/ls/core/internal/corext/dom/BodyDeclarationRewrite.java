/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/dom/BodyDeclarationRewrite.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.dom;

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.ls.core.internal.preferences.MemberSortOrder;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.text.edits.TextEditGroup;


/**
 * Rewrite helper for body declarations.
 *
 * @see ASTNodes#getBodyDeclarationsProperty(ASTNode)
 * @see JDTUIHelperClasses
 */
public class BodyDeclarationRewrite {

	private ASTNode fTypeNode;
	private ListRewrite fListRewrite;

	public static BodyDeclarationRewrite create(ASTRewrite rewrite, ASTNode typeNode) {
		return new BodyDeclarationRewrite(rewrite, typeNode);
	}

	private BodyDeclarationRewrite(ASTRewrite rewrite, ASTNode typeNode) {
		ChildListPropertyDescriptor property= ASTNodes.getBodyDeclarationsProperty(typeNode);
		fTypeNode= typeNode;
		fListRewrite= rewrite.getListRewrite(typeNode, property);
	}

	public void insert(BodyDeclaration decl, TextEditGroup description) {
		List<BodyDeclaration> container= ASTNodes.getBodyDeclarations(fTypeNode);
		int index= getInsertionIndex(decl, container);
		fListRewrite.insertAt(decl, index, description);
	}

	/**
	 * Computes the insertion index to be used to add the given member to the
	 * the list <code>container</code>.
	 * @param member the member to add
	 * @param container a list containing objects of type <code>BodyDeclaration</code>
	 * @return the insertion index to be used
	 */
	public static int getInsertionIndex(BodyDeclaration member, List<? extends BodyDeclaration> container) {
		int containerSize= container.size();

		MemberSortOrder sortOrder = null;
		ASTNode root= member.getRoot();
		if (root instanceof CompilationUnit) {
			ITypeRoot typeRoot= ((CompilationUnit) root).getTypeRoot();
			IResource res = typeRoot.getResource();
			if (res != null) {
				sortOrder = PreferenceManager.getPrefs(res).getMemberSortOrder();
			}
		}
		if (sortOrder == null) {
			sortOrder = new MemberSortOrder(null);
		}

		int orderIndex = getOrderPreference(member, sortOrder);

		int insertPos= containerSize;
		int insertPosOrderIndex= -1;

		for (int i= containerSize - 1; i >= 0; i--) {
			int currOrderIndex = getOrderPreference(container.get(i), sortOrder);
			if (orderIndex == currOrderIndex) {
				if (insertPosOrderIndex != orderIndex) { // no perfect match yet
					insertPos= i + 1; // after a same kind
					insertPosOrderIndex= orderIndex; // perfect match
				}
			} else if (insertPosOrderIndex != orderIndex) { // not yet a perfect match
				if (currOrderIndex < orderIndex) { // we are bigger
					if (insertPosOrderIndex == -1) {
						insertPos= i + 1; // after
						insertPosOrderIndex= currOrderIndex;
					}
				} else {
					insertPos= i; // before
					insertPosOrderIndex= currOrderIndex;
				}
			}
		}
		return insertPos;
	}

	private static int getOrderPreference(BodyDeclaration member, MemberSortOrder sortOrder) {
		int memberType= member.getNodeType();
		int modifiers= member.getModifiers();

		switch (memberType) {
		case ASTNode.TYPE_DECLARATION:
		case ASTNode.ENUM_DECLARATION :
		case ASTNode.ANNOTATION_TYPE_DECLARATION :
			return sortOrder.getCategoryIndex(MemberSortOrder.TYPE_INDEX) * 2;
		case ASTNode.FIELD_DECLARATION:
			if (Modifier.isStatic(modifiers)) {
				int index = sortOrder.getCategoryIndex(MemberSortOrder.STATIC_FIELDS_INDEX) * 2;
				if (Modifier.isFinal(modifiers)) {
					return index; // first final static, then static
				}
				return index + 1;
			}
			return sortOrder.getCategoryIndex(MemberSortOrder.FIELDS_INDEX) * 2;
		case ASTNode.INITIALIZER:
			if (Modifier.isStatic(modifiers)) {
				return sortOrder.getCategoryIndex(MemberSortOrder.STATIC_INIT_INDEX) * 2;
			}
			return sortOrder.getCategoryIndex(MemberSortOrder.INIT_INDEX) * 2;
		case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
			return sortOrder.getCategoryIndex(MemberSortOrder.METHOD_INDEX) * 2;
		case ASTNode.METHOD_DECLARATION:
			if (Modifier.isStatic(modifiers)) {
				return sortOrder.getCategoryIndex(MemberSortOrder.STATIC_METHODS_INDEX) * 2;
			}
			if (((MethodDeclaration) member).isConstructor()) {
				return sortOrder.getCategoryIndex(MemberSortOrder.CONSTRUCTORS_INDEX) * 2;
			}
			return sortOrder.getCategoryIndex(MemberSortOrder.METHOD_INDEX) * 2;
		default:
			return 100;
		}
	}
}
