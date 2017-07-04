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


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
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
	private final String uri;
	private final JavaClientConnection connection;
	private boolean reportAllErrors = true;

	public DiagnosticsHandler(JavaClientConnection conn, ICompilationUnit cu) {
		problems = new ArrayList<>();
		this.uri = JDTUtils.getFileURI(cu);
		this.connection = conn;
		this.reportAllErrors = !cu.getJavaProject().getProject().equals(JavaLanguageServerPlugin.getProjectsManager().getDefaultProject());
	}

	@Override
	public void acceptProblem(IProblem problem) {
		if (reportAllErrors || isSyntaxLikeError(problem)) {
			problems.add(problem);
		}
	}

	public boolean isSyntaxLikeError(IProblem problem) {
		//Syntax issues are always reported
		if ((problem.getID() & IProblem.Syntax) != 0) {
			return true;
		}
		//Type and Import issues are never reported
		if ((problem.getID() & IProblem.TypeRelated) != 0 || //
				(problem.getID() & IProblem.ImportRelated) != 0) {
			return false;
		}
		//For the rest, we need to cherry pick what is ignored or not
		switch (problem.getID()) {
			case IProblem.AbstractMethodMustBeImplemented:
			case IProblem.AmbiguousMethod:
			case IProblem.DanglingReference:
			case IProblem.MethodMustOverrideOrImplement:
			case IProblem.MissingReturnType:
			case IProblem.MissingTypeInConstructor:
			case IProblem.MissingTypeInLambda:
			case IProblem.MissingTypeInMethod:
			case IProblem.UndefinedConstructor:
			case IProblem.UndefinedField:
			case IProblem.UndefinedMethod:
			case IProblem.UndefinedName:
			case IProblem.UnresolvedVariable:
				return false;
			default:
				//We log problems for troubleshooting purposes
				String error = getError(problem);
				JavaLanguageServerPlugin.logInfo(problem.getMessage() + " is of type " + error);
		}
		return true;
	}

	private String getError(IProblem problem) {
		try {
			for (Field field : IProblem.class.getDeclaredFields()) {
				if (int.class.equals(field.getType())
						&& Integer.valueOf(problem.getID()).equals(field.get(null))) {
					return field.getName();
				}
			}
		} catch (Exception e) {
		}
		return "unknown";
	}

	@Override
	public void beginReporting() {
		JavaLanguageServerPlugin.logInfo("begin problem for " + this.uri.substring(this.uri.lastIndexOf('/')));
		problems.clear();
	}

	@Override
	public void endReporting() {
		JavaLanguageServerPlugin.logInfo(problems.size() + " problems reported for " + this.uri.substring(this.uri.lastIndexOf('/')));
		PublishDiagnosticsParams $ = new PublishDiagnosticsParams(uri, toDiagnosticsArray(problems));
		this.connection.publishDiagnostics($);
	}

	@Override
	public boolean isActive() {
		return true;
	}

	public static List<Diagnostic> toDiagnosticsArray(List<IProblem> problems) {
		List<Diagnostic> array = new ArrayList<>(problems.size());
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

	private static DiagnosticSeverity convertSeverity(IProblem problem) {
		if(problem.isError()) {
			return DiagnosticSeverity.Error;
		}
		if(problem.isWarning()) {
			return DiagnosticSeverity.Warning;
		}
		return DiagnosticSeverity.Information;
	}

	@SuppressWarnings("restriction")
	private static Range convertRange(IProblem problem) {
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
}
