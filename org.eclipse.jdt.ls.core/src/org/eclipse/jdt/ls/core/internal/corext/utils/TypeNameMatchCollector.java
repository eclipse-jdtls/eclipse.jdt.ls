/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/util/TypeNameMatchCollector.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.utils;

import java.util.Collection;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;

public class TypeNameMatchCollector extends TypeNameMatchRequestor {

	private final Collection<TypeNameMatch> fCollection;

	public TypeNameMatchCollector(Collection<TypeNameMatch> collection) {
		Assert.isNotNull(collection);
		fCollection= collection;
	}

	private boolean inScope(TypeNameMatch match) {
		return true;
		// return ! TypeFilter.isFiltered(match);
	}

	@Override
	public void acceptTypeNameMatch(TypeNameMatch match) {
		if (inScope(match)) {
			fCollection.add(match);
		}
	}

}
