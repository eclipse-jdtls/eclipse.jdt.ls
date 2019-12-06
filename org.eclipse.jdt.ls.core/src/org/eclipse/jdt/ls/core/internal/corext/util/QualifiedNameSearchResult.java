/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameSearchResult
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;

public class QualifiedNameSearchResult {

	private Map<IFile, TextChange> fChanges;

	public QualifiedNameSearchResult() {
		fChanges= new HashMap<>();
	}

	public TextChange getChange(IFile file) {
		TextChange result= fChanges.get(file);
		if (result == null) {
			result= new TextFileChange(file.getName(), file);
			fChanges.put(file, result);
		}
		return result;
	}

	public TextChange[] getAllChanges() {
		Collection<TextChange> values= fChanges.values();
		return values.toArray(new TextChange[values.size()]);
	}

	public IFile[] getAllFiles() {
		Set<IFile> keys= fChanges.keySet();
		return keys.toArray(new IFile[keys.size()]);
	}

	public Change getSingleChange(IFile[] alreadyTouchedFiles) {
		Collection<TextChange> values= fChanges.values();
		if (values.size() == 0) {
			return null;
		}

		CompositeChange result= new CompositeChange(RefactoringCoreMessages.QualifiedNameSearchResult_change_name);
		result.markAsSynthetic();
		List<IFile> files= Arrays.asList(alreadyTouchedFiles);
		for (Iterator<TextChange> iter= values.iterator(); iter.hasNext();) {
			TextFileChange change= (TextFileChange)iter.next();
			if (!files.contains(change.getFile())) {
				result.add(change);
			}
		}
		return result;
	}
}
