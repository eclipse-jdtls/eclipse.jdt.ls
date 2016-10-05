/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.jboss.tools.langs.Diagnostic;
import org.jboss.tools.langs.Position;
import org.jboss.tools.langs.PublishDiagnosticsParams;
import org.jboss.tools.langs.Range;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.langs.base.NotificationMessage;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

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
		NotificationMessage<PublishDiagnosticsParams> message = new NotificationMessage<>();
		message.setMethod(LSPMethods.DOCUMENT_DIAGNOSTICS.getMethod());
		message.setParams(new PublishDiagnosticsParams().withUri(JDTUtils.getFileURI(this.resource))
				.withDiagnostics(toDiagnosticsArray()));
		this.connection.send(message);
	}

	protected List<Diagnostic> toDiagnosticsArray() {
		List<Diagnostic> array = new ArrayList<>();
		for (IProblem problem : problems) {
			Diagnostic diag = new Diagnostic();
			diag.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
			diag.setMessage(problem.getMessage());
			diag.setCode(Integer.valueOf(problem.getID()));
			diag.setSeverity(convertSeverity(problem));
			diag.setRange(convertRange(problem));
			array.add(diag);
		}
		return array;
	}

	private Integer convertSeverity(IProblem problem) {
		if(problem.isError())
			return Integer.valueOf(1);
		if(problem.isWarning())
			return Integer.valueOf(2);
		return Integer.valueOf(3);
	}

	@SuppressWarnings("restriction")
	private Range convertRange(IProblem problem) {
		Range range = new Range();
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
		return range.withEnd(end).withStart(start);
	}

	@Override
	public boolean isActive() {
		return true;
	}
}
