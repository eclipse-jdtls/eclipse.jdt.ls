/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;


import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;

/**
 * @author Gorkem Ercan
 *
 */
public abstract class AbstractCompilationUnitBasedTest extends AbstractProjectsManagerBasedTest{
	@Mock
	protected JavaClientConnection connection;

	protected JDTLanguageServer server;
	protected WorkingCopyOwner wcOwner ;
	protected IProject project;

	@Before
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server= new JDTLanguageServer(projectsManager, preferenceManager);
		JavaCore.initializeAfterLoad(null);
	}

	protected ICompilationUnit getWorkingCopy(String path, String source) throws JavaModelException {
		ICompilationUnit workingCopy = getCompilationUnit(path);
		// workingCopy.getWorkingCopy(wcOwner, monitor);
		workingCopy.becomeWorkingCopy(monitor);
		workingCopy.getBuffer().setContents(source);
		workingCopy.makeConsistent(monitor);
		return workingCopy;
	}

	protected ICompilationUnit getCompilationUnit(String path) {
		return (ICompilationUnit)JavaCore.create(getFile(path));
	}

	protected IFile getFile(String path) {
		return project.getFile(new Path(path));
	}

	protected int[] findCompletionLocation(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		return findCompletionLocation(unit, completeBehind, 0);
	}

	protected int[] findCompletionLocation(ICompilationUnit unit, String completeBehind, int fromIndex) throws JavaModelException {
		String str= unit.getSource();
		int cursorLocation;
		if (fromIndex > 0) {
			cursorLocation = str.indexOf(completeBehind, fromIndex) + completeBehind.length();
		} else {
			cursorLocation = str.lastIndexOf(completeBehind) + completeBehind.length();
		}
		return JsonRpcHelpers.toLine(unit.getBuffer(), cursorLocation);
	}

	@After
	public void shutdown() throws Exception {
		CoreASTProvider.getInstance().disposeAST();
		ICompilationUnit[] workingCopies = JavaCore.getWorkingCopies(wcOwner);
		for (ICompilationUnit workingCopy : workingCopies) {
			workingCopy.discardWorkingCopy();
		}
		workingCopies = JavaCore.getWorkingCopies(null);
		for (ICompilationUnit workingCopy : workingCopies) {
			workingCopy.discardWorkingCopy();
		}
		if (server != null) {
			server.shutdown();
		}
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
	}
}
