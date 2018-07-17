/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

/**
 * Implementations handler
 *
 * @author Fred Bricon
 */
public class ImplementationsHandler {

	private PreferenceManager preferenceManager;

	public ImplementationsHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<? extends Location> findImplementations(TextDocumentPositionParams param, IProgressMonitor monitor) {
		List<Location> locations = null;
		IJavaElement elementToSearch = null;
		try {
			ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri());
			if (typeRoot != null) {
				elementToSearch = JDTUtils.findElementAtSelection(typeRoot, param.getPosition().getLine(), param.getPosition().getCharacter(), this.preferenceManager, monitor);
			}
			if (elementToSearch == null) {
				return Collections.emptyList();
			}
			int offset = getOffset(param, typeRoot);
			IRegion region = new Region(offset, 0);
			IType primaryType = typeRoot.findPrimaryType();
			//java.lang.Object is a special case. We need to minimize heavy cost of I/O,
			// by avoiding opening all files from the Object hierarchy
			boolean useDefaultLocation = primaryType == null ? false : "java.lang.Object".equals(primaryType.getFullyQualifiedName());
			ImplementationToLocationMapper mapper = new ImplementationToLocationMapper(preferenceManager.isClientSupportsClassFileContent(), useDefaultLocation);
			ImplementationCollector<Location> collector = new ImplementationCollector<>(region, elementToSearch, mapper);
			locations = collector.findImplementations(monitor);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Find implementations failure ", e);
		}

		return locations;
	}

	private int getOffset(TextDocumentPositionParams param, ITypeRoot typeRoot) {
		int offset = 0;
		try {
			IDocument document = JsonRpcHelpers.toDocument(typeRoot.getBuffer());
			offset = document.getLineOffset(param.getPosition().getLine()) + param.getPosition().getCharacter();
		} catch (JavaModelException | BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return offset;
	}

}
