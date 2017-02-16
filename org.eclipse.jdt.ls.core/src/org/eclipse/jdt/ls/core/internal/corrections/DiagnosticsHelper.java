/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.lsp4j.Diagnostic;

/**
 * Helper methods for {@link Diagnostic}
 *
 * @author Gorkem Ercan
 *
 */
public class DiagnosticsHelper {

	/**
	 * Gets the end offset for the diagnostic.
	 *
	 * @param unit
	 * @param diagnostic
	 * @return starting offset or negative value if can not be determined
	 */
	public static int getEndOffset(ICompilationUnit unit, Diagnostic diagnostic){
		try {
			return JsonRpcHelpers.toOffset(unit.getBuffer(), diagnostic.getRange().getEnd().getLine(), diagnostic.getRange().getEnd().getCharacter());
		} catch (JavaModelException e) {
			return -1;
		}
	}

	/**
	 * Gets the start offset for the diagnostic.
	 *
	 * @param unit
	 * @param diagnostic
	 * @return starting offset or negative value if can not be determined
	 */
	public static int getStartOffset(ICompilationUnit unit, Diagnostic diagnostic){
		try {
			return JsonRpcHelpers.toOffset(unit.getBuffer(), diagnostic.getRange().getStart().getLine(), diagnostic.getRange().getStart().getCharacter());
		} catch (JavaModelException e) {
			return -1;
		}
	}


}
