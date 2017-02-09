/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;

/**
 * Helper needed to get the content of a Javadoc comment.
 *
 * <p>
 * This class is not intended to be subclassed or instantiated by clients.
 * </p>
 *
 * @since 3.1
 *
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class JavadocContentAccess {
	/**
	 * The name of the package-info.java file.
	 */
	public static final String PACKAGE_INFO_JAVA= "package-info.java"; //$NON-NLS-1$

	/**
	 * The name of the package-info.class file.
	 */
	public static final String PACKAGE_INFO_CLASS= "package-info.class"; //$NON-NLS-1$

	private JavadocContentAccess() {
		// do not instantiate
	}

	/**
	 * Gets a reader for an IMember's Javadoc comment content from the source attachment.
	 * The content does contain only the text from the comment without the Javadoc leading star characters.
	 * Returns <code>null</code> if the member does not contain a Javadoc comment or if no source is available.
	 * @param member The member to get the Javadoc of.
	 * @param allowInherited For methods with no (Javadoc) comment, the comment of the overridden class
	 * is returned if <code>allowInherited</code> is <code>true</code>.
	 * @return Returns a reader for the Javadoc comment content or <code>null</code> if the member
	 * does not contain a Javadoc comment or if no source is available
	 * @throws JavaModelException is thrown when the elements javadoc can not be accessed
	 */
	public static Reader getContentReader(IMember member, boolean allowInherited) throws JavaModelException {
		Reader contentReader= internalGetContentReader(member);
		if (contentReader != null || !(allowInherited && (member.getElementType() == IJavaElement.METHOD)))
			return contentReader;
		return findDocInHierarchy((IMethod) member, false, false);
	}

	/**
	 * Gets a reader for an IMember's Javadoc comment content from the source attachment.
	 * The content does contain only the text from the comment without the Javadoc leading star characters.
	 * Returns <code>null</code> if the member does not contain a Javadoc comment or if no source is available.
	 * @param member The member to get the Javadoc of.
	 * @return Returns a reader for the Javadoc comment content or <code>null</code> if the member
	 * does not contain a Javadoc comment or if no source is available
	 * @throws JavaModelException is thrown when the elements javadoc can not be accessed
	 * @since 3.4
	 */
	private static Reader internalGetContentReader(IMember member) throws JavaModelException {
		IBuffer buf= member.getOpenable().getBuffer();
		if (buf == null) {
			return null; // no source attachment found
		}

		ISourceRange javadocRange= member.getJavadocRange();
		if (javadocRange != null) {
			JavaDocCommentReader reader= new JavaDocCommentReader(buf, javadocRange.getOffset(), javadocRange.getOffset() + javadocRange.getLength() - 1);
			if (!containsOnlyInheritDoc(reader, javadocRange.getLength())) {
				reader.reset();
				return reader;
			}
		}

		return null;
	}

	/**
	 * Gets a reader for an package fragment's Javadoc comment content from the source attachment.
	 * The content does contain only the text from the comment without the Javadoc leading star characters.
	 * Returns <code>null</code> if the package fragment does not contain a Javadoc comment or if no source is available.
	 * @param fragment The package fragment to get the Javadoc of.
	 * @return Returns a reader for the Javadoc comment content or <code>null</code> if the member
	 * does not contain a Javadoc comment or if no source is available
	 * @throws JavaModelException is thrown when the package fragment's javadoc can not be accessed
	 * @since 3.4
	 */
	private static Reader internalGetContentReader(IPackageFragment fragment) throws JavaModelException {
		IPackageFragmentRoot root= (IPackageFragmentRoot) fragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);

		//1==> Handle the case when the documentation is present in package-info.java or package-info.class file
		boolean isBinary= root.getKind() == IPackageFragmentRoot.K_BINARY;
		ITypeRoot packageInfo;
		if (isBinary) {
			packageInfo= fragment.getClassFile(PACKAGE_INFO_CLASS);
		} else {
			packageInfo= fragment.getCompilationUnit(PACKAGE_INFO_JAVA);
		}
		if (packageInfo != null && packageInfo.exists()) {
			String source = packageInfo.getSource();
			//the source can be null for some of the class files
			if (source != null) {
				Javadoc javadocNode = getPackageJavadocNode(fragment, source);
				if (javadocNode != null) {
					int start = javadocNode.getStartPosition();
					int length = javadocNode.getLength();
					return new JavaDocCommentReader(source, start, start + length - 1);
				}
			}
		}
		return null;
	}

	private static Javadoc getPackageJavadocNode(IJavaElement element, String cuSource) {
		CompilationUnit cu= createAST(element, cuSource);
		if (cu != null) {
			PackageDeclaration packDecl= cu.getPackage();
			if (packDecl != null) {
				return packDecl.getJavadoc();
			}
		}
		return null;
	}

	private static CompilationUnit createAST(IJavaElement element, String cuSource) {
		ASTParser parser= ASTParser.newParser(AST.JLS8);

		IJavaProject javaProject= element.getJavaProject();
		parser.setProject(javaProject);
		Map<String, String> options= javaProject.getOptions(true);
		options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED); // workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=212207
		parser.setCompilerOptions(options);

		parser.setSource(cuSource.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Checks whether the given reader only returns
	 * the inheritDoc tag.
	 *
	 * @param reader the reader
	 * @param length the length of the underlying content
	 * @return <code>true</code> if the reader only returns the inheritDoc tag
	 * @since 3.2
	 */
	private static boolean containsOnlyInheritDoc(Reader reader, int length) {
		char[] content= new char[length];
		try {
			reader.read(content, 0, length);
		} catch (IOException e) {
			return false;
		}
		return new String(content).trim().equals("{@inheritDoc}"); //$NON-NLS-1$

	}

	/**
	 * Gets a reader for an IMember's Javadoc comment content from the source attachment.
	 * and renders the tags in HTML.
	 * Returns <code>null</code> if the member does not contain a Javadoc comment or if no source is available.
	 *
	 * @param member				the member to get the Javadoc of.
	 * @param allowInherited		for methods with no (Javadoc) comment, the comment of the overridden
	 * 									class is returned if <code>allowInherited</code> is <code>true</code>
	 * @param useAttachedJavadoc	if <code>true</code> Javadoc will be extracted from attached Javadoc
	 * 									if there's no source
	 * @return a reader for the Javadoc comment content in HTML or <code>null</code> if the member
	 * 			does not contain a Javadoc comment or if no source is available
	 * @throws JavaModelException is thrown when the elements Javadoc can not be accessed
	 * @since 3.2
	 */
	public static Reader getHTMLContentReader(IMember member, boolean allowInherited, boolean useAttachedJavadoc) throws JavaModelException {
		Reader contentReader= internalGetContentReader(member);
		if (contentReader != null) {
			try {
				return new JavaDoc2MarkdownConverter(contentReader).getAsReader();
			} catch (IOException e) {
				throw new JavaModelException(e, IJavaModelStatusConstants.UNKNOWN_JAVADOC_FORMAT);
			}
		}

		if (useAttachedJavadoc && member.getOpenable().getBuffer() == null) { // only if no source available
			String s= member.getAttachedJavadoc(null);
			if (s != null)
				return new StringReader(s);
		}

		if (allowInherited && (member.getElementType() == IJavaElement.METHOD))
			return findDocInHierarchy((IMethod) member, true, useAttachedJavadoc);

		return null;
	}

	/**
	 * Gets a reader for a package fragment's Javadoc comment content from the source attachment.
	 * and renders the tags in HTML.
	 * Returns <code>null</code> if the package fragment does not contain a Javadoc comment or if no source is available.
	 *
	 * @param fragment				the package fragment to get the Javadoc of.
	 * @param useAttachedJavadoc	if <code>true</code> Javadoc will be extracted from attached Javadoc
	 * 									if there's no source
	 * @return a reader for the Javadoc comment content in HTML or <code>null</code> if the package fragment
	 * 			does not contain a Javadoc comment or if no source is available
	 * @throws JavaModelException is thrown when the package fragment's Javadoc can not be accessed
	 * @since 3.2
	 */
	public static Reader getHTMLContentReader(IPackageFragment fragment, boolean useAttachedJavadoc) throws JavaModelException {
		Reader contentReader= internalGetContentReader(fragment);
		if (contentReader != null) {
			try {
				return new JavaDoc2MarkdownConverter(contentReader).getAsReader();
			} catch (IOException e) {
				throw new JavaModelException(e, IJavaModelStatusConstants.UNKNOWN_JAVADOC_FORMAT);
			}
		}
		if (useAttachedJavadoc) {
			// only if no source available
			// check parent
			IPackageFragmentRoot root= (IPackageFragmentRoot) fragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);

			//1==> Handle the case when the documentation is present in package-info.java or package-info.class file
			boolean isBinary= root.getKind() == IPackageFragmentRoot.K_BINARY;
			if (isBinary) {
				String s= fragment.getAttachedJavadoc(null);
				if (s != null) {
					try {
						return new JavaDoc2MarkdownConverter(new StringReader(s)).getAsReader();
					} catch (IOException e) {
						throw new JavaModelException(e, IJavaModelStatusConstants.UNKNOWN_JAVADOC_FORMAT);
					}
				}
			}
		}
		return null;
	}

	private static Reader findDocInHierarchy(IMethod method, boolean isHTML, boolean useAttachedJavadoc) throws JavaModelException {
		/*
		 * Catch ExternalJavaProject in which case
		 * no hierarchy can be built.
		 */
		if (!method.getJavaProject().exists())
			return null;

		IType type= method.getDeclaringType();
		ITypeHierarchy hierarchy= type.newSupertypeHierarchy(null);

		MethodOverrideTester tester= new MethodOverrideTester(type, hierarchy);

		IType[] superTypes= hierarchy.getAllSupertypes(type);
		for (int i= 0; i < superTypes.length; i++) {
			IType curr= superTypes[i];
			IMethod overridden= tester.findOverriddenMethodInType(curr, method);
			if (overridden != null) {
				Reader reader;
				if (isHTML)
					reader= getHTMLContentReader(overridden, false, useAttachedJavadoc);
				else
					reader= getContentReader(overridden, false);
				if (reader != null)
					return reader;
			}
		}
		return null;
	}

}
