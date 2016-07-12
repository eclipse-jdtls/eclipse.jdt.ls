package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.Location;
import org.jboss.tools.vscode.java.model.SymbolInformation;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class WorkspaceSymbolHandler extends AbstractRequestHandler {

	private static final String REQ_WS_SYMBOLS = "workspace/symbol";
	
	public WorkspaceSymbolHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_WS_SYMBOLS.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		
		String query = (String) request.getNamedParams().get("query");
		
		SymbolInformation[] elements  = this.search(query);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
		for ( SymbolInformation element : elements ) {
			Map<String,Object> outlineItem = new HashMap<String,Object>();
			outlineItem.put("name", element.getName());
			outlineItem.put("kind", element.getKind());
			if(element.getContainerName() != null ){
				outlineItem.put("containerName", element.getContainerName());
			}
			Map<String,Object> l = new HashMap<String,Object>();
			l.put("uri", element.getLocation().getUri());
			l.put("range",JsonRpcHelpers.convertRange(element.getLocation().getLine(),
					element.getLocation().getColumn(),
					element.getLocation().getEndLine(),
					element.getLocation().getEndColumn()));
			outlineItem.put("location",l);
			result.add(outlineItem);			
		}
		response.setResult(result);
		return response;
	}

	private SymbolInformation[] search(String query) {
		try {
			ArrayList<SymbolInformation> symbols = new ArrayList<SymbolInformation>();
			
			new SearchEngine().searchAllTypeNames(null,SearchPattern.R_PATTERN_MATCH, query.toCharArray(), SearchPattern.R_PREFIX_MATCH,IJavaSearchConstants.TYPE, createSearchScope(),new TypeNameMatchRequestor() {
				
				@Override
				public void acceptTypeNameMatch(TypeNameMatch match) {
					SymbolInformation symbolInformation = new SymbolInformation();
					symbolInformation.setContainerName(match.getTypeContainerName());
					symbolInformation.setName(match.getSimpleTypeName());
					symbolInformation.setKind(SymbolInformation.mapKind(match.getType()));
					Location location = new Location();
					location.setUri(match.getType().getResource().getLocationURI().toString());
					location.setColumn(0);
					location.setEndColumn(0);
					location.setEndLine(0);
					location.setLine(0);
					symbolInformation.setLocation(location);
					symbols.add(symbolInformation);
				}
			}, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, new NullProgressMonitor());
		
			return symbols.toArray(new SymbolInformation[symbols.size()]);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting search for" +  query, e);
		}
		return new SymbolInformation[0];
	}
	private IJavaSearchScope createSearchScope() throws JavaModelException {
		IJavaProject[] projects = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects();
		return SearchEngine.createJavaSearchScope(projects, IJavaSearchScope.SOURCES);
	}
	
	@Override
	public void process(JSONRPC2Notification request) {
	}
}