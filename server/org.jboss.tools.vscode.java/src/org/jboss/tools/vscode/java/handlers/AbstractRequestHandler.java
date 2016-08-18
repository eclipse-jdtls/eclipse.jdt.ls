package org.jboss.tools.vscode.java.handlers;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.Position;
import org.jboss.tools.langs.Range;

public abstract class AbstractRequestHandler {

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
	
	
	protected Location toLocation(IJavaElement element) throws JavaModelException{
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
			Range range = new Range();
			
			if(loc != null){
				range.setStart(new Position().withLine(new Double(loc[0]))
						.withCharacter(new Double(loc[1])));
			}
			if(endLoc != null ){
				range.setEnd(new org.jboss.tools.langs.Position().withLine(new Double(endLoc[0]))
						.withCharacter(new Double(endLoc[1])));
			}
			return $.withRange(range);
		}
		return null;
	}
	
	protected Location toLocation(ICompilationUnit unit, int offset, int length) throws JavaModelException {
		Location result = new Location();
		result.setUri(getFileURI(unit));
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), offset);
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), offset + length);
		
		Range range = new Range();
		if (loc != null) {
			range.withStart(new Position().withLine(Double.valueOf(loc[0]))
					.withCharacter(Double.valueOf(loc[1])));
		}
		if (endLoc != null) {
			range.withEnd(new Position().withLine(Double.valueOf(endLoc[0]))
					.withCharacter(Double.valueOf(endLoc[1])));
		}
		return result.withRange(range);
	}
	
	protected Location toLocation(IClassFile unit, int offset, int length) throws JavaModelException, URISyntaxException {
		Location result = new Location();
		String packageName = unit.getParent().getElementName();
		String jarName = unit.getParent().getParent().getElementName();
		String uriString = new URI("jdt", "contents", "/" + jarName + "/" + packageName + "/" + unit.getElementName(), unit.getHandleIdentifier(), null).toASCIIString();
		result.setUri(uriString);
		IBuffer buffer = unit.getBuffer();
		int[] loc = JsonRpcHelpers.toLine(buffer, offset);
		int[] endLoc = JsonRpcHelpers.toLine(buffer, offset + length);

		Range range = new Range();
		if (loc != null) {
			range.withStart(new Position().withLine(Double.valueOf(loc[0]))
					.withCharacter(Double.valueOf(loc[1])));
		}
		if (endLoc != null) {
			range.withEnd(new Position().withLine(Double.valueOf(endLoc[0]))
					.withCharacter(Double.valueOf(endLoc[1])));
		}
		return result.withRange(range);
} 
	
	protected Range toRange(ICompilationUnit unit, int offset, int length) throws JavaModelException {
		Range result = new Range();
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), offset);
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), offset + length);

		if (loc != null && endLoc != null) {
			result.setStart(new Position().withLine(Double.valueOf(loc[0]))
					.withCharacter(Double.valueOf(loc[1])));
			
			result.setEnd(new Position().withLine(Double.valueOf(endLoc[0]))
					.withCharacter(Double.valueOf(endLoc[1])));
					
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
 