/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Arcadiy Ivanov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

/**
 * A minimal SearchParticipant for testing the searchParticipant extension point.
 */
public class FakeSearchParticipant extends SearchParticipant {

	public static final AtomicInteger instanceCount = new AtomicInteger(0);

	public FakeSearchParticipant() {
		instanceCount.incrementAndGet();
	}

	@Override
	public SearchDocument getDocument(String documentPath) {
		return null;
	}

	@Override
	public void indexDocument(SearchDocument document, IPath indexLocation) {
	}

	@Override
	public void locateMatches(SearchDocument[] indexMatches, SearchPattern pattern,
			IJavaSearchScope scope, SearchRequestor requestor, IProgressMonitor monitor) throws CoreException {
	}

	@Override
	public IPath[] selectIndexes(SearchPattern pattern, IJavaSearchScope scope) {
		return new IPath[0];
	}

	@Override
	public String getDescription() {
		return "Fake Search Participant for testing";
	}
}
