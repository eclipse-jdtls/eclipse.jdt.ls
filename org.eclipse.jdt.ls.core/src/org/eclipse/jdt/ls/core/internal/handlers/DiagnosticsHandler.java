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


import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

public class DiagnosticsHandler implements IProblemRequestor {

	private final List<IProblem> problems;
	private final IResource resource;
	private final JavaClientConnection connection;
	private boolean reportAllErrors = true;

	public DiagnosticsHandler(JavaClientConnection conn, IResource resource, boolean reportOnlySyntaxErrors) {
		problems = new ArrayList<>();
		this.resource = resource;
		this.connection = conn;
		this.reportAllErrors = !reportOnlySyntaxErrors;
	}

	@Override
	public void acceptProblem(IProblem problem) {
		if (reportAllErrors || isSyntaxError(problem)) {
			problems.add(problem);
		}
	}

	public boolean isSyntaxError(IProblem problem) {
		return (problem.getID() & IProblem.Syntax) != 0;
	}

	@Override
	public void beginReporting() {
		JavaLanguageServerPlugin.logInfo("begin problem for "+ this.resource.getName());
		problems.clear();
	}

	@Override
	public void endReporting() {
		JavaLanguageServerPlugin.logInfo("end reporting for "+ this.resource.getName());
		PublishDiagnosticsParams $ = new PublishDiagnosticsParams(JDTUtils.getFileURI(this.resource), toDiagnosticsArray());
		this.connection.publishDiagnostics($);
	}

	protected List<Diagnostic> toDiagnosticsArray() {
		List<Diagnostic> array = new ArrayList<>();
		for (IProblem problem : problems) {
			Diagnostic diag = new Diagnostic();
			diag.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
			diag.setMessage(problem.getMessage());
			diag.setCode(Integer.toString(problem.getID()));
			diag.setSeverity(convertSeverity(problem));
			diag.setRange(convertRange(problem));
			array.add(diag);
		}
		return array;
	}

	private DiagnosticSeverity convertSeverity(IProblem problem) {
		if(problem.isError())
			return DiagnosticSeverity.Error;
		if(problem.isWarning())
			return DiagnosticSeverity.Warning;
		return DiagnosticSeverity.Information;
	}

	@SuppressWarnings("restriction")
	private Range convertRange(IProblem problem) {
		Position start = new Position();
		Position end = new Position();

		start.setLine(problem.getSourceLineNumber()-1);// VSCode is 0-based
		end.setLine(problem.getSourceLineNumber()-1);
		if(problem instanceof DefaultProblem){
			DefaultProblem dProblem = (DefaultProblem)problem;
			start.setCharacter(dProblem.getSourceColumnNumber() - 1);
			int offset = 0;
			if (dProblem.getSourceStart() != -1 && dProblem.getSourceEnd() != -1) {
				offset = dProblem.getSourceEnd() - dProblem.getSourceStart() + 1;
			}
			end.setCharacter(dProblem.getSourceColumnNumber() - 1 + offset);
		}
		return new Range(start, end);
	}

	@Override
	public boolean isActive() {
		return true;
	}
}
