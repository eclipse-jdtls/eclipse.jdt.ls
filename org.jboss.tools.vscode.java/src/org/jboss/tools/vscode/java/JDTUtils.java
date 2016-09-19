/*******************************************************************************
 * Copyright (c) 2016 Red Hat, Inc. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.jboss.tools.vscode.java;

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
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.Position;
import org.jboss.tools.langs.Range;
import org.jboss.tools.vscode.java.handlers.JsonRpcHelpers;

/**
 * General utilities for working with JDT APIs
 * @author Gorkem Ercan
 *
 */
public final class JDTUtils {

	/**
	 * Given the uri returns a {@link ICompilationUnit}.
	 * May return null if it can not associate the uri with a Java 
	 * file. 
	 * 
	 * @param uri 
	 * @return compilation unit
	 */
	public static ICompilationUnit resolveCompilationUnit(String uri) {
		String path = null;
		try {
			path = new URI(uri).getPath();
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uri, e);
		}
		IFile resource = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(path));
		IJavaElement element = JavaCore.create(resource);
		if (element instanceof ICompilationUnit) {
			return (ICompilationUnit)element;
		}
		return null;		
	}
	
	/**
	 * Given the uri returns a {@link IClassFile}.
	 * May return null if it can not resolve the uri to a
	 * library.
	 * 
	 * @see #toLocation(IClassFile, int, int)
	 * @param uri with 'jdt' scheme
	 * @return class file
	 */
	public static IClassFile resolveClassFile(String uriString){
		URI uri = null;
		try {
			uri = new URI(uriString);
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uriString, e);
		}
		if (uri != null && uri.getScheme().equals("jdt") && uri.getAuthority().equals("contents")) {
			String handleId = uri.getQuery();
			IJavaElement element = JavaCore.create(handleId);
			IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
			return cf;
		}
		return null;
		
	}
	
	/**
	 * Convenience method that combines {@link #resolveClassFile(String)} and
	 * {@link #resolveCompilationUnit(String)}. 
	 * 
	 * @param uri
	 * @return either a class file or compilation unit 
	 */
	public static ITypeRoot resolveTypeRoot(String uriString) {
		try {
			URI uri = new URI(uriString);
			if (uri.getScheme().equals("jdt")) {
				return resolveClassFile(uriString);
			}
			return resolveCompilationUnit(uriString);
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uriString, e);
			return null;
		}
	}
	
	/**
	 * Creates a location for a given java element. 
	 * Element can be a {@link ICompilationUnit} or {@link IClassFile}
	 * 
	 * @param element
	 * @return location or null
	 * @throws JavaModelException
	 */
	public static Location toLocation(IJavaElement element) throws JavaModelException{
		ICompilationUnit unit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
		if (unit == null && cf == null) {
			return null;
		}
		if (element instanceof ISourceReference) {
			ISourceRange nameRange = ((ISourceReference) element).getNameRange();
			if(cf == null){
				return toLocation(unit,nameRange.getOffset(), nameRange.getLength());
			}else{
				return toLocation(cf,nameRange.getOffset(), nameRange.getLength());
			}
		}
		return null;
	}
	
	/**
	 * Creates location to the given offset and length for the compilation unit 
	 * 
	 * @param unit
	 * @param offset
	 * @param length
	 * @return location or null
	 * @throws JavaModelException
	 */
	public static Location toLocation(ICompilationUnit unit, int offset, int length) throws JavaModelException {
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
	
	/**
	 * Creates location to the given offset and length for the class file.
	 * 
	 * @param unit
	 * @param offset
	 * @param length
	 * @return location or null
	 * @throws JavaModelException
	 */
	public static Location toLocation(IClassFile unit, int offset, int length) throws JavaModelException{
		Location result = new Location();
		String packageName = unit.getParent().getElementName();
		String jarName = unit.getParent().getParent().getElementName();
		String uriString = null;
		try {
			uriString = new URI("jdt", "contents", "/" + jarName + "/" + packageName + "/" + unit.getElementName(), unit.getHandleIdentifier(), null).toASCIIString();
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Error generating URI for class ", e);
		}
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
	
	/**
	 * Creates a range for the given offset and length for a compilation unit
	 * 
	 * @param unit
	 * @param offset
	 * @param length
	 * @return
	 * @throws JavaModelException
	 */
	public static Range toRange(ICompilationUnit unit, int offset, int length) throws JavaModelException {
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
	
	/**
	 * Returns uri for a compilation unit
	 * @param cu
	 * @return
	 */
	public static String getFileURI(ICompilationUnit cu) {
		return getFileURI(cu.getResource());
	}
	
	/**
	 * Returns uri for a resource 
	 * @param resource
	 * @return
	 */
	public static String getFileURI(IResource resource) {
		String uri = resource.getLocation().toFile().toURI().toString();
		return uri.replaceFirst("file:/([^/])", "file:///$1");
	}	
}
 