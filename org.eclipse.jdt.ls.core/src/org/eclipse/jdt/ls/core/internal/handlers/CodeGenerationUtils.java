/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
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

import java.util.Objects;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.lsp4j.Range;

public class CodeGenerationUtils {
	public static final String INSERT_AS_LAST_MEMBER = "lastMember";
	public static final String INSERT_BEFORE_CURSOR = "beforeCursor";
	public static final String INSERT_AFTER_CURSOR = "afterCursor";

	public static IJavaElement findInsertElement(IType type, Range cursor) {
		if (cursor == null) {
			return null;
		}

		String insertionLocation = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getCodeGenerationInsertionLocation();
		if (Objects.equals(insertionLocation, INSERT_AS_LAST_MEMBER)) {
			return null;
		} else if (Objects.equals(insertionLocation, INSERT_BEFORE_CURSOR)) {
			return findElementAtPosition(type, cursor);
		}

		return findElementAfterPosition(type, cursor);
	}

	public static IJavaElement findInsertElement(IType type, int currentOffset) {
		String insertionLocation = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getCodeGenerationInsertionLocation();
		if (Objects.equals(insertionLocation, INSERT_AS_LAST_MEMBER)) {
			return null;
		} else if (Objects.equals(insertionLocation, INSERT_BEFORE_CURSOR)) {
			return findElementAtPosition(type, currentOffset);
		}

		return findElementAfterPosition(type, currentOffset);
	}

	public static IJavaElement findInsertElementAfterLastField(IType type) {
		int lastOffset = 0;
		try {
			IJavaElement[] members = type.getChildren();
			for (IJavaElement member : members) {
				if (member instanceof SourceField) {
					ISourceRange sourceRange = ((IMember) member).getSourceRange();
					int offset = sourceRange.getOffset() + sourceRange.getLength();
					if (offset > lastOffset) {
						lastOffset = offset;
					}
				}
			}
			return findElementAfterPosition(type, lastOffset);
		} catch (JavaModelException e) {
			// do nothing.
		}
		return null;
	}

	private static IJavaElement findElementAtPosition(IType type, int currentOffset) {
		try {
			IJavaElement[] members = type.getChildren();
			for (IJavaElement member : members) {
				ISourceRange sourceRange = ((IMember) member).getSourceRange();
				if (currentOffset <= sourceRange.getOffset() + sourceRange.getLength()) {
					return member;
				}
			}
		} catch (JavaModelException e) {
			// do nothing.
		}

		return null;
	}

	private static IJavaElement findElementAtPosition(IType type, Range range) {
		if (range == null) {
			return null;
		}

		int startOffset = DiagnosticsHelper.getStartOffset(type.getCompilationUnit(), range);
		if (startOffset < 0) {
			return null;
		}

		return findElementAtPosition(type, startOffset);
	}

	private static IJavaElement findElementAfterPosition(IType type, int currentOffset) {
		try {
			IJavaElement[] members = type.getChildren();
			for (IJavaElement member : members) {
				ISourceRange sourceRange = ((IMember) member).getSourceRange();
				if (currentOffset <= sourceRange.getOffset()) {
					return member;
				}
			}
		} catch (JavaModelException e) {
			// do nothing.
		}

		return null;
	}

	private static IJavaElement findElementAfterPosition(IType type, Range range) {
		if (range == null) {
			return null;
		}

		int endOffset = DiagnosticsHelper.getEndOffset(type.getCompilationUnit(), range);
		if (endOffset < 0) {
			return null;
		}

		return findElementAfterPosition(type, endOffset);
	}
}
