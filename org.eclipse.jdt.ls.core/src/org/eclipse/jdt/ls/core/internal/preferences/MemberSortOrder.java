/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/preferences/MembersOrderPreferenceCache.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import java.util.StringTokenizer;

/**
 */
public class MemberSortOrder {

	public static final int TYPE_INDEX= 0;
	public static final int CONSTRUCTORS_INDEX= 1;
	public static final int METHOD_INDEX= 2;
	public static final int FIELDS_INDEX= 3;
	public static final int INIT_INDEX= 4;
	public static final int STATIC_FIELDS_INDEX= 5;
	public static final int STATIC_INIT_INDEX= 6;
	public static final int STATIC_METHODS_INDEX= 7;
	public static final int ENUM_CONSTANTS_INDEX= 8;
	public static final int N_CATEGORIES= ENUM_CONSTANTS_INDEX + 1;

	public static final String DEFAULT_ORDER = "T,SF,SI,SM,F,I,C,M";

	private final String fCategoryOffsetsSetting;
	private int[] fCategoryOffsets= null;


	public MemberSortOrder(String categoryByOffsetsSetting) {
		fCategoryOffsetsSetting = categoryByOffsetsSetting;
	}


	public int getCategoryIndex(int kind) {
		if (fCategoryOffsets == null) {
			fCategoryOffsets= getCategoryOffsets();
		}
		return fCategoryOffsets[kind];
	}

	private int[] getCategoryOffsets() {
		int[] offsets= new int[N_CATEGORIES];
		boolean success = fillCategoryOffsetsFromPreferenceString(fCategoryOffsetsSetting, offsets);
		if (!success) {
			fillCategoryOffsetsFromPreferenceString(DEFAULT_ORDER, offsets);
		}
		return offsets;
	}

	private boolean fillCategoryOffsetsFromPreferenceString(String str, int[] offsets) {
		if (str == null) {
			return false;
		}

		StringTokenizer tokenizer= new StringTokenizer(str, ","); //$NON-NLS-1$
		int i= 0;
		offsets[ENUM_CONSTANTS_INDEX]= i++; // enum constants always on top

		while (tokenizer.hasMoreTokens()) {
			String token= tokenizer.nextToken().trim();
			if ("T".equals(token)) { //$NON-NLS-1$
				offsets[TYPE_INDEX]= i++;
			} else if ("M".equals(token)) { //$NON-NLS-1$
				offsets[METHOD_INDEX]= i++;
			} else if ("F".equals(token)) { //$NON-NLS-1$
				offsets[FIELDS_INDEX]= i++;
			} else if ("I".equals(token)) { //$NON-NLS-1$
				offsets[INIT_INDEX]= i++;
			} else if ("SF".equals(token)) { //$NON-NLS-1$
				offsets[STATIC_FIELDS_INDEX]= i++;
			} else if ("SI".equals(token)) { //$NON-NLS-1$
				offsets[STATIC_INIT_INDEX]= i++;
			} else if ("SM".equals(token)) { //$NON-NLS-1$
				offsets[STATIC_METHODS_INDEX]= i++;
			} else if ("C".equals(token)) { //$NON-NLS-1$
				offsets[CONSTRUCTORS_INDEX]= i++;
			}
		}
		return i == N_CATEGORIES;
	}


}
