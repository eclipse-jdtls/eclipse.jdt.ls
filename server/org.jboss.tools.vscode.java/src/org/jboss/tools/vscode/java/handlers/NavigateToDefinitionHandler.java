package org.jboss.tools.vscode.java.handlers;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.TextDocumentPositionParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class NavigateToDefinitionHandler extends AbstractRequestHandler implements RequestHandler<TextDocumentPositionParams, org.jboss.tools.langs.Location>{

	public NavigateToDefinitionHandler() {
	}
	
	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_DEFINITION.getMethod().equals(request);
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
				return toLocation(element);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeSelect for" +  unit.getElementName(), e);
		}
		return null;
	}
	
	@Override
	public org.jboss.tools.langs.Location handle(TextDocumentPositionParams param) {
		ICompilationUnit unit = resolveCompilationUnit(param.getTextDocument().getUri());
		
		return computeDefinitonNavigation(unit, param.getPosition().getLine().intValue(),
				param.getPosition().getCharacter().intValue());
	}

}
