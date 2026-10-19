/*******************************************************************************
 * Copyright (c) 2016-2018 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.BinaryMember;
import org.eclipse.jdt.internal.core.JrtPackageFragmentRoot;
import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess2;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class HoverInfoProvider {

	private static final long LABEL_FLAGS=
			JavaElementLabelsCore.ALL_FULLY_QUALIFIED
			| JavaElementLabelsCore.M_PRE_RETURNTYPE
			| JavaElementLabelsCore.M_PARAMETER_ANNOTATIONS
			| JavaElementLabelsCore.M_PARAMETER_TYPES
			| JavaElementLabelsCore.M_PARAMETER_NAMES
			| JavaElementLabelsCore.M_EXCEPTIONS
			| JavaElementLabelsCore.F_PRE_TYPE_SIGNATURE
			| JavaElementLabelsCore.M_PRE_TYPE_PARAMETERS
			| JavaElementLabelsCore.T_TYPE_PARAMETERS
			| JavaElementLabelsCore.USE_RESOLVED;

	private static final long LOCAL_VARIABLE_FLAGS= LABEL_FLAGS & ~JavaElementLabelsCore.F_FULLY_QUALIFIED | JavaElementLabelsCore.F_POST_QUALIFIED;

	private static final long COMMON_SIGNATURE_FLAGS = LABEL_FLAGS & ~JavaElementLabelsCore.ALL_FULLY_QUALIFIED
			| JavaElementLabelsCore.T_FULLY_QUALIFIED | JavaElementLabelsCore.M_FULLY_QUALIFIED;

	private static final String LANGUAGE_ID = "java";

	private final ITypeRoot unit;

	private final PreferenceManager preferenceManager;

	public HoverInfoProvider(ITypeRoot aUnit, PreferenceManager preferenceManager) {
		this.unit = aUnit;
		this.preferenceManager = preferenceManager;
	}

	public List<Either<String, MarkedString>> computeHover(int line, int column, IProgressMonitor monitor) {
		List<Either<String, MarkedString>> res = new LinkedList<>();
		if (preferenceManager != null && !preferenceManager.getPreferences().isHoverJavadocEnabled()) {
			return res;
		}
		try {
			if (monitor.isCanceled()) {
				return cancelled(res);
			}
			IJavaElement[] elements = JDTUtils.findElementsAtSelection(unit, line, column, this.preferenceManager, monitor);
			if (elements == null || elements.length == 0 || monitor.isCanceled()) {
				return cancelled(res);
			}
			IJavaElement curr = null;
			if (elements.length != 1) {
				// they could be package fragments.
				// We need to select the one that matches the package fragment of the current unit
				IPackageFragment packageFragment = (IPackageFragment) unit.getParent();
				IJavaElement found =
						Stream
						.of(elements)
						.filter(e -> e.equals(packageFragment))
						.findFirst()
						.orElse(null);
				if (found == null) {
					// this would be a binary package fragment
					curr = elements[0];
				} else {
					curr = found;
				}
			} else {
				curr = elements[0];
			}
			if (monitor.isCanceled()) {
				return cancelled(res);
			}
			if (JDTEnvironmentUtils.isSyntaxServer() || isResolved(curr, monitor)) {
				IBuffer buffer = curr.getOpenable().getBuffer();
				if (buffer == null && curr instanceof BinaryMember binaryMember) {
					IClassFile classFile = binaryMember.getClassFile();
					if (classFile != null) {
						Optional<IBuildSupport> bs = JavaLanguageServerPlugin.getProjectsManager().getBuildSupport(curr.getJavaProject().getProject());
						if (bs.isPresent()) {
							bs.get().discoverSource(classFile, monitor);
						}
					}
				}
				if (monitor.isCanceled()) {
					return cancelled(res);
				}
				MarkedString signature = computeSignature(curr);
				if (signature != null) {
					res.add(Either.forRight(signature));
				}
				if (monitor.isCanceled()) {
					return cancelled(res);
				}
				MarkedString javadoc = computeJavadoc(curr);
				String value = javadoc == null ? null : javadoc.getValue();
				if (value != null && !value.isBlank()) {
					res.add(Either.forLeft(value));
				}
				String sourceInfo = getSourceInfo(curr);
				if (sourceInfo != null) {
					res.add(Either.forLeft("Source: *" + sourceInfo+"*"));
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Error computing hover", e);
		}
		if (monitor.isCanceled()) {
			return cancelled(res);
		}
		return res;
	}

	private List<Either<String, MarkedString>> cancelled(List<Either<String, MarkedString>> res) {
		res.clear();
		res.add(Either.forLeft(""));
		return res;
	}

	private boolean isResolved(IJavaElement element, IProgressMonitor monitor) throws CoreException {
		if (!(unit instanceof ICompilationUnit)) {
			return true;
		}
		if (element == null) {
			return false;
		}
		if (element.getElementType() != IJavaElement.TYPE) {
			return true;
		}
		if (unit.getResource() != null && !unit.getResource().exists()) {
			return true;
		}
		SearchPattern pattern = SearchPattern.createPattern(element, IJavaSearchConstants.ALL_OCCURRENCES);
		final boolean[] res = new boolean[1];
		res[0] = false;
		SearchEngine engine = new SearchEngine();
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { unit }, IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
		try {
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope, new SearchRequestor() {

				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					if (match.getAccuracy() == SearchMatch.A_INACCURATE) {
						return;
					}
					Object o = match.getElement();
					if (o instanceof IJavaElement element) {
						if (element.getElementType() == IJavaElement.TYPE) {
							res[0] = true;
							return;
						}
						ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						if (compilationUnit == null) {
							return;
						}
						res[0] = true;
						throw new HoverException();
					}
				}
			}, monitor);
		} catch (HoverException | OperationCanceledException e) {
			// ignore
		}
		return res[0];
	}

	public static MarkedString computeSignature(IJavaElement element)  {
		if (element == null) {
			return null;
		}
		String elementLabel = null;
		if (element instanceof ILocalVariable) {
			elementLabel = JavaElementLabelsCore.getElementLabel(element, LOCAL_VARIABLE_FLAGS);
		} else {
			elementLabel = JavaElementLabelsCore.getElementLabel(element, COMMON_SIGNATURE_FLAGS);
		}
		if (element instanceof IField field) {
			IRegion region = null;
			try {
				ISourceRange nameRange = JDTUtils.getNameRange(field);
				if (SourceRange.isAvailable(nameRange)) {
					region = new Region(nameRange.getOffset(), nameRange.getLength());
				}
			} catch (JavaModelException e) {
				// ignore
			}
			String constantValue = JDTUtils.getConstantValue(field, field.getTypeRoot(), region);
			if (constantValue != null) {
				elementLabel = elementLabel + " = " + constantValue;
			}
		}
		return new MarkedString(LANGUAGE_ID, elementLabel);
	}

	private static String getDefaultValue(IMethod method) {
		if (method != null) {
			IRegion region = null;
			try {
				ISourceRange nameRange = JDTUtils.getNameRange(method);
				if (SourceRange.isAvailable(nameRange)) {
					region = new Region(nameRange.getOffset(), nameRange.getLength());
				}
			} catch (JavaModelException e) {
				// ignore
			}
			try {
				return JDTUtils.getAnnotationMemberDefaultValue(method, method.getTypeRoot(), region);
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		return null;
	}

	public static MarkedString computeJavadoc(IJavaElement element) throws CoreException {
		IMember member = null;
		String result = null;
		if (element instanceof ITypeParameter typeParameter) {
			member = typeParameter.getDeclaringMember();
		} else if (element instanceof IMember memberElement) {
			member = memberElement;
		} else if (element instanceof IPackageFragment) {
			result = JavadocContentAccess2.getMarkdownContent(element);
		}
		if (member != null) {
			result = JavadocContentAccess2.getMarkdownContent(member);
			if (member instanceof IMethod method) {
				String defaultValue = getDefaultValue(method);
				if (defaultValue != null) {
					if (JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isSupportsCompletionDocumentationMarkdown()) {
						result = (result == null ? CompletionResolveHandler.EMPTY_STRING : result) + "\n" + CompletionResolveHandler.DEFAULT + defaultValue;
					} else {
						result = (result == null ? CompletionResolveHandler.EMPTY_STRING : result) + CompletionResolveHandler.DEFAULT + defaultValue;
					}
				}
			}
		}
		return result != null ? new MarkedString(LANGUAGE_ID, result) : null;
	}

	public static String getSourceInfo(IJavaElement element) throws JavaModelException {
		IJavaElement current = element;

		while (current != null && !(current instanceof IPackageFragmentRoot)) {
			current = current.getParent();
		}
		if (!(current instanceof IPackageFragmentRoot root)) {
			return null;
		}
		String sourceInfo = null;
		if (root.isArchive()) {
			String jdkVersion = "";
			if (isSystemLibrary(root)) {
				IVMInstall vm = null;
				try {
					vm = JavaRuntime.getVMInstall(root.getJavaProject());
					if (vm instanceof IVMInstall2 vm2) {
						jdkVersion = vm2.getJavaVersion();
					}
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
				sourceInfo = "Java " + jdkVersion;
				if (root instanceof JrtPackageFragmentRoot) {
					sourceInfo += " (module: " + root.getElementName() + ")";
				}
			} else {
				sourceInfo = root.getElementName();
			}

		} else {
			sourceInfo = root.getJavaProject().getElementName();
		}

		if (sourceInfo != null) {
			// Use toLocation to get URI with line number
			Location location = JDTUtils.toLocation(element);
			if (location != null) {
				String uri = location.getUri() + "#" + (location.getRange().getStart().getLine() + 1);
				return "[" + sourceInfo + "](" + uri + ")";
			}
		}

		return sourceInfo;
	}

	/**
	 * Checks if the package fragment root is a system library.
	 * @param root the package fragment root
	 * @return true if the package fragment root is a JDK, false otherwise
	 */
	private static boolean isSystemLibrary(IPackageFragmentRoot root) {
		try {
			if (root.getKind() == IPackageFragmentRoot.K_BINARY && root.isExternal() && root.isReadOnly()) {
				IClasspathEntry entry = root.getRawClasspathEntry();
				if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
					return isJREClasspathContainer(entry.getPath());
				}
			}
			return false;
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return false;
	}

	private static boolean isJREClasspathContainer(IPath containerPath) {
		return containerPath != null && containerPath.segmentCount() > 0 && JavaRuntime.JRE_CONTAINER.equals(containerPath.segment(0));
	}

	/**
	 * Gets the reader content as a String
	 *
	 * @param reader the reader
	 * @return the reader content as string
	 */

	private class HoverException extends CoreException {

		private static final long serialVersionUID = 1L;

		public HoverException() {
			super(new Status(IStatus.OK, IConstants.PLUGIN_ID, ""));
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;
		}

	}
}
