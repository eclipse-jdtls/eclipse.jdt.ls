package org.jboss.tools.vscode.java.handlers;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.model.Location;
import org.jboss.tools.vscode.java.model.Position;
import org.jboss.tools.vscode.java.model.Range;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;

public abstract class AbstractRequestHandler implements RequestHandler {

	protected ICompilationUnit resolveCompilationUnit(JSONRPC2Request request) {
		return resolveCompilationUnit(JsonRpcHelpers.readTextDocumentUri(request));		
	}
	
	protected ICompilationUnit resolveCompilationUnit(JSONRPC2Notification notification) {
		return resolveCompilationUnit(JsonRpcHelpers.readTextDocumentUri(notification));		
	}
	
	protected ICompilationUnit resolveCompilationUnit(String uri) {
		String path = null;
		try {
			path = new URI(uri).getPath();
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		}
		IFile resource = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(path));
		IJavaElement element = JavaCore.create(resource);
		if (element instanceof ICompilationUnit) {
			return (ICompilationUnit)element;
		}
		return null;		
	}
	
	protected Location getLocation(IJavaElement element) throws JavaModelException {
		ICompilationUnit unit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (unit == null) {
			return null;
		}
		Location $ = new Location();
		$.setUri(getFileURI(unit));
		if (element instanceof ISourceReference) {
			ISourceRange nameRange = ((ISourceReference) element).getNameRange();
			int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(),nameRange.getOffset());
			int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), nameRange.getOffset() + nameRange.getLength());
			
			if(loc != null){
				$.setLine(loc[0]);
				$.setColumn(loc[1]);
			}
			if(endLoc != null ){
				$.setEndLine(endLoc[0]);
				$.setEndColumn(endLoc[1]);
			}
			return $;
		}
		return null;
	}
	
	protected Location getLocation(ICompilationUnit unit, int offset, int length) throws JavaModelException {
		Location result = new Location();
		result.setUri(getFileURI(unit));
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), offset);
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), offset + length);

		if (loc != null) {
			result.setLine(loc[0]);
			result.setColumn(loc[1]);
		}
		if (endLoc != null) {
			result.setEndLine(endLoc[0]);
			result.setEndColumn(endLoc[1]);
		}
		return result;
	}
	
	protected Range getRange(ICompilationUnit unit, int offset, int length) throws JavaModelException {
		Range result = new Range();
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), offset);
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), offset + length);

		if (loc != null && endLoc != null) {
			result.start = new Position(loc[0], loc[1]);
			result.end = new Position(endLoc[0], endLoc[1]);
		}
		return result;

	}
	
	public static String getFileURI(ICompilationUnit cu) {
		return getFileURI(cu.getResource());
	}
	
	public static String getFileURI(IResource resource) {
		String uri = resource.getLocation().toFile().toURI().toString();
		return uri.replaceFirst("file:/([^/])", "file:///$1");
	}	
}
 