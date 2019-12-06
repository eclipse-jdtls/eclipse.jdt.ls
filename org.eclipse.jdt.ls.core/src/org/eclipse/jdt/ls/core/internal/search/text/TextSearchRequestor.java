/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.search.core.text.TextSearchRequestor
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Terry Parker <tparker@google.com> (Google Inc.) - Bug 441016 - Speed up text search by parallelizing it using JobGroups
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.search.text;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

/**
 * Collects the results from a search engine query.
 * Clients implement a subclass to pass to {@link TextSearchEngine#search(TextSearchScope,
 * TextSearchRequestor, java.util.regex.Pattern, org.eclipse.core.runtime.IProgressMonitor)}
 * and implement the {@link #acceptPatternMatch(TextSearchMatchAccess)}
 * method, and possibly override other life cycle methods.
 * <p>
 * The search engine calls {@link #beginReporting()} when a search starts,
 * then calls {@link #acceptFile(IFile)} for a file visited.
 * If {@link #acceptFile(IFile)} returns <code>true</code> {@link #reportBinaryFile(IFile)} is
 * called if the file could be binary followed by
 * {@link #acceptPatternMatch(TextSearchMatchAccess)} for each pattern match found
 * in this file. The end of the search is signaled with a call to {@link #endReporting()}.
 * Note that {@link #acceptFile(IFile)} is called for all files in the search scope,
 * even if no match can be found.
 * </p>
 * <p>
 * {@link TextSearchEngine#search(TextSearchScope, TextSearchRequestor, java.util.regex.Pattern,
 * org.eclipse.core.runtime.IProgressMonitor)} can perform parallel processing.
 * To support parallel processing, subclasses of this class must synchronize access
 * to any shared data accumulated by or accessed by overrides of the {@link #acceptFile(IFile)},
 * {@link #reportBinaryFile(IFile)} and {@link #acceptPatternMatch(TextSearchMatchAccess)}
 * methods, and override the {@link #canRunInParallel()} method to return true.
 * </p>
 * <p>
 * The order of the search results is unspecified and may vary from request to request;
 * when displaying results, clients should not rely on the order but should instead arrange the results
 * in an order that would be more meaningful to the user.
 * </p>
 *
 * @see TextSearchEngine
 * @since 3.2
 */
public abstract class TextSearchRequestor {

	/**
	 * Notification sent before starting the search action.
	 * Typically, this would tell a search requestor to clear previously
	 * recorded search results.
	 * <p>
	 * The default implementation of this method does nothing. Subclasses
	 * may override.
	 * </p>
	 */
	public void beginReporting() {
		// do nothing
	}

	/**
	 * Notification sent after having completed the search action.
	 * Typically, this would tell a search requestor collector that no more
	 * results will be forthcoming in this search.
	 * <p>
	 * The default implementation of this method does nothing. Subclasses
	 * may override.
	 * </p>
	 */
	public void endReporting() {
		// do nothing
	}

	/**
	 * Notification sent before search starts in the given file. This method is called for all files that are contained
	 * in the search scope.
	 * Implementors can decide if the file content should be searched for search matches or not.
	 * <p>
	 * The default behaviour is to search the file for matches.
	 * </p>
	 * <p>
	 * If {@link #canRunInParallel()} returns true, this method may be called in parallel by different threads,
	 * so any access or updates to collections of results or other shared state must be synchronized.
	 * </p>
	 * @param file the file resource to be searched.
	 * @return If false, no pattern matches will be reported for the content of this file.
	 * @throws CoreException implementors can throw a {@link CoreException} if accessing the resource fails or another
	 * problem prevented the processing of the search match.
	 */
	public boolean acceptFile(IFile file) throws CoreException {
		return true;
	}

	/**
	 * Notification sent that a file might contain binary context.
	 * It is the choice of the search engine to report binary files and it is the heuristic of the search engine to decide
	 * that a file could be binary.
	 * Implementors can decide if the file content should be searched for search matches or not.
	 * <p>
	 * This call is sent after calls {link {@link #acceptFile(IFile)} that return <code>true</code> and before any matches
	 * reported for this file with {@link #acceptPatternMatch(TextSearchMatchAccess)}.
	 * </p>
	 * <p>
	 * If {@link #canRunInParallel()} returns true, this method may be called in parallel by different threads,
	 * so any access or updates to collections of results or other shared state must be synchronized.
	 * </p>
	 * <p>
	 * The default behaviour is to skip binary files
	 * </p>
	 *
	 * @param file the file that might be binary
	 * @return If false, no pattern matches will be reported for the content of this file.
	 */
	public boolean reportBinaryFile(IFile file) {
		return false;
	}

	/**
	 * Accepts the given search match and decides if the search should continue for this file.
	 * <p>
	 * If {@link #canRunInParallel()} returns true, this method may be called in parallel by different threads,
	 * so any access or updates to collections of results or other shared state must be synchronized.
	 * </p>
	 *
	 * @param matchAccess gives access to information of the match found. The matchAccess is not a value
	 * object. Its value might change after this method is finished, and the element might be reused.
	 * @return If false is returned no further matches will be reported for this file.
	 * @throws CoreException implementors can throw a {@link CoreException} if accessing the resource fails or another
	 * problem prevented the processing of the search match.
	 */
	public boolean acceptPatternMatch(TextSearchMatchAccess matchAccess) throws CoreException {
		return true;
	}

	/**
	 * Reports whether this TextSearchRequestor supports executing the text search algorithm
	 * in parallel.
	 * <p>
	 * Subclasses should override this method and return true if they desire faster search results
	 * and their {@link #acceptFile(IFile)}, {@link #reportBinaryFile(IFile)} and
	 * {@link #acceptPatternMatch(TextSearchMatchAccess)} methods are thread-safe.
	 * </p>
	 * <p>
	 * The default behavior is to not use parallelism when running a text search.
	 * </p>
	 *
	 * @return If true, the text search will be run in parallel.
	 * @since 3.10
	 */
	public boolean canRunInParallel() {
		return false;
	}
}
