package org.jboss.tools.vscode.java.handlers;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.TextDocumentIdentifier;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;

public class ClassfileContentHandler extends AbstractRequestHandler implements RequestHandler<TextDocumentIdentifier, String> {

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.CLASSFILECONTENTS.getMethod().equals(request);
	}

	@Override
	public String handle(TextDocumentIdentifier param) {
		try {
			URI uri = new URI(param.getUri());
			if (uri.getAuthority().equals("contents")) {
				String handleId = uri.getQuery();
				IJavaElement element = JavaCore.create(handleId);
				IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
				if (cf != null) {
					IBuffer buffer = cf.getBuffer();
					if (buffer != null) {
						JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
						return buffer.getContents();
					}
				}
			}
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Problem reading URI " + param.getUri(), e);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting java element ", e);
		}
		return null;
	}

}