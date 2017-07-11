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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.util.ClassFileBytesDisassembler;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
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

	private static final String JDT_SCHEME = "jdt";
	//Code generators known to cause problems
	private static Set<String> SILENCED_CODEGENS = Collections.singleton("lombok");
	public static final String MISSING_SOURCES_HEADER = " // Failed to get sources. Instead, stub sources have been generated.\n"
			+ " // Implementation of methods is unavailable.\n";
	private static final String LF = "\n";

	private JDTUtils() {
		//No public instantiation
	}

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
		if (uri == null || JDT_SCHEME.equals(uri.getScheme()) || !uri.isAbsolute()){
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
			return getFakeCompilationUnit(uri, new NullProgressMonitor());
		}
		//the resource is not null but no compilation unit could be created (eg. project not ready yet)
		return null;
	}

	static ICompilationUnit getFakeCompilationUnit(URI uri, IProgressMonitor monitor) {
		if (uri == null || !"file".equals(uri.getScheme()) || !uri.getPath().endsWith(".java")) {
			return null;
		}
		java.nio.file.Path path = Paths.get(uri);
		//Only support existing standalone java files
		if (!java.nio.file.Files.isReadable(path)) {
			return null;
		}

		IProject project = JavaLanguageServerPlugin.getProjectsManager().getDefaultProject();
		if (project == null || !project.isAccessible()) {
			return null;
		}
		IJavaProject javaProject = JavaCore.create(project);

		String packageName = getPackageName(javaProject, uri);
		String fileName = path.getName(path.getNameCount() - 1).toString();
		String packagePath = packageName.replace(".", "/");

		IPath filePath = new Path("src").append(packagePath).append(fileName);
		final IFile file = project.getFile(filePath);
		if (!file.isLinked()) {
			try {
				createFolders(file.getParent(), monitor);
				file.createLink(uri, IResource.REPLACE, monitor);
			} catch (CoreException e) {
				String errMsg = "Failed to create linked resource from " + uri + " to " + project.getName();
				JavaLanguageServerPlugin.logException(errMsg, e);
			}
		}
		if (file.isLinked()) {
			return (ICompilationUnit) JavaCore.create(file, javaProject);
		}
		return null;
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
		} catch (IOException e) {
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
		if (uri != null && JDT_SCHEME.equals(uri.getScheme()) && "contents".equals(uri.getAuthority())) {
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
		if (JDT_SCHEME.equals(uri.getScheme())) {
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
			ISourceRange nameRange = getNameRange(element);
			if (SourceRange.isAvailable(nameRange)) {
				if (cf == null) {
					return toLocation(unit, nameRange.getOffset(), nameRange.getLength());
				} else {
					return toLocation(cf, nameRange.getOffset(), nameRange.getLength());
				}
			}
		}
		return null;
	}

	private static ISourceRange getNameRange(IJavaElement element) throws JavaModelException {
		ISourceRange nameRange = null;
		if (element instanceof IMember) {
			IMember member = (IMember) element;
			nameRange = member.getNameRange();
			if ( (!SourceRange.isAvailable(nameRange))) {
				nameRange = member.getSourceRange();
			}
		} else if (element instanceof ITypeParameter || element instanceof ILocalVariable) {
			nameRange = ((ISourceReference) element).getNameRange();
		} else if (element instanceof ISourceReference) {
			nameRange = ((ISourceReference) element).getSourceRange();
		}
		if (!SourceRange.isAvailable(nameRange) && element.getParent() != null) {
			nameRange = getNameRange(element.getParent());
		}
		return nameRange;
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
		return new Location(getFileURI(unit), toRange(unit, offset, length));
	}

	/**
	 * Creates a default location for the class file.
	 *
	 * @param classFile
	 * @return location
	 * @throws JavaModelException
	 */
	public static Location toLocation(IClassFile classFile) throws JavaModelException{
		return toLocation(classFile, 0, 0);
	}

	/**
	 * Creates a default location for the uri.
	 *
	 * @param classFile
	 * @return location
	 * @throws JavaModelException
	 */
	public static Location toLocation(String uri) {
		return new Location(uri, newRange());
	}

	/**
	 * Creates location to the given offset and length for the class file.
	 *
	 * @param unit
	 * @param offset
	 * @param length
	 * @return location
	 * @throws JavaModelException
	 */
	public static Location toLocation(IClassFile classFile, int offset, int length) throws JavaModelException{
		String packageName = classFile.getParent().getElementName();
		String jarName = classFile.getParent().getParent().getElementName();
		String uriString = null;
		try {
			uriString = new URI(JDT_SCHEME, "contents", "/" + jarName + "/" + packageName + "/" + classFile.getElementName(), classFile.getHandleIdentifier(), null).toASCIIString();
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Error generating URI for class ", e);
		}
		Range range = toRange(classFile, offset, length);
		return new Location(uriString, range);
	}

	/**
	 * Creates a range for the given offset and length for an {@link IOpenable}
	 *
	 * @param openable
	 * @param offset
	 * @param length
	 * @return
	 * @throws JavaModelException
	 */
	public static Range toRange(IOpenable openable, int offset, int length) throws JavaModelException{
		Range range = newRange();
		if (offset > 0 || length > 0) {
			int[] loc = null;
			int[] endLoc = null;
			IBuffer buffer = openable.getBuffer();
			if (buffer != null) {
				loc = JsonRpcHelpers.toLine(buffer, offset);
				endLoc = JsonRpcHelpers.toLine(buffer, offset + length);
			}
			if (loc == null) {
				loc = new int[2];
			}
			if (endLoc == null) {
				endLoc = new int[2];
			}
			setPosition(range.getStart(), loc);
			setPosition(range.getEnd(), endLoc);
		}
		return range;
	}

	/**
	 * Creates a new {@link Range} with its start and end {@link Position}s set to line=0, character=0
	 *
	 * @return a new {@link Range};
	 */
	public static Range newRange() {
		return new Range(new Position(), new Position());
	}

	private static void setPosition(Position position, int[] coords) {
		assert coords.length == 2;
		position.setLine(coords[0]);
		position.setCharacter(coords[1]);
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
		if (unit instanceof IClassFile) {
			IClassFile classFile = (IClassFile) unit;
			String contents = disassemble(classFile);
			if (contents != null) {
				IDocument document = new Document(contents);
				try {
					offset = document.getLineOffset(line) + column;
					if (offset > -1) {
						String name = parse(contents, offset);
						if (name == null) {
							return null;
						}
						SearchPattern pattern = SearchPattern.createPattern(name, IJavaSearchConstants.TYPE,
								IJavaSearchConstants.DECLARATIONS, SearchPattern.R_FULL_MATCH);

						IJavaSearchScope scope = createSearchScope(unit.getJavaProject());

						List<IJavaElement> elements = new ArrayList<>();
						SearchRequestor requestor = new SearchRequestor() {
							@Override
							public void acceptSearchMatch(SearchMatch match) {
								if (match.getElement() instanceof IJavaElement) {
									elements.add((IJavaElement) match.getElement());
								}
							}
						};
						SearchEngine searchEngine = new SearchEngine();
						searchEngine.search(pattern,
								new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope,
								requestor, null);
						return elements.toArray(new IJavaElement[0]);
					}
				} catch (BadLocationException | CoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
			}
		}
		return null;
	}

	private static String parse(String contents, int offset) {
		if (contents == null || offset < 0 || contents.length() < offset
				|| !isJavaIdentifierOrPeriod(contents.charAt(offset))) {
			return null;
		}
		int start = offset;
		while (start - 1 > -1 && isJavaIdentifierOrPeriod(contents.charAt(start - 1))) {
			start--;
		}
		int end = offset;
		while (end <= contents.length() && isJavaIdentifierOrPeriod(contents.charAt(end))) {
			end++;
		}
		if (end >= start) {
			return contents.substring(start, end);
		}
		return null;
	}

	private static boolean isJavaIdentifierOrPeriod(char ch) {
		return Character.isJavaIdentifierPart(ch) || ch == '.';
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

	public static String disassemble(IClassFile classFile) {
		ClassFileBytesDisassembler disassembler = ToolFactory.createDefaultClassFileBytesDisassembler();
		String disassembledByteCode = null;
		try {
			disassembledByteCode = disassembler.disassemble(classFile.getBytes(), LF,
					ClassFileBytesDisassembler.WORKING_COPY);
			disassembledByteCode = MISSING_SOURCES_HEADER + LF + disassembledByteCode;
		} catch (Exception e) {
			JavaLanguageServerPlugin.logError("Unable to disassemble " + classFile.getHandleIdentifier());
		}
		return disassembledByteCode;
	}

	public static IJavaSearchScope createSearchScope(IJavaProject project) {
		if (project == null) {
			return SearchEngine.createWorkspaceScope();
		}
		return SearchEngine.createJavaSearchScope(new IJavaProject[] { project },
				IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
	}

}
