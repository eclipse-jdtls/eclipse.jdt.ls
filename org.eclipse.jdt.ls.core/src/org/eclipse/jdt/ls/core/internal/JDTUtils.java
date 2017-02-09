/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * General utilities for working with JDT APIs
 * @author Gorkem Ercan
 *
 */
public final class JDTUtils {

	//Code generators known to cause problems
	private static Set<String> SILENCED_CODEGENS = Collections.singleton("lombok");

	/**
	 * Given the uri returns a {@link ICompilationUnit}.
	 * May return null if it can not associate the uri with a Java
	 * file.
	 *
	 * @param uriString
	 * @return compilation unit
	 */
	public static ICompilationUnit resolveCompilationUnit(String uriString) {
		return resolveCompilationUnit(toURI(uriString));
	}

	/**
	 * Given the uri returns a {@link ICompilationUnit}.
	 * May return null if it can not associate the uri with a Java
	 * file.
	 *
	 * @param uriString
	 * @return compilation unit
	 */
	public static ICompilationUnit resolveCompilationUnit(URI uri) {
		if (uri == null || "jdt".equals(uri.getScheme()) || !uri.isAbsolute()){
			return null;
		}

		IFile resource = findFile(uri);
		if(resource != null){
			if(!ProjectUtils.isJavaProject(resource.getProject())){
				return null;
			}
			IJavaElement element = JavaCore.create(resource);
			if (element instanceof ICompilationUnit) {
				return (ICompilationUnit)element;
			}
		}
		if (resource == null) {
			return getFakeCompilationUnit(uri);
		}
		//the resource is not null but no compilation unit could be created (eg. project not ready yet)
		return null;
	}

	static ICompilationUnit getFakeCompilationUnit(URI uri) {
		if (uri == null) {
			return null;
		}
		IProject project = JavaLanguageServerPlugin.getProjectsManager().getDefaultProject();
		if (project == null || !project.isAccessible()) {
			return null;
		}
		IJavaProject javaProject = JavaCore.create(project);
		//Only support existing standalone java files
		ICompilationUnit unit = null;

		if ("file".equals(uri.getScheme())) {
			java.nio.file.Path path = Paths.get(uri);
			if (!java.nio.file.Files.isReadable(path)) {
				return null;
			}
			String packageName = getPackageName(javaProject, uri);
			String fileName = path.getName(path.getNameCount()-1).toString();
			String packagePath = packageName.replace(".", "/");
			final IFile file = project.getFile(new Path("src").append(packagePath).append(fileName));
			if (!file.isLinked()) {
				String errMsg = "Failed to create linked resource from "+uri+" to "+project.getName();
				WorkspaceJob job = new WorkspaceJob("Create link") {
					@Override
					public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
						try{
							if (file.isLinked()) {
								return Status.OK_STATUS;
							}
							createFolders(file.getParent(), monitor);
							file.createLink(uri, IResource.REPLACE, monitor);
						} catch (CoreException e) {
							JavaLanguageServerPlugin.logException(errMsg, e);
							return StatusFactory.newErrorStatus(errMsg);
						}
						return Status.OK_STATUS;
					}
				};
				job.setRule(project);
				job.schedule();
				try {
					job.join(1_000, new NullProgressMonitor());
				} catch (OperationCanceledException | InterruptedException e) {
					JavaLanguageServerPlugin.logException(errMsg, e);
				}
			}
			unit = (ICompilationUnit)JavaCore.create(file, javaProject);
		}
		return unit;
	}

	public static void createFolders(IContainer folder, IProgressMonitor monitor) throws CoreException {
		if (!folder.exists() && folder instanceof IFolder) {
			IContainer parent = folder.getParent();
			createFolders(parent, monitor);
			folder.refreshLocal(IResource.DEPTH_ZERO, monitor);
			if (!folder.exists()) {
				((IFolder)folder).create(true, true, monitor);
			}
		}
	}

	public static String getPackageName(IJavaProject javaProject, URI uri) {
		try {
			File file = new File(uri);
			//FIXME need to determine actual charset from file
			String content = Files.toString(file, Charsets.UTF_8);
			return getPackageName(javaProject, content);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to read package name from "+uri, e);
		}
		return "";
	}

	public static String getPackageName(IJavaProject javaProject, String fileContent) {
		if (fileContent == null) {
			return "";
		}
		//TODO probably not the most efficient way to get the package name as this reads the whole file;
		char[] source = fileContent.toCharArray();
		ASTParser parser= ASTParser.newParser(AST.JLS8);
		parser.setProject(javaProject);
		parser.setIgnoreMethodBodies(true);
		parser.setSource(source);
		CompilationUnit ast = (CompilationUnit) parser.createAST(null);
		PackageDeclaration pkg = ast.getPackage();
		return (pkg == null || pkg.getName() == null)?"":pkg.getName().getFullyQualifiedName();
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
		return resolveClassFile(toURI(uriString));
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
	public static IClassFile resolveClassFile(URI uri){
		if (uri != null && "jdt".equals(uri.getScheme()) && "contents".equals(uri.getAuthority())) {
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
		URI uri = toURI(uriString);
		if (uri == null) {
			return null;
		}
		if ("jdt".equals(uri.getScheme())) {
			return resolveClassFile(uri);
		}
		return resolveCompilationUnit(uri);
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
			range.setStart(new Position(loc[0],loc[1]));
		}
		if (endLoc != null) {
			range.setEnd(new Position(endLoc[0],endLoc[1]));
		}
		result.setRange(range);
		return result;
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
			range.setStart(new Position(loc[0], loc[1]));
		}
		if (endLoc != null) {
			range.setEnd(new Position(endLoc[0],endLoc[1]));
		}
		result.setRange(range);
		return result;
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
		final IBuffer buffer = unit.getBuffer();
		int[] loc = JsonRpcHelpers.toLine(buffer, offset);
		int[] endLoc = JsonRpcHelpers.toLine(buffer, offset + length);

		if (loc != null && endLoc != null) {
			result.setStart(new Position(loc[0],loc[1]));
			result.setEnd(new Position(endLoc[0],endLoc[1]));
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
		return ResourceUtils.fixURI(resource.getRawLocationURI());
	}

	public static IJavaElement findElementAtSelection(ITypeRoot unit, int line, int column) throws JavaModelException {
		IJavaElement[] elements = findElementsAtSelection(unit, line, column);
		if (elements != null && elements.length == 1) {
			return elements[0];
		}
		return null;
	}

	public static IJavaElement[] findElementsAtSelection(ITypeRoot unit, int line, int column) throws JavaModelException {
		if (unit == null) {
			return null;
		}
		int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
		if (offset > -1) {
			return unit.codeSelect(offset, 0);
		}
		return null;
	}

	public static IFile findFile(String uriString) {
		return findFile(toURI(uriString));
	}

	public static IFile findFile(URI uri) {
		if (uri == null || !"file".equals(uri.getScheme())) {
			return null;
		}
		IFile[] resources = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(uri);
		switch(resources.length) {
		case 0:
			return null;
		case 1:
			return resources[0];
		default://several candidates if a linked resource was created before the real project was configured
			IFile file = null;
			for (IFile f : resources) {
				//delete linked resource
				if (JavaLanguageServerPlugin.getProjectsManager().getDefaultProject().equals(f.getProject())) {
					try {
						f.delete(true, null);
					} catch (CoreException e) {
						e.printStackTrace();
					}
				}
				//find closest project containing that file, in case of nested projects
				if (file == null ||f.getProjectRelativePath().segmentCount() < file.getProjectRelativePath().segmentCount()) {
					file = f;
				}
			}
			return file;
		}
	}

	public static URI toURI(String uriString) {
		if (uriString == null || uriString.isEmpty()) {
			return null;
		}
		try {
			return new URI(uriString);
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uriString, e);
			return null;
		}
	}

	public static boolean isHiddenGeneratedElement(IJavaElement element) {
		// generated elements are tagged with javax.annotation.Generated and
		// they need to be filtered out
		if (element instanceof IAnnotatable) {
			try {
				IAnnotation[] annotations = ((IAnnotatable) element).getAnnotations();
				if (annotations.length != 0) {
					for (IAnnotation annotation : annotations) {
						if (isSilencedGeneratedAnnotation(annotation)) {
							return true;
						}
					}
				}
			} catch (JavaModelException e) {
				//ignore
			}
		}
		return false;
	}

	private static boolean isSilencedGeneratedAnnotation(IAnnotation annotation) throws JavaModelException {
		if ("javax.annotation.Generated".equals(annotation.getElementName())) {
			IMemberValuePair[] memberValuePairs = annotation.getMemberValuePairs();
			for (IMemberValuePair m : memberValuePairs) {
				if ("value".equals(m.getMemberName())
						&& IMemberValuePair.K_STRING == m.getValueKind()) {
					if (m.getValue() instanceof String) {
						return SILENCED_CODEGENS.contains(m.getValue());
					} else if (m.getValue() instanceof Object[]) {
						for (Object val : (Object[])m.getValue()) {
							if(SILENCED_CODEGENS.contains(val)) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

}
