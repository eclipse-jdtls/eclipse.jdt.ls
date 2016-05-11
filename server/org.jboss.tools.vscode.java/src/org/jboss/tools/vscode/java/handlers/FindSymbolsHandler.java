package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.model.SymbolInformation;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class FindSymbolsHandler implements RequestHandler{

	private static final String REQ_WS_SYMBOLS = "workspace/symbol";
	
	@Override
	public boolean canHandle(String request) {
		return REQ_WS_SYMBOLS.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		SearchEngine engine = new SearchEngine();
		String query = (String) request.getNamedParams().get("query");
		SearchPattern pattern = SearchPattern.createPattern(query,IJavaSearchConstants.TYPE,IJavaSearchConstants.DECLARATIONS, SearchPattern.R_PREFIX_MATCH);
		JSONRPC2Response response = new JSONRPC2Response(request.getID());
		try {
			final List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), new SearchRequestor() {
						
						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							Object o = match.getElement();
							if( o instanceof IJavaElement){
								IJavaElement element = (IJavaElement)o;
								Map<String,Object> outlineItem = new HashMap<String,Object>();
								outlineItem.put("name", element.getElementName());
								outlineItem.put("kind", SymbolInformation.mapKind(element));
								if(element.getParent() != null ){
									outlineItem.put("containerName", element.getParent().getElementName());
								}
								Map<String,Object> l = new HashMap<String,Object>();
								l.put("uri",match.getResource().getLocationURI().toString());
								l.put("range", JsonRpcHelpers.convertRange(0,0,0,0));
								outlineItem.put("location",l);
								result.add(outlineItem);
								
							}
							
						}
					},new NullProgressMonitor() );

			response.setResult(result);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		// TODO Auto-generated method stub
		
	}

}
