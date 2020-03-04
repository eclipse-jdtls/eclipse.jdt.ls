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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

public class DiagnosticsHandler implements IProblemRequestor {

	private final ICompilationUnit cu;
	private final List<IProblem> problems;
	private final String uri;
	private final JavaClientConnection connection;
	private boolean nonProjectFile = false;
	private boolean isDefaultProject;

	public static final int NON_PROJECT_JAVA_FILE = 0x10;
	public static final int NOT_ON_CLASSPATH = 0x20;

	public DiagnosticsHandler(JavaClientConnection conn, ICompilationUnit cu) {
		problems = new ArrayList<>();
		this.cu = cu;
		this.uri = JDTUtils.toURI(cu);
		this.connection = conn;
		this.isDefaultProject = JDTUtils.isDefaultProject(cu);
		this.nonProjectFile = isDefaultProject || !JDTUtils.isOnClassPath(cu);
	}

	@Override
	public void acceptProblem(IProblem problem) {
		if (shouldReportAllErrors() || isSyntaxLikeError(problem)) {
			problems.add(problem);
		}
	}

	private boolean shouldReportAllErrors() {
		return !nonProjectFile || !JavaLanguageServerPlugin.getNonProjectDiagnosticsState().isOnlySyntaxReported(uri);
	}

	public boolean isSyntaxLikeError(IProblem problem) {
		//Syntax issues are always reported
		if ((problem.getID() & IProblem.Syntax) != 0) {
			return true;
		}
		if (!isDefaultProject && problem.getID() == IProblem.PackageIsNotExpectedPackage) {
			return false;
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
		List<IProblem> allProblems = problems;
		if (nonProjectFile) {
			allProblems = new ArrayList<>();
			allProblems.add(createNonProjectProblem());
			allProblems.addAll(problems);
		}
		JavaLanguageServerPlugin.logInfo(allProblems.size() + " problems reported for " + this.uri.substring(this.uri.lastIndexOf('/')));
		boolean isDiagnosticTagSupported = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isDiagnosticTagSupported();
		PublishDiagnosticsParams $ = new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), toDiagnosticsArray(this.cu, allProblems, isDiagnosticTagSupported));
		this.connection.publishDiagnostics($);
	}

	private IProblem createNonProjectProblem() {
		String fileName = cu.getElementName();
		String projectName = cu.getJavaProject().getProject().getName();
		String message = null;
		int problemId = NON_PROJECT_JAVA_FILE;
		if (shouldReportAllErrors()) {
			if (isDefaultProject) {
				message = fileName + " is a non-project file, only JDK classes are added to its build path";
				problemId = NON_PROJECT_JAVA_FILE;
			} else {
				message = fileName + " is not on the classpath of project " + projectName + ", it will not be compiled to a .class file";
				problemId = NOT_ON_CLASSPATH;
			}
		} else {
			if (isDefaultProject) {
				message = fileName + " is a non-project file, only syntax errors are reported";
				problemId = NON_PROJECT_JAVA_FILE;
			} else {
				message = fileName + " is not on the classpath of project " + projectName + ", only syntax errors are reported";
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

	@Override
	public boolean isActive() {
		return true;
	}

	@Deprecated
	public static List<Diagnostic> toDiagnosticsArray(IOpenable openable, List<IProblem> problems) {
		return toDiagnosticsArray(openable, problems, false);
	}

	public static List<Diagnostic> toDiagnosticsArray(IOpenable openable, List<IProblem> problems, boolean isDiagnosticTagSupported) {
		List<Diagnostic> array = new ArrayList<>(problems.size());
		for (IProblem problem : problems) {
			Diagnostic diag = new Diagnostic();
			diag.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
			diag.setMessage(problem.getMessage());
			diag.setCode(Integer.toString(problem.getID()));
			diag.setSeverity(convertSeverity(problem));
			diag.setRange(convertRange(openable, problem));
			if (isDiagnosticTagSupported) {
				diag.setTags(getDiagnosticTag(problem.getID()));
			}
			array.add(diag);
		}
		return array;
	}

	public static List<DiagnosticTag> getDiagnosticTag(int id) {
		switch (id) {
			case IProblem.UsingDeprecatedType:
			case IProblem.UsingDeprecatedField:
			case IProblem.UsingDeprecatedMethod:
			case IProblem.UsingDeprecatedConstructor:
			case IProblem.OverridingDeprecatedMethod:
			case IProblem.JavadocUsingDeprecatedField:
			case IProblem.JavadocUsingDeprecatedConstructor:
			case IProblem.JavadocUsingDeprecatedMethod:
			case IProblem.JavadocUsingDeprecatedType:
			case IProblem.UsingTerminallyDeprecatedType:
			case IProblem.UsingTerminallyDeprecatedMethod:
			case IProblem.UsingTerminallyDeprecatedConstructor:
			case IProblem.UsingTerminallyDeprecatedField:
			case IProblem.OverridingTerminallyDeprecatedMethod:
			case IProblem.UsingDeprecatedSinceVersionType:
			case IProblem.UsingDeprecatedSinceVersionMethod:
			case IProblem.UsingDeprecatedSinceVersionConstructor:
			case IProblem.UsingDeprecatedSinceVersionField:
			case IProblem.OverridingDeprecatedSinceVersionMethod:
			case IProblem.UsingTerminallyDeprecatedSinceVersionType:
			case IProblem.UsingTerminallyDeprecatedSinceVersionMethod:
			case IProblem.UsingTerminallyDeprecatedSinceVersionConstructor:
			case IProblem.UsingTerminallyDeprecatedSinceVersionField:
			case IProblem.OverridingTerminallyDeprecatedSinceVersionMethod:
			case IProblem.UsingDeprecatedPackage:
			case IProblem.UsingDeprecatedSinceVersionPackage:
			case IProblem.UsingTerminallyDeprecatedPackage:
			case IProblem.UsingTerminallyDeprecatedSinceVersionPackage:
			case IProblem.UsingDeprecatedModule:
			case IProblem.UsingDeprecatedSinceVersionModule:
			case IProblem.UsingTerminallyDeprecatedModule:
			case IProblem.UsingTerminallyDeprecatedSinceVersionModule:
				return Arrays.asList(DiagnosticTag.Deprecated);
			case IProblem.UnnecessaryCast:
			case IProblem.UnnecessaryInstanceof:
			case IProblem.UnnecessaryElse:
			case IProblem.UnnecessaryNLSTag:
			// Report *unused* cases as unnecessary
			case IProblem.UnusedPrivateType:
			case IProblem.UnusedPrivateField:
			case IProblem.UnusedPrivateMethod:
			case IProblem.UnusedPrivateConstructor:
			case IProblem.UnusedObjectAllocation:
			case IProblem.UnusedMethodDeclaredThrownException:
			case IProblem.UnusedConstructorDeclaredThrownException:
			case IProblem.UnusedLabel:
			case IProblem.UnusedImport:
			case IProblem.UnusedTypeArgumentsForMethodInvocation:
			case IProblem.UnusedWarningToken:
			case IProblem.UnusedTypeArgumentsForConstructorInvocation:
			case IProblem.UnusedTypeParameter:
			// Other unused cases
			case IProblem.LocalVariableIsNeverUsed:
			case IProblem.ArgumentIsNeverUsed:
			case IProblem.ExceptionParameterIsNeverUsed:
				return Arrays.asList(DiagnosticTag.Unnecessary);
		}

		return null;
	}

	private static DiagnosticSeverity convertSeverity(IProblem problem) {
		if (problem.isError()) {
			return DiagnosticSeverity.Error;
		}
		if (problem.isWarning() && (problem.getID() != IProblem.Task)) {
			return DiagnosticSeverity.Warning;
		}
		return DiagnosticSeverity.Information;
	}

	@SuppressWarnings("restriction")
	private static Range convertRange(IOpenable openable, IProblem problem) {
		try {
			return JDTUtils.toRange(openable, problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart() + 1);
		} catch (CoreException e) {
			// In case failed to open the IOpenable's buffer, use the IProblem's information to calculate the range.
			Position start = new Position();
			Position end = new Position();

			start.setLine(problem.getSourceLineNumber() - 1);// The protocol is 0-based.
			end.setLine(problem.getSourceLineNumber() - 1);
			if (problem instanceof DefaultProblem) {
				DefaultProblem dProblem = (DefaultProblem) problem;
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

	public void clearDiagnostics() {
		JavaLanguageServerPlugin.logInfo("Clearing problems for " + this.uri.substring(this.uri.lastIndexOf('/')));
		problems.clear();
		PublishDiagnosticsParams $ = new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), Collections.emptyList());
		this.connection.publishDiagnostics($);
	}

	/**
	 * @noreference public for test purposes only
	 */
	public List<IProblem> getProblems() {
		List<IProblem> allProblems = problems;
		if (nonProjectFile) {
			allProblems = new ArrayList<>();
			allProblems.add(createNonProjectProblem());
			allProblems.addAll(problems);
		}

		return allProblems;
	}
}
