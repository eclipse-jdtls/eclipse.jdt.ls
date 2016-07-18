package org.jboss.tools.vscode.java.handlers;

import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.langs.Hover;
import org.jboss.tools.langs.TextDocumentPositionParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.HoverInfoProvider;

public class HoverHandler extends AbstractRequestHandler implements RequestHandler<TextDocumentPositionParams, Hover>{
	
	public HoverHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_HOVER.getMethod().equals(request);
	}

	@Override
	public Hover handle(TextDocumentPositionParams param) {
		ICompilationUnit unit = resolveCompilationUnit(param.getTextDocument().getUri());
		
		String hover = computeHover(unit ,param.getPosition().getLine().intValue(),
				param.getPosition().getCharacter().intValue());
		if (hover != null && hover.length() > 0) {
			return new Hover().withContents(hover);
		}
		return null;
	}


	public String computeHover(ICompilationUnit unit, int line, int column) {
		HoverInfoProvider provider = new HoverInfoProvider(unit);
		return provider.computeHover(line,column);
	}	

}
