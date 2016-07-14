package org.jboss.tools.vscode.java.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.Location;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class NavigateToDefinitionHandler implements RequestHandler<TextDocumentPositionParams, List<org.jboss.tools.langs.Location>>{

	private static final String REQ_DEFINITION = "textDocument/definition";
	
	public NavigateToDefinitionHandler() {
	}
	
	@Override
	public boolean canHandle(String request) {
		return REQ_DEFINITION.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ICompilationUnit unit = resolveCompilationUnit(request);
		
		int[] position = JsonRpcHelpers.readTextDocumentPosition(request);
		Location l = computeDefinitonNavigation(unit, position[0], position[1]);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		Map<String,Object> result = new HashMap<String,Object>();
		if(l != null){
			result.put("uri",l.getUri());
			result.put("range", convertRange(l.getLine(),l.getColumn(),l.getEndLine(),l.getEndColumn()));
		}
		response.setResult(result);
		return response;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// not implemented
		
	}
	
	private Location computeDefinitonNavigation(ICompilationUnit unit, int line, int column) {
		try {
			IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

			if (elements == null || elements.length != 1)
				return null;
			IJavaElement element = elements[0];
			IResource resource = element.getResource();

			// if the selected element corresponds to a resource in workspace,
			// navigate to it
			if (resource != null && resource.getProject() != null) {
				return getLocation(element);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeSelect for" +  unit.getElementName(), e);
		}
		return null;
	}
	
	private Map<String, Object> convertRange(int startLine, int startCol, int endLine, int endCol) {
		Map<String, Object> range = new HashMap<String, Object>();
		Map<String, Object> start = new HashMap<String, Object>();
		Map<String, Object> end = new HashMap<String, Object>();
		if(startLine >-1)
			start.put("line",startLine);
		if(startCol > -1)
			start.put("character", startCol);
		if(endCol >-1)
			end.put("character",endCol);
		if(endLine > -1)
			end.put("line",endLine);
		range.put("start",start);
		range.put("end",end);
		return range;
	}

	@Override
	public List<org.jboss.tools.langs.Location> handle(TextDocumentPositionParams param) {
		// TODO Auto-generated method stub
		return null;
	}

}
