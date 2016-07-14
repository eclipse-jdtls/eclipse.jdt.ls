package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.model.SymbolInformation;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class DocumentSymbolHandler implements RequestHandler<DocumentSymbolParams,List<org.jboss.tools.langs.SymbolInformation>>{

	private static final String  REQ_DOC_SYMBOL = "textDocument/documentSymbol";
	
	public DocumentSymbolHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return REQ_DOC_SYMBOL.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		ICompilationUnit unit = this.resolveCompilationUnit(request);
		String uri = JsonRpcHelpers.readTextDocumentUri(request);
		
		SymbolInformation[] elements  = this.getOutline(unit);
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
			l.put("uri", uri);
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

	private SymbolInformation[] getOutline(ICompilationUnit unit) {
		try {
			IJavaElement[] elements = unit.getChildren();
			ArrayList<SymbolInformation> symbols = new ArrayList<SymbolInformation>(elements.length);
			collectChildren(unit, elements, symbols);
			return symbols.toArray(new SymbolInformation[symbols.size()]);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting outline for" +  unit.getElementName(), e);
		}
		return new SymbolInformation[0];
	}
	
	private void collectChildren(ICompilationUnit unit, IJavaElement[] elements, ArrayList<SymbolInformation> symbols)
			throws JavaModelException {
		for(IJavaElement element : elements ){
			if(element.getElementType() == IJavaElement.TYPE){
				collectChildren(unit, ((IType)element).getChildren(),symbols);
			}
			if(element.getElementType() != IJavaElement.FIELD &&
					element.getElementType() != IJavaElement.METHOD
					){
				continue;
			}
			SymbolInformation si = new SymbolInformation();
			si.setName(element.getElementName());
			si.setKind(SymbolInformation.mapKind(element));
			if(element.getParent() != null )
				si.setContainerName(element.getParent().getElementName());
			si.setLocation(getLocation(element));
			symbols.add(si);
		}
	}
	
	@Override
	public void process(JSONRPC2Notification request) {
	}
	@Override
	public List<org.jboss.tools.langs.SymbolInformation> handle(DocumentSymbolParams param) {
		// TODO Auto-generated method stub
		return null;
	}

}