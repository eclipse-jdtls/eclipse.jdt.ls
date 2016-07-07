package org.jboss.tools.vscode.java.handlers;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.internal.core.DocumentAdapter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

final public class JsonRpcHelpers {

	public static JSONRPC2Response methodNotFound(JSONRPC2Request request){
		return new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.METHOD_NOT_FOUND.getCode(), request.getMethod() +  " not found"),request.getID());
		
	}
	
	public static String readTextDocumentUri(JSONRPC2Request request) {
        Map<String, Object> params = request.getNamedParams();
        if (!params.containsKey("textDocument")) {
            return null;
        }
        @SuppressWarnings("unchecked")
		Map<String, Object> textDocument = (Map<String, Object>) params.get("textDocument");
        return (String) textDocument.get("uri");
    }
	
	public static String readTextDocumentUri(JSONRPC2Notification notification) {
		Map<String, Object> params = notification.getNamedParams();
		if (!params.containsKey("textDocument")) {
			return null;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> textDocument = (Map<String, Object>) params.get("textDocument");
		return (String) textDocument.get("uri");
	}
	
	/**
	 * Returns line number on slot 0 and column on slot 1 of the 
	 * returned array.
	 * 
	 * @param request
	 * @return
	 */
	public static int[] readTextDocumentPosition(JSONRPC2Request request){
		Map<String, Object> params = request.getNamedParams();
		if(!params.containsKey("position")) return null;
		Map<String, Object> position = (Map<String, Object>) params.get("position");
		Number line = (Number) position.get("line");
		Number charn = (Number) position.get("character"); 
		return new int[] {line.intValue(), charn.intValue()};
	}
	
	/**
	 * Convert line, column to a document offset.  
	 * @param buffer
	 * @param line
	 * @param column
	 * @return
	 */
	public static int toOffset(IBuffer buffer, int line, int column){
		IDocument document = buffer instanceof IDocument ? (IDocument) buffer : new DocumentAdapter(buffer);
		try {
			return document.getLineOffset(line) + column;
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}
	/**
	 * Convert offset to line number and column;
	 * @param buffer
	 * @param line
	 * @param column
	 * @return
	 */
	public static int[] toLine(IBuffer buffer, int offset){
		IDocument document = buffer instanceof IDocument ? (IDocument) buffer : new DocumentAdapter(buffer);
		try {
			int line = document.getLineOfOffset(offset);
			int column = offset - document.getLineOffset(line); 
			return new int[] {line, column};
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static Map<String, Object> convertRange(int startLine, int startCol, int endLine, int endCol) {
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
	
}
