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

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

public class DiagnosticsHandler extends BaseDiagnosticsHandler {
	private final ICompilationUnit cu;
	private final String uri;
	private boolean isDefaultProject;
	private boolean nonProjectFile = false;

	public DiagnosticsHandler(JavaClientConnection conn, ICompilationUnit cu) {
		super(conn, cu);
		this.cu = cu;
		this.uri = JDTUtils.toURI(cu);
		this.isDefaultProject = JDTUtils.isDefaultProject(cu);
		this.nonProjectFile = isDefaultProject || !JDTUtils.isOnClassPath(cu);
	}

	public boolean isSyntaxMode() {
		return nonProjectFile && JavaLanguageServerPlugin.getNonProjectDiagnosticsState().isOnlySyntaxReported(uri);
	}

	@Override
	public void beginReporting() {
		super.beginReporting();
		if (nonProjectFile) {
			problems.add(createNonProjectProblem());
		}
	}


	private IProblem createNonProjectProblem() {
		String fileName = cu.getElementName();
		String projectName = cu.getJavaProject().getProject().getName();
		String message = null;
		int problemId = NON_PROJECT_JAVA_FILE;
		if (isSyntaxMode()) {
			if (isDefaultProject) {
				message = fileName + " is a non-project file, only syntax errors are reported";
				problemId = NON_PROJECT_JAVA_FILE;
			} else {
				message = fileName + " is not on the classpath of project " + projectName + ", only syntax errors are reported";
				problemId = NOT_ON_CLASSPATH;
			}
		} else {
			if (isDefaultProject) {
				message = fileName + " is a non-project file, only JDK classes are added to its build path";
				problemId = NON_PROJECT_JAVA_FILE;
			} else {
				message = fileName + " is not on the classpath of project " + projectName + ", it will not be compiled to a .class file";
				problemId = NOT_ON_CLASSPATH;
			}
		}

		return new DefaultProblem(
			fileName.toCharArray(),
			message,
			problemId,
			null,
			ProblemSeverities.Warning, 0, 0, 1, 1);
	}

	/**
	 * @noreference public for test purposes only
	 */
	public List<IProblem> getProblems() {
		return problems;
	}
}
