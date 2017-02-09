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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.handlers.WorkspaceDiagnosticsHandler;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class WorkspaceDiagnosticsHandlerTest {
	@Mock
	private JavaClientConnection connection;

	@InjectMocks
	private WorkspaceDiagnosticsHandler handler;

	@Test
	public void testToDiagnosticsArray() throws Exception {
		String msg1 = "Something's wrong Jim";
		IMarker m1 = createMarker(IMarker.SEVERITY_WARNING, msg1, 2, 95, 100);

		String msg2 = "He's dead";
		IMarker m2 = createMarker(IMarker.SEVERITY_ERROR, msg2, 10, 1015, 1025);

		String msg3 = "It's probably time to panic";
		IMarker m3 = createMarker(42, msg3, 100, 10000, 10005);

		IDocument d = mock(IDocument.class);
		when(d.getLineOffset(1)).thenReturn(90);
		when(d.getLineOffset(9)).thenReturn(1000);
		when(d.getLineOffset(99)).thenReturn(10000);

		List<Diagnostic> diags = handler.toDiagnosticsArray(d, new IMarker[]{m1, m2, m3});
		assertEquals(3, diags.size());

		Range r;
		Diagnostic d1 = diags.get(0);
		assertEquals(msg1, d1.getMessage());
		assertEquals(DiagnosticSeverity.Warning, d1.getSeverity());
		r = d1.getRange();
		assertEquals(1, r.getStart().getLine());
		assertEquals(5, r.getStart().getCharacter());
		assertEquals(1, r.getEnd().getLine());
		assertEquals(10, r.getEnd().getCharacter());

		Diagnostic d2 = diags.get(1);
		assertEquals(msg2, d2.getMessage());
		assertEquals(DiagnosticSeverity.Error, d2.getSeverity());
		r = d2.getRange();
		assertEquals(9, r.getStart().getLine());
		assertEquals(15, r.getStart().getCharacter());
		assertEquals(9, r.getEnd().getLine());
		assertEquals(25, r.getEnd().getCharacter());

		Diagnostic d3 = diags.get(2);
		assertEquals(msg3, d3.getMessage());
		assertEquals(DiagnosticSeverity.Information, d3.getSeverity());
		r = d3.getRange();
		assertEquals(99, r.getStart().getLine());
		assertEquals(0, r.getStart().getCharacter());
		assertEquals(99, r.getEnd().getLine());
		assertEquals(5, r.getEnd().getCharacter());

	}

	private IMarker createMarker(int severity, String msg, int line, int start, int end) {
		IMarker m = mock(IMarker.class);
		when(m.getAttribute(IMarker.SEVERITY, -1)).thenReturn(severity);
		when(m.getAttribute(IMarker.MESSAGE, "")).thenReturn(msg);
		when(m.getAttribute(IMarker.LINE_NUMBER, -1)).thenReturn(line);
		when(m.getAttribute(IMarker.CHAR_START, -1)).thenReturn(start);
		when(m.getAttribute(IMarker.CHAR_END, -1)).thenReturn(end);
		return m;
	}

}
