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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.DerivedSourceSearchParticipant;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.index.IndexLocation;
import org.eclipse.jdt.internal.core.search.IndexSelector;
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer;
import org.eclipse.jdt.internal.core.search.indexing.IIndexConstants;
import org.eclipse.jdt.internal.core.search.matching.MatchLocator;
import org.eclipse.jdt.internal.core.search.matching.MethodPattern;
import org.eclipse.jdt.internal.core.search.matching.TypeDeclarationPattern;

/**
 * A test search participant for "Language X" ({@code .langx} files).
 * Returns hardcoded index entries and delegates match location to
 * {@link MatchLocator}. Used to verify that jdtls handlers correctly
 * invoke contributed search participants.
 */
public class TestDerivedSourceSearchParticipant extends DerivedSourceSearchParticipant {

	public static final AtomicInteger beginSearchingCount = new AtomicInteger();
	public static final AtomicInteger doneSearchingCount = new AtomicInteger();
	public static final AtomicInteger selectIndexesCount = new AtomicInteger();
	public static final AtomicInteger locateMatchesCount = new AtomicInteger();
	public static final AtomicInteger indexDocumentCount = new AtomicInteger();
	public static final AtomicInteger locateCalleesCount = new AtomicInteger();
	public static final AtomicInteger getCompilationUnitCount = new AtomicInteger();

	private final ThreadLocal<IndexSelector> indexSelector = new ThreadLocal<>();

	public static void reset() {
		beginSearchingCount.set(0);
		doneSearchingCount.set(0);
		selectIndexesCount.set(0);
		locateMatchesCount.set(0);
		indexDocumentCount.set(0);
		locateCalleesCount.set(0);
		getCompilationUnitCount.set(0);
	}

	@Override
	public String getDescription() {
		return "Language X";
	}

	@Override
	public void beginSearching() {
		beginSearchingCount.incrementAndGet();
		this.indexSelector.remove();
	}

	@Override
	public void doneSearching() {
		doneSearchingCount.incrementAndGet();
		this.indexSelector.remove();
	}

	@Override
	public SearchDocument getDocument(String documentPath) {
		return new TestDerivedSearchDocument(documentPath, this);
	}

	@Override
	public void indexDocument(SearchDocument document, IPath indexLocation) {
		indexDocumentCount.incrementAndGet();
		document.removeAllIndexEntries();
		new LangxIndexer(document).indexDocument();
	}

	@Override
	public IPath[] selectIndexes(SearchPattern pattern, IJavaSearchScope scope) {
		selectIndexesCount.incrementAndGet();
		IndexSelector selector = this.indexSelector.get();
		if (selector == null) {
			selector = new IndexSelector(scope, pattern);
			this.indexSelector.set(selector);
		}
		IndexLocation[] urls = selector.getIndexLocations();
		IPath[] paths = new IPath[urls.length];
		for (int i = 0; i < urls.length; i++) {
			paths[i] = new Path(urls[i].getIndexFile().getPath());
		}
		return paths;
	}

	@Override
	public void locateMatches(SearchDocument[] documents, SearchPattern pattern,
			IJavaSearchScope scope, SearchRequestor requestor,
			IProgressMonitor monitor) throws CoreException {
		locateMatchesCount.incrementAndGet();
		SearchDocument[] langxDocs = filterLangxDocuments(documents);
		if (langxDocs.length > 0) {
			MatchLocator matchLocator = new MatchLocator(pattern, requestor, scope, monitor);
			matchLocator.locateMatches(langxDocs);
		}
	}

	private static SearchDocument[] filterLangxDocuments(SearchDocument[] documents) {
		int count = 0;
		for (SearchDocument doc : documents) {
			if (doc.getPath().endsWith(".langx")) {
				count++;
			}
		}
		if (count == documents.length) {
			return documents;
		}
		SearchDocument[] filtered = new SearchDocument[count];
		int idx = 0;
		for (SearchDocument doc : documents) {
			if (doc.getPath().endsWith(".langx")) {
				filtered[idx++] = doc;
			}
		}
		return filtered;
	}

	@Override
	public SearchMatch[] locateCallees(IMember caller, SearchDocument document,
			IProgressMonitor monitor) throws CoreException {
		locateCalleesCount.incrementAndGet();
		return new SearchMatch[0];
	}

	@Override
	public org.eclipse.jdt.core.ICompilationUnit getCompilationUnit(
			org.eclipse.core.resources.IFile file) {
		getCompilationUnitCount.incrementAndGet();
		return null;
	}

	/**
	 * Hardcoded indexer for Language X. Adds a fixed type declaration
	 * and method declaration for any {@code .langx} document.
	 */
	private static class LangxIndexer extends AbstractIndexer {

		LangxIndexer(SearchDocument document) {
			super(document);
		}

		@Override
		public void indexDocument() {
			addIndexEntry(IIndexConstants.TYPE_DECL,
					TypeDeclarationPattern.createIndexKey(
							0, "LangxType".toCharArray(),
							"java".toCharArray(),
							new char[0][], false));
			addIndexEntry(IIndexConstants.METHOD_DECL,
					MethodPattern.createIndexKey(
							"langxMethod".toCharArray(), 0));
		}
	}
}
