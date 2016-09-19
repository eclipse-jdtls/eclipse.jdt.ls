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
package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jboss.tools.langs.DocumentHighlight;
import org.jboss.tools.langs.TextDocumentPositionParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JDTUtils;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

import copied.org.eclipse.jdt.internal.ui.search.IOccurrencesFinder;
import copied.org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;
import copied.org.eclipse.jdt.internal.ui.search.OccurrencesFinder;

public class DocumentHighlightHandler implements RequestHandler<TextDocumentPositionParams, List<DocumentHighlight>>{
	
	
	public DocumentHighlightHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_HIGHLIGHT.getMethod().equals(request);
	}

	private List<DocumentHighlight> computeOccurrences(ITypeRoot unit, int line, int column) {
		if (unit != null) {
			try {
				int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
				OccurrencesFinder finder = new OccurrencesFinder();
				ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setSource(unit);
				parser.setResolveBindings(true);
				ASTNode ast = parser.createAST(new NullProgressMonitor());
				if (ast instanceof CompilationUnit) {
					finder.initialize((CompilationUnit) ast, offset, 0);
					List<DocumentHighlight> result = new ArrayList<>();
					OccurrenceLocation[] occurrences = finder.getOccurrences();
					if (occurrences != null) {
						for (OccurrenceLocation loc : occurrences) {
							result.add(convertToHighlight(unit, loc));
						}
					}
					return result;
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Problem with compute occurrences for" + unit.getElementName(), e);
			}
		}
		return Collections.emptyList();
	}

	private DocumentHighlight convertToHighlight(ITypeRoot unit, OccurrenceLocation occurrence)
			throws JavaModelException {
		DocumentHighlight h = new DocumentHighlight();
		if ((occurrence.getFlags() | IOccurrencesFinder.F_WRITE_OCCURRENCE) == IOccurrencesFinder.F_WRITE_OCCURRENCE) {
			h.setKind(Double.valueOf(3));
		} else if ((occurrence.getFlags()
				| IOccurrencesFinder.F_READ_OCCURRENCE) == IOccurrencesFinder.F_READ_OCCURRENCE) {
			h.setKind(Double.valueOf(2));
		}
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset());
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset() + occurrence.getLength());

		return h.withRange(new org.jboss.tools.langs.Range().
				withStart(new org.jboss.tools.langs.Position().
						withLine(Double.valueOf(loc[0])).withCharacter(Double.valueOf(loc[1])))
				.withEnd(new org.jboss.tools.langs.Position().
						withLine(Double.valueOf(endLoc[0])).withCharacter(Double.valueOf(endLoc[1]))));
	}


	@Override
	public List<DocumentHighlight> handle(TextDocumentPositionParams param) {
		ITypeRoot type = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri());
		return computeOccurrences(type, param.getPosition().getLine().intValue(), 
				param.getPosition().getCharacter().intValue());
	}
}
