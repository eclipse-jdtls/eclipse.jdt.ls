/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/SimilarElement.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.Arrays;

public class SimilarElement {

	private final int fKind;
	private final String fName;
	private final String[] fTypesParameters;
	private final int fRelevance;

	public SimilarElement(int kind, String name, int relevance) {
		this(kind, name, null, relevance);
	}

	public SimilarElement(int kind, String name, String[] typesParameters, int relevance) {
		fKind= kind;
		fName= name;
		fTypesParameters= typesParameters;
		fRelevance= relevance;
	}

	/**
	 * Gets the kind.
	 * @return Returns a int
	 */
	public int getKind() {
		return fKind;
	}

	/**
	 * Gets the parameter types.
	 * @return Returns a int
	 */
	public String[] getTypesParameter() {
		return fTypesParameters;
	}

	/**
	 * Gets the name.
	 * @return Returns a String
	 */
	public String getName() {
		return fName;
	}

	/**
	 * Gets the relevance.
	 * @return Returns a int
	 */
	public int getRelevance() {
		return fRelevance;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof SimilarElement elem && fName.equals(elem.fName) && fKind == elem.fKind && Arrays.equals(fTypesParameters, elem.fTypesParameters);
	}

	@Override
	public int hashCode() {
		return fName.hashCode() + fKind;
	}
}
