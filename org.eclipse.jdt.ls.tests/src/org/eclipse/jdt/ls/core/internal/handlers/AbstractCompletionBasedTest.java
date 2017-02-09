/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.mockito.Mock;

/**
 * @author Gorkem Ercan
 *
 */
public abstract class AbstractCompletionBasedTest extends AbstractProjectsManagerBasedTest{
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
		server= new JDTLanguageServer(projectsManager, null);
	}

	protected ICompilationUnit getWorkingCopy(String path, String source) throws JavaModelException {
		ICompilationUnit workingCopy = getCompilationUnit(path);
		workingCopy.getWorkingCopy(wcOwner,null/*no progress monitor*/);
		workingCopy.getBuffer().setContents(source);
		workingCopy.makeConsistent(null/*no progress monitor*/);
		return workingCopy;
	}

	protected ICompilationUnit getCompilationUnit(String path) {
		return (ICompilationUnit)JavaCore.create(getFile(path));
	}

	protected IFile getFile(String path) {
		return project.getFile(new Path(path));
	}

	protected int[] findCompletionLocation(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		String str= unit.getSource();
		int cursorLocation = str.lastIndexOf(completeBehind) + completeBehind.length();
		return JsonRpcHelpers.toLine(unit.getBuffer(), cursorLocation);
	}
}
