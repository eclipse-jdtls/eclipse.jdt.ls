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

import java.util.Comparator;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.util.CompilationUnitSorter;
import org.eclipse.jdt.internal.core.manipulation.MembersOrderPreferenceCacheCommon;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

/**
 * org.eclipse.jdt.internal.corext.codemanipulation.SortMembersOperation
 */
public class DefaultJavaElementComparator implements Comparator<BodyDeclaration> {

	private final MembersOrderPreferenceCacheCommon fMemberOrderCache;
	private final boolean fDoNotSortFields;

	public DefaultJavaElementComparator(boolean doNotSortFields) {
		fDoNotSortFields= doNotSortFields;
		fMemberOrderCache = new MembersOrderPreferenceCacheCommon();
		fMemberOrderCache.install();
	}

	private int category(BodyDeclaration bodyDeclaration) {
		switch (bodyDeclaration.getNodeType()) {
			case ASTNode.METHOD_DECLARATION:
				{
					MethodDeclaration method= (MethodDeclaration) bodyDeclaration;
					if (method.isConstructor()) {
						return MembersOrderPreferenceCacheCommon.CONSTRUCTORS_INDEX;
					}
					int flags= method.getModifiers();
					if (Modifier.isStatic(flags))
						return MembersOrderPreferenceCacheCommon.STATIC_METHODS_INDEX;
					else
						return MembersOrderPreferenceCacheCommon.METHOD_INDEX;
				}
			case ASTNode.FIELD_DECLARATION :
				{
					if (JdtFlags.isStatic(bodyDeclaration))
						return MembersOrderPreferenceCacheCommon.STATIC_FIELDS_INDEX;
					else
						return MembersOrderPreferenceCacheCommon.FIELDS_INDEX;
				}
			case ASTNode.INITIALIZER :
				{
					int flags= ((Initializer) bodyDeclaration).getModifiers();
					if (Modifier.isStatic(flags))
						return MembersOrderPreferenceCacheCommon.STATIC_INIT_INDEX;
					else
						return MembersOrderPreferenceCacheCommon.INIT_INDEX;
				}
			case ASTNode.TYPE_DECLARATION :
			case ASTNode.ENUM_DECLARATION :
			case ASTNode.ANNOTATION_TYPE_DECLARATION :
				return MembersOrderPreferenceCacheCommon.TYPE_INDEX;
			case ASTNode.ENUM_CONSTANT_DECLARATION :
				return MembersOrderPreferenceCacheCommon.ENUM_CONSTANTS_INDEX;
			case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
				return MembersOrderPreferenceCacheCommon.METHOD_INDEX; // reusing the method index

		}
		return 0; // should never happen
	}

	private int getCategoryIndex(int category) {
		return fMemberOrderCache.getCategoryIndex(category);
	}

	/**
	 * This comparator follows the contract defined in CompilationUnitSorter.sort.
	 * @see Comparator#compare(java.lang.Object, java.lang.Object)
	 * @see CompilationUnitSorter#sort(int, org.eclipse.jdt.core.ICompilationUnit, int[], java.util.Comparator, int, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public int compare(BodyDeclaration bodyDeclaration1, BodyDeclaration bodyDeclaration2) {
		boolean preserved1= fDoNotSortFields && isSortPreserved(bodyDeclaration1);
		boolean preserved2= fDoNotSortFields && isSortPreserved(bodyDeclaration2);

		// Bug 407759: need to use a common category for all isSortPreserved members that are to be sorted in the same group:
		int cat1= category(bodyDeclaration1);
		if (preserved1) {
			cat1= sortPreservedCategory(cat1);
		}
		int cat2= category(bodyDeclaration2);
		if (preserved2) {
			cat2= sortPreservedCategory(cat2);
		}

		if (cat1 != cat2) {
			return getCategoryIndex(cat1) - getCategoryIndex(cat2);
		}

		// cat1 == cat2 implies preserved1 == preserved2

		if (preserved1) {
			return preserveRelativeOrder(bodyDeclaration1, bodyDeclaration2);
		}

		int flags1= JdtFlags.getVisibilityCode(bodyDeclaration1);
		int flags2= JdtFlags.getVisibilityCode(bodyDeclaration2);
		int vis= fMemberOrderCache.getVisibilityIndex(flags1) - fMemberOrderCache.getVisibilityIndex(flags2);
		return vis;
	}

	private static int sortPreservedCategory(int category) {
		switch (category) {
			case MembersOrderPreferenceCacheCommon.STATIC_FIELDS_INDEX:
			case MembersOrderPreferenceCacheCommon.STATIC_INIT_INDEX:
				return MembersOrderPreferenceCacheCommon.STATIC_FIELDS_INDEX;
			case MembersOrderPreferenceCacheCommon.FIELDS_INDEX:
			case MembersOrderPreferenceCacheCommon.INIT_INDEX:
				return MembersOrderPreferenceCacheCommon.FIELDS_INDEX;
			default:
				return category;
		}
	}

	private boolean isSortPreserved(BodyDeclaration bodyDeclaration) {
		switch (bodyDeclaration.getNodeType()) {
			case ASTNode.FIELD_DECLARATION:
			case ASTNode.ENUM_CONSTANT_DECLARATION:
			case ASTNode.INITIALIZER:
				return true;
			default:
				return false;
		}
	}

	private int preserveRelativeOrder(BodyDeclaration bodyDeclaration1, BodyDeclaration bodyDeclaration2) {
		int value1= ((Integer) bodyDeclaration1.getProperty(CompilationUnitSorter.RELATIVE_ORDER));
		int value2= ((Integer) bodyDeclaration2.getProperty(CompilationUnitSorter.RELATIVE_ORDER));
		return value1 - value2;
	}
}
