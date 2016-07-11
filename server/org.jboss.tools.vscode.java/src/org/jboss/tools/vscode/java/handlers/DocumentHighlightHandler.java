package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.DocumentHighlight;
import org.jboss.tools.vscode.java.model.Position;
import org.jboss.tools.vscode.java.model.Range;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import copied.org.eclipse.jdt.internal.ui.search.IOccurrencesFinder;
import copied.org.eclipse.jdt.internal.ui.search.OccurrencesFinder;
import copied.org.eclipse.jdt.internal.ui.search.IOccurrencesFinder.OccurrenceLocation;

public class DocumentHighlightHandler extends AbstractRequestHandler {
	
	public static final String REQ_DOCUMENT_HIGHLIGHT= "textDocument/documentHighlight";
	
	public DocumentHighlightHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_DOCUMENT_HIGHLIGHT.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ICompilationUnit unit = resolveCompilationUnit(request);
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		List<DocumentHighlight> computeOccurrences = computeOccurrences(unit, position[0], position[1]);
		List<Map<String, Object>> result= new ArrayList<>();
		for (DocumentHighlight occurrence : computeOccurrences) {
			result.add(occurrence.convertForRPC());
		}
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		response.setResult(result);
		return response;
	}
	
	private List<DocumentHighlight> computeOccurrences(ICompilationUnit unit, int line, int column) {
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

	private DocumentHighlight convertToHighlight(ICompilationUnit unit, OccurrenceLocation occurrence)
			throws JavaModelException {
		DocumentHighlight h = new DocumentHighlight();
		// TODO Auto-generated method stub
		if ((occurrence.getFlags() | IOccurrencesFinder.F_WRITE_OCCURRENCE) == IOccurrencesFinder.F_WRITE_OCCURRENCE) {
			h.kind = 3;
		} else if ((occurrence.getFlags()
				| IOccurrencesFinder.F_READ_OCCURRENCE) == IOccurrencesFinder.F_READ_OCCURRENCE) {
			h.kind = 2;
		}
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset());
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset() + occurrence.getLength());

		h.range = new Range();
		h.range.start = new Position(loc[0], loc[1]);
		h.range.end = new Position(endLoc[0], endLoc[1]);
		return h;
	}

	private Map<String, Object> convert(OccurrenceLocation occurrence) {
		Map<String, Object> result= new HashMap<>();
		if ((occurrence.getFlags() | IOccurrencesFinder.F_WRITE_OCCURRENCE) == IOccurrencesFinder.F_WRITE_OCCURRENCE) {
			result.put("kind",3);
		} else if ((occurrence.getFlags() | IOccurrencesFinder.F_READ_OCCURRENCE) == IOccurrencesFinder.F_READ_OCCURRENCE) {
			result.put("kind", 2);
		} 
		return result;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not implemented
	}
}
