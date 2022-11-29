/*******************************************************************************
 * Copyright (c) 2008, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Code copied from org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Tom Hofmann, Google <eclipse@tom.eicher.name> - [hovering] NPE when hovering over @value reference within a type's javadoc - https://bugs.eclipse.org/bugs/show_bug.cgi?id=320084
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TagProperty;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;

/**
 * Helper to get the content of a Javadoc comment as HTML.
 *
 * <p>
 * <strong>This is work in progress. Parts of this will later become API through
 * {@link JavadocContentAccess}</strong>
 * </p>
 *
 * @since 3.4
 */
public class JavadocContentAccess2 {

	private static final String UL_CLASS_BLOCK_LIST = "<ul class=\"blockList\">";

	private static final String BASE_URL_COMMENT_INTRO = "<!-- baseURL=\""; //$NON-NLS-1$

	private static final String BLOCK_TAG_START = "<ul>"; //$NON-NLS-1$
	private static final String BLOCK_TAG_END = "</ul>"; //$NON-NLS-1$

	public static final String BlOCK_TAG_TITLE_START= "<dt>"; //$NON-NLS-1$
	public static final String BlOCK_TAG_TITLE_END= "</dt>"; //$NON-NLS-1$

	private static final String BlOCK_TAG_ENTRY_START = "<li>"; //$NON-NLS-1$
	private static final String BlOCK_TAG_ENTRY_END = "</li>"; //$NON-NLS-1$

	private static final String PARAM_NAME_START = "<b>"; //$NON-NLS-1$
	private static final String PARAM_NAME_END = "</b> "; //$NON-NLS-1$

	private static final String JavaDoc2HTMLTextReader_parameters_section = "Parameters:";
	private static final String JavaDoc2HTMLTextReader_returns_section = "Returns:";
	private static final String JavaDoc2HTMLTextReader_throws_section = "Throws:";
	private static final String JavaDoc2HTMLTextReader_type_parameters_section = "Type Parameters:";
	private static final String JavaDoc2HTMLTextReader_author_section = "Author:";
	private static final String JavaDoc2HTMLTextReader_deprecated_section = "Deprecated";
	private static final String JavaDoc2HTMLTextReader_method_in_type = "{0}in{1}";
	private static final String JavaDoc2HTMLTextReader_overrides_section = "Overrides:";
	private static final String JavaDoc2HTMLTextReader_see_section = "See Also:";
	private static final String JavaDoc2HTMLTextReader_api_note = "API Note:";
	private static final String JavaDoc2HTMLTextReader_since_section = "Since:";
	private static final String JavaDoc2HTMLTextReader_specified_by_section = "Specified by:";
	private static final String JavaDoc2HTMLTextReader_version_section = "Version:";
	private static final String JavadocContentAccess2_getproperty_message = "<p>Gets the value ofthe property{0}.</p><dl><dt>Property Description:</dt><dd>{1}</dd></dl>";
	private static final String JavadocContentAccess2_setproperty_message = "<p>Sets the value ofthe property {0}.</p><dl><dt>Property Description:</dt><dd>{1}</dd></dl>";

	private static final String CONTENT_CONTAINER = "<div class=\"contentContainer\">";

	/**
	 * Implements the "Algorithm for Inheriting Method Comments" as specified for
	 * <a href=
	 * "http://download.oracle.com/javase/1.4.2/docs/tooldocs/solaris/javadoc.html#inheritingcomments"
	 * >1.4.2</a>, <a href=
	 * "http://download.oracle.com/javase/1.5.0/docs/tooldocs/windows/javadoc.html#inheritingcomments"
	 * >1.5</a>, and <a href=
	 * "http://download.oracle.com/javase/6/docs/technotes/tools/windows/javadoc.html#inheritingcomments"
	 * >1.6</a>.
	 *
	 * <p>
	 * Unfortunately, the implementation is broken in Javadoc implementations since
	 * 1.5, see
	 * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6376959">Sun's
	 * bug</a>.
	 * </p>
	 *
	 * <p>
	 * We adhere to the spec.
	 * </p>
	 */
	private static abstract class InheritDocVisitor {
		public static final Object STOP_BRANCH = new Object() {
			@Override
			public String toString() {
				return "STOP_BRANCH"; //$NON-NLS-1$
			}
		};
		public static final Object CONTINUE = new Object() {
			@Override
			public String toString() {
				return "CONTINUE"; //$NON-NLS-1$
			}
		};

		/**
		 * Visits a type and decides how the visitor should proceed.
		 *
		 * @param currType
		 *            the current type
		 * @return
		 *         <ul>
		 *         <li>{@link #STOP_BRANCH} to indicate that no Javadoc has been found
		 *         and visiting super types should stop here</li>
		 *         <li>{@link #CONTINUE} to indicate that no Javadoc has been found and
		 *         visiting super types should continue</li>
		 *         <li>an {@link Object} or <code>null</code>, to indicate that visiting
		 *         should be cancelled immediately. The returned value is the result of
		 *         {@link #visitInheritDoc(IType, ITypeHierarchy)}</li>
		 *         </ul>
		 * @throws JavaModelException
		 *             unexpected problem
		 * @see #visitInheritDoc(IType, ITypeHierarchy)
		 */
		public abstract Object visit(IType currType) throws JavaModelException;

		/**
		 * Visits the super types of the given <code>currentType</code>.
		 *
		 * @param currentType
		 *            the starting type
		 * @param typeHierarchy
		 *            a super type hierarchy that contains <code>currentType</code>
		 * @return the result from a call to {@link #visit(IType)}, or <code>null</code>
		 *         if none of the calls returned a result
		 * @throws JavaModelException
		 *             unexpected problem
		 */
		public Object visitInheritDoc(IType currentType, ITypeHierarchy typeHierarchy) throws JavaModelException {
			ArrayList<IType> visited = new ArrayList<>();
			visited.add(currentType);
			Object result = visitInheritDocInterfaces(visited, currentType, typeHierarchy);
			if (result != InheritDocVisitor.CONTINUE) {
				return result;
			}

			IType superClass;
			if (currentType.isInterface()) {
				superClass = currentType.getJavaProject().findType("java.lang.Object"); //$NON-NLS-1$
			} else {
				superClass = typeHierarchy.getSuperclass(currentType);
			}

			while (superClass != null && !visited.contains(superClass)) {
				result = visit(superClass);
				if (result == InheritDocVisitor.STOP_BRANCH) {
					return null;
				} else if (result == InheritDocVisitor.CONTINUE) {
					visited.add(superClass);
					result = visitInheritDocInterfaces(visited, superClass, typeHierarchy);
					if (result != InheritDocVisitor.CONTINUE) {
						return result;
					} else {
						superClass = typeHierarchy.getSuperclass(superClass);
					}
				} else {
					return result;
				}
			}

			return null;
		}

		/**
		 * Visits the super interfaces of the given type in the given hierarchy, thereby
		 * skipping already visited types.
		 *
		 * @param visited
		 *            set of visited types
		 * @param currentType
		 *            type whose super interfaces should be visited
		 * @param typeHierarchy
		 *            type hierarchy (must include <code>currentType</code>)
		 * @return the result, or {@link #CONTINUE} if no result has been found
		 * @throws JavaModelException
		 *             unexpected problem
		 */
		private Object visitInheritDocInterfaces(ArrayList<IType> visited, IType currentType, ITypeHierarchy typeHierarchy) throws JavaModelException {
			ArrayList<IType> toVisitChildren = new ArrayList<>();
			IType[] superInterfaces = typeHierarchy.getSuperInterfaces(currentType);
			for (int i = 0; i < superInterfaces.length; i++) {
				IType superInterface = superInterfaces[i];
				if (visited.contains(superInterface)) {
					continue;
				}
				visited.add(superInterface);
				Object result = visit(superInterface);
				if (result == InheritDocVisitor.STOP_BRANCH) {
					//skip
				} else if (result == InheritDocVisitor.CONTINUE) {
					toVisitChildren.add(superInterface);
				} else {
					return result;
				}
			}
			for (Iterator<IType> iter = toVisitChildren.iterator(); iter.hasNext();) {
				IType child = iter.next();
				Object result = visitInheritDocInterfaces(visited, child, typeHierarchy);
				if (result != InheritDocVisitor.CONTINUE) {
					return result;
				}
			}
			return InheritDocVisitor.CONTINUE;
		}
	}

	private static class JavadocLookup {
		private static final JavadocLookup NONE = new JavadocLookup(null) {
			@Override
			public CharSequence getInheritedMainDescription(IMethod method) {
				return null;
			}

			@Override
			public CharSequence getInheritedParamDescription(IMethod method, int i) {
				return null;
			}

			@Override
			public CharSequence getInheritedReturnDescription(IMethod method) {
				return null;
			}

			@Override
			public CharSequence getInheritedExceptionDescription(IMethod method, String name) {
				return null;
			}
		};

		private static interface DescriptionGetter {
			/**
			 * Returns a Javadoc tag description or <code>null</code>.
			 *
			 * @param contentAccess
			 *            the content access
			 * @return the description, or <code>null</code> if none
			 * @throws JavaModelException
			 *             unexpected problem
			 */
			CharSequence getDescription(JavadocContentAccess2 contentAccess) throws JavaModelException;
		}

		private final IType fStartingType;
		private final HashMap<IMethod, JavadocContentAccess2> fContentAccesses;

		private ITypeHierarchy fTypeHierarchy;
		private MethodOverrideTester fOverrideTester;

		private JavadocLookup(IType startingType) {
			fStartingType = startingType;
			fContentAccesses = new HashMap<>();
		}

		/**
		 * For the given method, returns the main description from an overridden method.
		 *
		 * @param method
		 *            a method
		 * @return the description that replaces the <code>{&#64;inheritDoc}</code> tag,
		 *         or <code>null</code> if none could be found
		 */
		public CharSequence getInheritedMainDescription(IMethod method) {
			return getInheritedDescription(method, new DescriptionGetter() {
				@Override
				public CharSequence getDescription(JavadocContentAccess2 contentAccess) {
					return contentAccess.getMainDescription();
				}
			});
		}

		/**
		 * For the given method, returns the @param tag description for the given type
		 * parameter from an overridden method.
		 *
		 * @param method
		 *            a method
		 * @param typeParamIndex
		 *            the index of the type parameter
		 * @return the description that replaces the <code>{&#64;inheritDoc}</code> tag,
		 *         or <code>null</code> if none could be found
		 */
		public CharSequence getInheritedTypeParamDescription(IMethod method, final int typeParamIndex) {
			return getInheritedDescription(method, new DescriptionGetter() {
				@Override
				public CharSequence getDescription(JavadocContentAccess2 contentAccess) throws JavaModelException {
					return contentAccess.getInheritedTypeParamDescription(typeParamIndex);
				}
			});
		}

		/**
		 * For the given method, returns the @param tag description for the given
		 * parameter from an overridden method.
		 *
		 * @param method
		 *            a method
		 * @param paramIndex
		 *            the index of the parameter
		 * @return the description that replaces the <code>{&#64;inheritDoc}</code> tag,
		 *         or <code>null</code> if none could be found
		 */
		public CharSequence getInheritedParamDescription(IMethod method, final int paramIndex) {
			return getInheritedDescription(method, new DescriptionGetter() {
				@Override
				public CharSequence getDescription(JavadocContentAccess2 contentAccess) throws JavaModelException {
					return contentAccess.getInheritedParamDescription(paramIndex);
				}
			});
		}

		/**
		 * For the given method, returns the @return tag description from an overridden
		 * method.
		 *
		 * @param method
		 *            a method
		 * @return the description that replaces the <code>{&#64;inheritDoc}</code> tag,
		 *         or <code>null</code> if none could be found
		 */
		public CharSequence getInheritedReturnDescription(IMethod method) {
			return getInheritedDescription(method, new DescriptionGetter() {
				@Override
				public CharSequence getDescription(JavadocContentAccess2 contentAccess) {
					return contentAccess.getReturnDescription();
				}
			});
		}

		/**
		 * For the given method, returns the @throws/@exception tag description for the
		 * given exception from an overridden method.
		 *
		 * @param method
		 *            a method
		 * @param simpleName
		 *            the simple name of an exception
		 * @return the description that replaces the <code>{&#64;inheritDoc}</code> tag,
		 *         or <code>null</code> if none could be found
		 */
		public CharSequence getInheritedExceptionDescription(IMethod method, final String simpleName) {
			return getInheritedDescription(method, new DescriptionGetter() {
				@Override
				public CharSequence getDescription(JavadocContentAccess2 contentAccess) {
					return contentAccess.getExceptionDescription(simpleName);
				}
			});
		}

		private CharSequence getInheritedDescription(final IMethod method, final DescriptionGetter descriptionGetter) {
			try {
				return (CharSequence) new InheritDocVisitor() {
					@Override
					public Object visit(IType currType) throws JavaModelException {
						IMethod overridden = getOverrideTester().findOverriddenMethodInType(currType, method);
						if (overridden == null) {
							return InheritDocVisitor.CONTINUE;
						}

						JavadocContentAccess2 contentAccess = getJavadocContentAccess(overridden);
						if (contentAccess == null) {
							if (overridden.getOpenable().getBuffer() == null) {
								// Don't continue this branch when no source is available.
								// We don't extract individual tags from Javadoc attachments,
								// and it would be wrong to copy doc from further up the branch,
								// thereby skipping doc from this overridden method.
								return InheritDocVisitor.STOP_BRANCH;
							} else {
								return InheritDocVisitor.CONTINUE;
							}
						}

						CharSequence overriddenDescription = descriptionGetter.getDescription(contentAccess);
						if (overriddenDescription != null) {
							return overriddenDescription;
						} else {
							return InheritDocVisitor.CONTINUE;
						}
					}
				}.visitInheritDoc(method.getDeclaringType(), getTypeHierarchy());
			} catch (JavaModelException e) {

			}
			return null;
		}

		/**
		 * @param method
		 *            the method
		 * @return the Javadoc content access for the given method, or <code>null</code>
		 *         if no Javadoc could be found in source
		 * @throws JavaModelException
		 *             unexpected problem
		 */
		private JavadocContentAccess2 getJavadocContentAccess(IMethod method) throws JavaModelException {
			Object cached = fContentAccesses.get(method);
			if (cached != null) {
				return (JavadocContentAccess2) cached;
			}
			if (fContentAccesses.containsKey(method)) {
				return null;
			}

			IBuffer buf = method.getOpenable().getBuffer();
			if (buf == null) { // no source attachment found
				fContentAccesses.put(method, null);
				return null;
			}

			ISourceRange javadocRange = method.getJavadocRange();
			if (javadocRange == null) {
				fContentAccesses.put(method, null);
				return null;
			}

			String rawJavadoc = buf.getText(javadocRange.getOffset(), javadocRange.getLength());
			Javadoc javadoc = getJavadocNode(method, rawJavadoc);
			if (javadoc == null) {
				fContentAccesses.put(method, null);
				return null;
			}

			JavadocContentAccess2 contentAccess = new JavadocContentAccess2(method, javadoc, rawJavadoc, this);
			fContentAccesses.put(method, contentAccess);
			return contentAccess;
		}

		private ITypeHierarchy getTypeHierarchy() throws JavaModelException {
			if (fTypeHierarchy == null) {
				fTypeHierarchy = SuperTypeHierarchyCache.getTypeHierarchy(fStartingType);
			}
			return fTypeHierarchy;
		}

		private MethodOverrideTester getOverrideTester() throws JavaModelException {
			if (fOverrideTester == null) {
				fOverrideTester = SuperTypeHierarchyCache.getMethodOverrideTester(fStartingType);
			}
			return fOverrideTester;
		}
	}

	/**
	 * Either an IMember or an IPackageFragment.
	 */
	private final IJavaElement fElement;

	private final JavaDocSnippetStringEvaluator fSnippetStringEvaluator;

	/**
	 * The method, or <code>null</code> if {@link #fElement} is not a method
	 * where @inheritDoc could work.
	 */
	private final IMethod fMethod;
	private final Javadoc fJavadoc;
	private final String fSource;
	private final JavadocLookup fJavadocLookup;

	private StringBuffer fBuf;
	private int fLiteralContent;
	private StringBuffer fMainDescription;
	private StringBuffer fReturnDescription;
	private StringBuffer[] fTypeParamDescriptions;
	private StringBuffer[] fParamDescriptions;
	private HashMap<String, StringBuffer> fExceptionDescriptions;

	private JavadocContentAccess2(IJavaElement element, Javadoc javadoc, String source, JavadocLookup lookup) {
		Assert.isNotNull(element);
		Assert.isTrue(element instanceof IMethod || element instanceof ILocalVariable || element instanceof ITypeParameter);
		fElement = element;
		fSnippetStringEvaluator= new JavaDocSnippetStringEvaluator(fElement);
		fMethod = (IMethod) ((element instanceof ILocalVariable || element instanceof ITypeParameter) ? element.getParent() : element);
		fJavadoc = javadoc;
		fSource = source;
		fJavadocLookup = lookup;
	}

	private JavadocContentAccess2(IJavaElement element, Javadoc javadoc, String source) {
		Assert.isTrue(element instanceof IMember || element instanceof IPackageFragment || element instanceof ILocalVariable || element instanceof ITypeParameter);
		fElement = element;
		fSnippetStringEvaluator= new JavaDocSnippetStringEvaluator(fElement);
		fMethod = null;
		fJavadoc = javadoc;
		fSource = source;
		fJavadocLookup = JavadocLookup.NONE;
	}

	/**
	 * Gets an IJavaElement's Javadoc comment content from the source or Javadoc
	 * attachment and renders the tags and links in HTML. Returns <code>null</code>
	 * if the element does not have a Javadoc comment or if no source is available.
	 *
	 * @param element
	 *            the element to get the Javadoc of
	 * @param useAttachedJavadoc
	 *            if <code>true</code> Javadoc will be extracted from attached
	 *            Javadoc if there's no source
	 * @return the Javadoc comment content in HTML or <code>null</code> if the
	 *         element does not have a Javadoc comment or if no source is available
	 * @throws CoreException
	 *             is thrown when the element's Javadoc cannot be accessed
	 */
	public static String getHTMLContent(IJavaElement element, boolean useAttachedJavadoc) throws CoreException {
		if (element instanceof IPackageFragment packageFragment) {
			return getHTMLContent(packageFragment);
		}
		if (element instanceof IPackageDeclaration packageDecl) {
			return getHTMLContent(packageDecl);
		}
		if (!(element instanceof IMember || element instanceof ITypeParameter || (element instanceof ILocalVariable && (((ILocalVariable) element).isParameter())))) {
			return null;
		}
		String sourceJavadoc = getHTMLContentFromSource(element);
		if (sourceJavadoc == null || sourceJavadoc.length() == 0 || sourceJavadoc.trim().equals("{@inheritDoc}")) { //$NON-NLS-1$
			if (useAttachedJavadoc) {
				if (element.getOpenable().getBuffer() == null) { // only if no source available
					try {
						return element.getAttachedJavadoc(null);
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
						return null;
					}
				}
				IMember member = null;
				if (element instanceof ILocalVariable localVariable) {
					member = localVariable.getDeclaringMember();
				} else if (element instanceof ITypeParameter typeParam) {
					member = typeParam.getDeclaringMember();
				} else if (element instanceof IMember memberElement) {
					member = memberElement;
				}
				if (canInheritJavadoc(member)) {
					IMethod method = (IMethod) member;
					String attachedDocInHierarchy = findAttachedDocInHierarchy(method);

					if (attachedDocInHierarchy == null) {
						return sourceJavadoc;
					}
					StringBuffer superMethodReferences = createSuperMethodReferences(method);
					if (superMethodReferences == null) {
						return attachedDocInHierarchy;
					}
					superMethodReferences.append(attachedDocInHierarchy);
					return superMethodReferences.toString();
				}
			}
		}
		return sourceJavadoc;
	}

	private static StringBuffer createSuperMethodReferences(final IMethod method) throws JavaModelException {
		IType type = method.getDeclaringType();
		ITypeHierarchy hierarchy = SuperTypeHierarchyCache.getTypeHierarchy(type);
		final MethodOverrideTester tester = SuperTypeHierarchyCache.getMethodOverrideTester(type);

		final ArrayList<IMethod> superInterfaceMethods = new ArrayList<>();
		final IMethod[] superClassMethod = { null };
		new InheritDocVisitor() {
			@Override
			public Object visit(IType currType) throws JavaModelException {
				IMethod overridden = tester.findOverriddenMethodInType(currType, method);
				if (overridden == null) {
					return InheritDocVisitor.CONTINUE;
				}

				if (currType.isInterface()) {
					superInterfaceMethods.add(overridden);
				} else {
					superClassMethod[0] = overridden;
				}

				return STOP_BRANCH;
			}
		}.visitInheritDoc(type, hierarchy);

		boolean hasSuperInterfaceMethods = superInterfaceMethods.size() != 0;
		if (!hasSuperInterfaceMethods && superClassMethod[0] == null) {
			return null;
		}

		StringBuffer buf = new StringBuffer();
		//		buf.append("<div>"); //$NON-NLS-1$
		//		if (hasSuperInterfaceMethods) {
		//			buf.append("<b>"); //$NON-NLS-1$
		//			buf.append(JavaDoc2HTMLTextReader_specified_by_section);
		//			buf.append("</b> "); //$NON-NLS-1$
		//			for (Iterator<IMethod> iter = superInterfaceMethods.iterator(); iter.hasNext();) {
		//				IMethod overridden = iter.next();
		//				buf.append(createMethodInTypeLinks(overridden));
		//				if (iter.hasNext()) {
		//					buf.append(JavaElementLabels.COMMA_STRING);
		//				}
		//			}
		//		}
		//		if (superClassMethod[0] != null) {
		//			if (hasSuperInterfaceMethods) {
		//				buf.append(JavaElementLabels.COMMA_STRING);
		//			}
		//			buf.append("<b>"); //$NON-NLS-1$
		//			buf.append(JavaDoc2HTMLTextReader_overrides_section);
		//			buf.append("</b> "); //$NON-NLS-1$
		//			buf.append(createMethodInTypeLinks(superClassMethod[0]));
		//		}
		//		buf.append("</div>"); //$NON-NLS-1$
		return buf;
	}

	private static String createMethodInTypeLinks(IMethod overridden) {
		CharSequence methodLink = createSimpleMemberLink(overridden);
		CharSequence typeLink = createSimpleMemberLink(overridden.getDeclaringType());
		String methodInType = MessageFormat.format(JavaDoc2HTMLTextReader_method_in_type, new Object[] { methodLink, typeLink });
		return methodInType;
	}

	private static CharSequence createSimpleMemberLink(IMember member) {
		StringBuffer buf = new StringBuffer();
		buf.append("<a href='"); //$NON-NLS-1$
		try {
			String uri = JavaElementLinks.createURI(null, member);
			buf.append(uri);
		} catch (URISyntaxException e) {

		}
		buf.append("'>"); //$NON-NLS-1$
		//JavaElementLabels.getElementLabel(member, 0, buf);
		buf.append("</a>"); //$NON-NLS-1$
		return buf;
	}

	private static String getHTMLContentFromSource(IJavaElement element) throws JavaModelException {
		IMember member;
		if (element instanceof ILocalVariable localVariable) {
			member = localVariable.getDeclaringMember();
		} else if (element instanceof ITypeParameter typeParam) {
			member = typeParam.getDeclaringMember();
		} else if (element instanceof IMember memberElement) {
			member = memberElement;
		} else {
			return null;
		}

		IBuffer buf = member.getOpenable().getBuffer();
		if (buf == null) {
			return null; // no source attachment found
		}

		ISourceRange javadocRange = member.getJavadocRange();
		if (javadocRange == null) {
			if (canInheritJavadoc(member)) {
				// Try to use the inheritDoc algorithm.
				String inheritedJavadoc = javadoc2HTML(member, element, "/***/"); //$NON-NLS-1$
				if (inheritedJavadoc != null && inheritedJavadoc.length() > 0) {
					return inheritedJavadoc;
				}
			}
			return getJavaFxPropertyDoc(member);
		}

		String rawJavadoc = buf.getText(javadocRange.getOffset(), javadocRange.getLength());
		return javadoc2HTML(member, element, rawJavadoc);
	}

	private static String getJavaFxPropertyDoc(IMember member) throws JavaModelException {
		// XXX: should not do this by default (but we don't have settings for Javadoc, see https://bugs.eclipse.org/424283 )
		if (member instanceof IMethod) {
			String name = member.getElementName();
			boolean isGetter = name.startsWith("get") && name.length() > 3; //$NON-NLS-1$
			boolean isBooleanGetter = name.startsWith("is") && name.length() > 2; //$NON-NLS-1$
			boolean isSetter = name.startsWith("set") && name.length() > 3; //$NON-NLS-1$

			if (isGetter || isBooleanGetter || isSetter) {
				String propertyName = firstToLower(name.substring(isBooleanGetter ? 2 : 3));
				IType type = member.getDeclaringType();
				IMethod method = type.getMethod(propertyName + "Property", new String[0]); //$NON-NLS-1$

				if (method.exists()) {
					String content = getHTMLContentFromSource(method);
					if (content != null) {
						if (isSetter) {
							//content = Messages.format(JavadocContentAccess2_setproperty_message, new Object[] { propertyName, content });
						} else {
							//content = Messages.format(JavadocContentAccess2_getproperty_message, new Object[] { propertyName, content });
						}
					}
					return content;
				}
			} else if (name.endsWith("Property")) { //$NON-NLS-1$
				String propertyName = name.substring(0, name.length() - 8);

				IType type = member.getDeclaringType();
				IField field = type.getField(propertyName);
				if (field.exists()) {
					return getHTMLContentFromSource(field);
				}
			}
		}
		return null;
	}

	private static String firstToLower(String propertyName) {
		char[] c = propertyName.toCharArray();
		c[0] = Character.toLowerCase(c[0]);
		return String.valueOf(c);
	}

	private static Javadoc getJavadocNode(IJavaElement element, String rawJavadoc) {
		//FIXME: take from SharedASTProvider if available
		//Caveat: Javadoc nodes are not available when Javadoc processing has been disabled!
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=212207

		String source = rawJavadoc + "class C{}"; //$NON-NLS-1$
		CompilationUnit root = createAST(element, source);
		if (root == null) {
			return null;
		}
		List<AbstractTypeDeclaration> types = root.types();
		if (types.size() != 1) {
			return null;
		}
		AbstractTypeDeclaration type = types.get(0);
		return type.getJavadoc();
	}

	private static Javadoc getPackageJavadocNode(IJavaElement element, String cuSource) {
		CompilationUnit cu = createAST(element, cuSource);
		if (cu != null) {
			PackageDeclaration packDecl = cu.getPackage();
			if (packDecl != null) {
				return packDecl.getJavadoc();
			}
		}
		return null;
	}

	private static CompilationUnit createAST(IJavaElement element, String cuSource) {
		Assert.isNotNull(element);
		ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);

		IJavaProject javaProject = element.getJavaProject();
		parser.setProject(javaProject);
		Map<String, String> options = javaProject.getOptions(true);
		options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED); // workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=212207
		parser.setCompilerOptions(options);

		parser.setSource(cuSource.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	private static String javadoc2HTML(IMember member, IJavaElement element, String rawJavadoc) {
		Javadoc javadoc = getJavadocNode(member, rawJavadoc);

		if (javadoc == null) {
			Reader contentReader = null;
			// fall back to JavadocContentAccess:
			try {
				contentReader = JavadocContentAccess.getHTMLContentReader(member, false, false);
				if (contentReader != null) {
					return getString(contentReader);
				}
			} catch (JavaModelException e) {

			} finally {
				if (contentReader != null) {
					try {
						contentReader.close();
					} catch (IOException e) {
						//ignore
					}
				}
			}
			return null;
		}

		if (canInheritJavadoc(member)) {
			IMethod method = (IMethod) member;
			return new JavadocContentAccess2(element, javadoc, rawJavadoc, new JavadocLookup(method.getDeclaringType())).toHTML();
		}
		return new JavadocContentAccess2(element, javadoc, rawJavadoc).toHTML();
	}

	private static boolean canInheritJavadoc(IMember member) {
		if (member instanceof IMethod method && member.getJavaProject().exists()) {
			/*
			 * Exists test catches ExternalJavaProject, in which case no hierarchy can be built.
			 */
			try {
				return !method.isConstructor();
			} catch (JavaModelException e) {

			}
		}
		return false;
	}

	/**
	 * Gets the reader content as a String
	 *
	 * @param reader
	 *            the reader
	 * @return the reader content as string
	 */
	private static String getString(Reader reader) {
		StringBuffer buf = new StringBuffer();
		char[] buffer = new char[1024];
		int count;
		try {
			while ((count = reader.read(buffer)) != -1) {
				buf.append(buffer, 0, count);
			}
		} catch (IOException e) {
			return null;
		}
		return buf.toString();
	}

	/**
	 * Finds the first available attached Javadoc in the hierarchy of the given
	 * method.
	 *
	 * @param method
	 *            the method
	 * @return the inherited Javadoc from the Javadoc attachment, or
	 *         <code>null</code> if none
	 * @throws JavaModelException
	 *             unexpected problem
	 */
	private static String findAttachedDocInHierarchy(final IMethod method) throws JavaModelException {
		IType type = method.getDeclaringType();
		ITypeHierarchy hierarchy = SuperTypeHierarchyCache.getTypeHierarchy(type);
		final MethodOverrideTester tester = SuperTypeHierarchyCache.getMethodOverrideTester(type);

		return (String) new InheritDocVisitor() {
			@Override
			public Object visit(IType currType) throws JavaModelException {
				IMethod overridden = tester.findOverriddenMethodInType(currType, method);
				if (overridden == null) {
					return InheritDocVisitor.CONTINUE;
				}

				if (overridden.getOpenable().getBuffer() == null) { // only if no source available
					String attachedJavadoc = overridden.getAttachedJavadoc(null);
					if (attachedJavadoc != null) {
						// BaseURL for the original method can be wrong for attached Javadoc from overridden
						// (e.g. when overridden is from rt.jar).
						// Fix is to store the baseURL inside the doc content and later fetch it with #extractBaseURL(String).
						String baseURL = JavaDocLocations.getBaseURL(overridden, overridden.isBinary());
						if (baseURL != null) {
							attachedJavadoc = BASE_URL_COMMENT_INTRO + baseURL + "\"--> " + attachedJavadoc; //$NON-NLS-1$
						}
						return attachedJavadoc;
					}
				}
				return CONTINUE;
			}
		}.visitInheritDoc(type, hierarchy);
	}

	private String toHTML() {
		fBuf = new StringBuffer();
		fLiteralContent = 0;

		if (fElement instanceof ILocalVariable || fElement instanceof ITypeParameter) {
			parameterToHTML();
		} else {
			elementToHTML();
		}

		String result = fBuf.toString();
		fBuf = null;
		return result;
	}

	private void parameterToHTML() {
		String elementName = fElement.getElementName();
		List<TagElement> tags = fJavadoc.tags();
		for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
			TagElement tag = iter.next();
			String tagName = tag.getTagName();
			if (TagElement.TAG_PARAM.equals(tagName)) {
				List<? extends ASTNode> fragments = tag.fragments();
				int size = fragments.size();
				if (size > 0) {
					Object first = fragments.get(0);
					if (first instanceof SimpleName simpleName) {
						String name = simpleName.getIdentifier();
						if (elementName.equals(name)) {
							handleContentElements(fragments.subList(1, size));
							return;
						}
					} else if (size > 2 && fElement instanceof ITypeParameter && first instanceof TextElement textElement) {
						String firstText = textElement.getText();
						if ("<".equals(firstText)) { //$NON-NLS-1$
							Object second = fragments.get(1);
							Object third = fragments.get(2);
							if (second instanceof SimpleName secondName && third instanceof TextElement thirdTextElement) {
								String name = secondName.getIdentifier();
								String thirdText = thirdTextElement.getText();
								if (elementName.equals(name) && ">".equals(thirdText)) { //$NON-NLS-1$
									handleContentElements(fragments.subList(3, size));
									return;
								}
							}
						}
					}
				}
			}
		}
		if (fElement instanceof ILocalVariable) {
			List<String> parameterNames = initParameterNames();
			int i = parameterNames.indexOf(elementName);
			if (i != -1) {
				CharSequence inheritedParamDescription = fJavadocLookup.getInheritedParamDescription(fMethod, i);
				handleInherited(inheritedParamDescription);
			}
		} else if (fElement instanceof ITypeParameter) {
			List<String> typeParameterNames = initTypeParameterNames();
			int i = typeParameterNames.indexOf(elementName);
			if (i != -1) {
				CharSequence inheritedTypeParamDescription = fJavadocLookup.getInheritedTypeParamDescription(fMethod, i);
				handleInherited(inheritedTypeParamDescription);
			}
		}
	}

	private void elementToHTML() {
		// After first loop, non-null entries in the following two lists are missing and need to be inherited:
		List<String> typeParameterNames = initTypeParameterNames();
		List<String> parameterNames = initParameterNames();
		List<String> exceptionNames = initExceptionNames();

		TagElement deprecatedTag = null;
		TagElement start = null;
		List<TagElement> typeParameters = new ArrayList<>();
		List<TagElement> parameters = new ArrayList<>();
		TagElement returnTag = null;
		List<TagElement> exceptions = new ArrayList<>();
		List<TagElement> versions = new ArrayList<>();
		List<TagElement> authors = new ArrayList<>();
		List<TagElement> sees = new ArrayList<>();
		List<TagElement> since = new ArrayList<>();
		List<TagElement> rest = new ArrayList<>();
		List<TagElement> apinote = new ArrayList<>(1);

		List<TagElement> tags = fJavadoc.tags();
		for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
			TagElement tag = iter.next();
			String tagName = tag.getTagName();
			if (tagName == null) {
				start = tag;

			} else if (TagElement.TAG_PARAM.equals(tagName)) {
				List<? extends ASTNode> fragments = tag.fragments();
				int size = fragments.size();
				if (size > 0) {
					Object first = fragments.get(0);
					if (first instanceof SimpleName simpleName) {
						String name = simpleName.getIdentifier();
						int paramIndex = parameterNames.indexOf(name);
						if (paramIndex != -1) {
							parameterNames.set(paramIndex, null);
						}
						parameters.add(tag);
					} else if (size > 2 && first instanceof TextElement firstTextElement) {
						String firstText = firstTextElement.getText();
						if ("<".equals(firstText)) { //$NON-NLS-1$
							Object second = fragments.get(1);
							Object third = fragments.get(2);
							if (second instanceof SimpleName secondSimpleName && third instanceof TextElement thirdTextElement) {
								String name = secondSimpleName.getIdentifier();
								String thirdText = thirdTextElement.getText();
								if (">".equals(thirdText)) { //$NON-NLS-1$
									int paramIndex = typeParameterNames.indexOf(name);
									if (paramIndex != -1) {
										typeParameterNames.set(paramIndex, null);
									}
									typeParameters.add(tag);
								}
							}
						}
					}
				}

			} else if (TagElement.TAG_RETURN.equals(tagName)) {
				if (returnTag == null) {
					returnTag = tag; // the Javadoc tool only shows the first return tag
				}

			} else if (TagElement.TAG_EXCEPTION.equals(tagName) || TagElement.TAG_THROWS.equals(tagName)) {
				exceptions.add(tag);
				List<? extends ASTNode> fragments = tag.fragments();
				if (fragments.size() > 0) {
					Object first = fragments.get(0);
					if (first instanceof Name firstName) {
						String name = ASTNodes.getSimpleNameIdentifier(firstName);
						int exceptionIndex = exceptionNames.indexOf(name);
						if (exceptionIndex != -1) {
							exceptionNames.set(exceptionIndex, null);
						}
					}
				}

			} else if (TagElement.TAG_SINCE.equals(tagName)) {
				since.add(tag);
			} else if (TagElement.TAG_VERSION.equals(tagName)) {
				versions.add(tag);
			} else if (TagElement.TAG_AUTHOR.equals(tagName)) {
				authors.add(tag);
			} else if (TagElement.TAG_SEE.equals(tagName)) {
				sees.add(tag);
			} else if (TagElement.TAG_DEPRECATED.equals(tagName)) {
				if (deprecatedTag == null) {
					deprecatedTag = tag; // the Javadoc tool only shows the first deprecated tag
				}
			} else if (TagElement.TAG_API_NOTE.equals(tagName)) {
				apinote.add(tag);
			} else {
				rest.add(tag);
			}
		}

		//TODO: @Documented annotations before header
		if (deprecatedTag != null) {
			handleDeprecatedTag(deprecatedTag);
		}
		if (start != null) {
			handleContentElements(start.fragments());
		} else if (fMethod != null) {
			CharSequence inherited = fJavadocLookup.getInheritedMainDescription(fMethod);
			// The Javadoc tool adds "Description copied from class: ..." (only for the main description).
			// We don't bother doing that.
			handleInherited(inherited);
		}

		CharSequence[] typeParameterDescriptions = new CharSequence[typeParameterNames.size()];
		boolean hasInheritedTypeParameters = inheritTypeParameterDescriptions(typeParameterNames, typeParameterDescriptions);
		boolean hasTypeParameters = typeParameters.size() > 0 || hasInheritedTypeParameters;

		CharSequence[] parameterDescriptions = new CharSequence[parameterNames.size()];
		boolean hasInheritedParameters = inheritParameterDescriptions(parameterNames, parameterDescriptions);
		boolean hasParameters = parameters.size() > 0 || hasInheritedParameters;

		CharSequence returnDescription = null;
		if (returnTag == null && needsReturnTag()) {
			returnDescription = fJavadocLookup.getInheritedReturnDescription(fMethod);
		}
		boolean hasReturnTag = returnTag != null || returnDescription != null;

		CharSequence[] exceptionDescriptions = new CharSequence[exceptionNames.size()];
		boolean hasInheritedExceptions = inheritExceptionDescriptions(exceptionNames, exceptionDescriptions);
		boolean hasExceptions = exceptions.size() > 0 || hasInheritedExceptions;

		if (hasParameters || hasTypeParameters || hasReturnTag || hasExceptions || versions.size() > 0 || authors.size() > 0 || since.size() > 0 || sees.size() > 0 || rest.size() > 0 || apinote.size() > 0
				|| (fBuf.length() > 0 && (parameterDescriptions.length > 0 || exceptionDescriptions.length > 0))) {
			handleSuperMethodReferences();
			fBuf.append(BLOCK_TAG_START);
			handleParameterTags(typeParameters, typeParameterNames, typeParameterDescriptions, true);
			handleParameterTags(parameters, parameterNames, parameterDescriptions, false);
			handleReturnTag(returnTag, returnDescription);
			handleExceptionTags(exceptions, exceptionNames, exceptionDescriptions);
			handleBlockTags(JavaDoc2HTMLTextReader_since_section, since);
			handleBlockTags(JavaDoc2HTMLTextReader_version_section, versions);
			handleBlockTags(JavaDoc2HTMLTextReader_author_section, authors);
			handleBlockTags(JavaDoc2HTMLTextReader_see_section, sees);
			handleBlockTags(JavaDoc2HTMLTextReader_api_note, apinote);
			handleBlockTags(rest);
			fBuf.append(BLOCK_TAG_END);

		} else if (fBuf.length() > 0) {
			handleSuperMethodReferences();
		}
	}

	private void handleDeprecatedTag(TagElement tag) {
		fBuf.append("<p><b>"); //$NON-NLS-1$
		fBuf.append(JavaDoc2HTMLTextReader_deprecated_section);
		fBuf.append("</b> <i>"); //$NON-NLS-1$
		handleContentElements(tag.fragments());
		fBuf.append("</i><p>"); //$NON-NLS-1$ TODO: Why not </p>? See https://bugs.eclipse.org/bugs/show_bug.cgi?id=243318 .
	}

	private void handleSuperMethodReferences() {
		if (fMethod != null) {
			try {
				StringBuffer superMethodReferences = createSuperMethodReferences(fMethod);
				if (superMethodReferences != null) {
					fBuf.append(superMethodReferences);
				}
			} catch (JavaModelException e) {

			}
		}
	}

	private List<String> initTypeParameterNames() {
		if (fMethod != null) {
			try {
				ArrayList<String> typeParameterNames = new ArrayList<>();
				for (ITypeParameter typeParameter : fMethod.getTypeParameters()) {
					typeParameterNames.add(typeParameter.getElementName());
				}
				return typeParameterNames;
			} catch (JavaModelException e) {

			}
		}
		return Collections.emptyList();
	}

	private List<String> initParameterNames() {
		if (fMethod != null) {
			try {
				return new ArrayList<>(Arrays.asList(fMethod.getParameterNames()));
			} catch (JavaModelException e) {

			}
		}
		return Collections.emptyList();
	}

	private List<String> initExceptionNames() {
		if (fMethod != null) {
			try {
				String[] exceptionTypes = fMethod.getExceptionTypes();
				ArrayList<String> exceptionNames = new ArrayList<>();
				for (int i = 0; i < exceptionTypes.length; i++) {
					exceptionNames.add(Signature.getSimpleName(Signature.toString(exceptionTypes[i])));
				}
				return exceptionNames;
			} catch (JavaModelException e) {

			}
		}
		return Collections.emptyList();
	}

	private boolean needsReturnTag() {
		if (fMethod == null) {
			return false;
		}
		try {
			return !Signature.SIG_VOID.equals(fMethod.getReturnType());
		} catch (JavaModelException e) {
			return false;
		}
	}

	private boolean inheritTypeParameterDescriptions(List<String> typeParameterNames, CharSequence[] typeParameterDescriptions) {
		boolean hasInheritedTypeParameters = false;
		for (int i = 0; i < typeParameterNames.size(); i++) {
			String name = typeParameterNames.get(i);
			if (name != null) {
				typeParameterDescriptions[i] = fJavadocLookup.getInheritedTypeParamDescription(fMethod, i);
				if (typeParameterDescriptions[i] != null) {
					hasInheritedTypeParameters = true;
				}
			}
		}
		return hasInheritedTypeParameters;
	}

	private boolean inheritParameterDescriptions(List<String> parameterNames, CharSequence[] parameterDescriptions) {
		boolean hasInheritedParameters = false;
		for (int i = 0; i < parameterNames.size(); i++) {
			String name = parameterNames.get(i);
			if (name != null) {
				parameterDescriptions[i] = fJavadocLookup.getInheritedParamDescription(fMethod, i);
				if (parameterDescriptions[i] != null) {
					hasInheritedParameters = true;
				}
			}
		}
		return hasInheritedParameters;
	}

	private boolean inheritExceptionDescriptions(List<String> exceptionNames, CharSequence[] exceptionDescriptions) {
		boolean hasInheritedExceptions = false;
		for (int i = 0; i < exceptionNames.size(); i++) {
			String name = exceptionNames.get(i);
			if (name != null) {
				exceptionDescriptions[i] = fJavadocLookup.getInheritedExceptionDescription(fMethod, name);
				if (exceptionDescriptions[i] != null) {
					hasInheritedExceptions = true;
				}
			}
		}
		return hasInheritedExceptions;
	}

	CharSequence getMainDescription() {
		if (fMainDescription == null) {
			fMainDescription = new StringBuffer();
			fBuf = fMainDescription;
			fLiteralContent = 0;

			List<TagElement> tags = fJavadoc.tags();
			for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
				TagElement tag = iter.next();
				String tagName = tag.getTagName();
				if (tagName == null) {
					handleContentElements(tag.fragments());
					break;
				}
			}

			fBuf = null;
		}
		return fMainDescription.length() > 0 ? fMainDescription : null;
	}

	CharSequence getReturnDescription() {
		if (fReturnDescription == null) {
			fReturnDescription = new StringBuffer();
			fBuf = fReturnDescription;
			fLiteralContent = 0;

			List<TagElement> tags = fJavadoc.tags();
			for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
				TagElement tag = iter.next();
				String tagName = tag.getTagName();
				if (TagElement.TAG_RETURN.equals(tagName)) {
					handleContentElements(tag.fragments());
					break;
				}
			}

			fBuf = null;
		}
		return fReturnDescription.length() > 0 ? fReturnDescription : null;
	}

	CharSequence getInheritedTypeParamDescription(int typeParamIndex) {
		if (fMethod != null) {
			List<String> typeParameterNames = initTypeParameterNames();
			if (fTypeParamDescriptions == null) {
				fTypeParamDescriptions = new StringBuffer[typeParameterNames.size()];
			} else {
				StringBuffer description = fTypeParamDescriptions[typeParamIndex];
				if (description != null) {
					return description.length() > 0 ? description : null;
				}
			}

			StringBuffer description = new StringBuffer();
			fTypeParamDescriptions[typeParamIndex] = description;
			fBuf = description;
			fLiteralContent = 0;

			String typeParamName = typeParameterNames.get(typeParamIndex);
			List<TagElement> tags = fJavadoc.tags();
			for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
				TagElement tag = iter.next();
				String tagName = tag.getTagName();
				if (TagElement.TAG_PARAM.equals(tagName)) {
					List<? extends ASTNode> fragments = tag.fragments();
					if (fragments.size() > 2) {
						Object first = fragments.get(0);
						Object second = fragments.get(1);
						Object third = fragments.get(2);
						if (first instanceof TextElement firstTextElement && second instanceof SimpleName secondSimpleName && third instanceof TextElement thirdTextElement) {
							String firstText = firstTextElement.getText();
							String thirdText = thirdTextElement.getText();
							if ("<".equals(firstText) && ">".equals(thirdText)) { //$NON-NLS-1$ //$NON-NLS-2$
								String name = secondSimpleName.getIdentifier();
								if (name.equals(typeParamName)) {
									handleContentElements(fragments.subList(3, fragments.size()));
									break;
								}
							}
						}
					}
				}
			}

			fBuf = null;
			return description.length() > 0 ? description : null;
		}
		return null;
	}

	CharSequence getInheritedParamDescription(int paramIndex) throws JavaModelException {
		if (fMethod != null) {
			String[] parameterNames = fMethod.getParameterNames();
			if (fParamDescriptions == null) {
				fParamDescriptions = new StringBuffer[parameterNames.length];
			} else {
				StringBuffer description = fParamDescriptions[paramIndex];
				if (description != null) {
					return description.length() > 0 ? description : null;
				}
			}

			StringBuffer description = new StringBuffer();
			fParamDescriptions[paramIndex] = description;
			fBuf = description;
			fLiteralContent = 0;

			String paramName = parameterNames[paramIndex];
			List<TagElement> tags = fJavadoc.tags();
			for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
				TagElement tag = iter.next();
				String tagName = tag.getTagName();
				if (TagElement.TAG_PARAM.equals(tagName)) {
					List<? extends ASTNode> fragments = tag.fragments();
					if (fragments.size() > 0) {
						Object first = fragments.get(0);
						if (first instanceof SimpleName simpleName) {
							String name = simpleName.getIdentifier();
							if (name.equals(paramName)) {
								handleContentElements(fragments.subList(1, fragments.size()));
								break;
							}
						}
					}
				}
			}

			fBuf = null;
			return description.length() > 0 ? description : null;
		}
		return null;
	}

	CharSequence getExceptionDescription(String simpleName) {
		if (fMethod != null) {
			if (fExceptionDescriptions == null) {
				fExceptionDescriptions = new HashMap<>();
			} else {
				StringBuffer description = fExceptionDescriptions.get(simpleName);
				if (description != null) {
					return description.length() > 0 ? description : null;
				}
			}

			StringBuffer description = new StringBuffer();
			fExceptionDescriptions.put(simpleName, description);
			fBuf = description;
			fLiteralContent = 0;

			List<TagElement> tags = fJavadoc.tags();
			for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
				TagElement tag = iter.next();
				String tagName = tag.getTagName();
				if (TagElement.TAG_THROWS.equals(tagName) || TagElement.TAG_EXCEPTION.equals(tagName)) {
					List<? extends ASTNode> fragments = tag.fragments();
					if (fragments.size() > 0) {
						Object first = fragments.get(0);
						if (first instanceof Name firstName) {
							String name = ASTNodes.getSimpleNameIdentifier(firstName);
							if (name.equals(simpleName)) {
								if (fragments.size() > 1) {
									handleContentElements(fragments.subList(1, fragments.size()));
								}
								break;
							}
						}
					}
				}
			}

			fBuf = null;
			return description.length() > 0 ? description : null;
		}
		return null;
	}

	private void handleContentElements(List<? extends ASTNode> nodes) {
		handleContentElements(nodes, false);
	}

	private void handleContentElements(List<? extends ASTNode> nodes, boolean skipLeadingWhitespace) {
		ASTNode previousNode = null;
		for (Iterator<? extends ASTNode> iter = nodes.iterator(); iter.hasNext();) {
			ASTNode child = iter.next();
			boolean isInSnippet = previousNode instanceof TagElement previousTag && TagElement.TAG_SNIPPET.equals(previousTag.getTagName());
			if (previousNode != null) {
				int previousEnd = previousNode.getStartPosition() + previousNode.getLength();
				int childStart = child.getStartPosition();
				if (previousEnd > childStart) {
					// should never happen, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=304826
					Exception exception = new Exception("Illegal ASTNode positions: previousEnd=" + previousEnd //$NON-NLS-1$
							+ ", childStart=" + childStart //$NON-NLS-1$
							+ ", element=" + fElement.getHandleIdentifier() //$NON-NLS-1$
							+ ", Javadoc:\n" + fSource); //$NON-NLS-1$
				} else if (previousEnd != childStart) {
					// Need to preserve whitespace before a node that's not
					// directly following the previous node (e.g. on a new line)
					// due to https://bugs.eclipse.org/bugs/show_bug.cgi?id=206518 :
					String textWithStars = fSource.substring(previousEnd, childStart);
					String text = removeDocLineIntros(textWithStars);
					fBuf.append(text);
				}
			}

			previousNode = child;
			if (child instanceof TextElement textElement) {
				String text = textElement.getText();
				if (JavaDocHTMLPathHandler.containsHTMLTag(text)) {
					text = JavaDocHTMLPathHandler.getValidatedHTMLSrcAttribute(textElement, fElement);
				}

				if (skipLeadingWhitespace) {
					text = text.stripLeading();
				}
				// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=233481 :
				text = text.replaceAll("(\r\n?|\n)([ \t]*\\*)", "$1"); //$NON-NLS-1$ //$NON-NLS-2$
				handleText(markSnippet(text, isInSnippet));
			} else if (child instanceof TagElement tagElement) {
				handleInlineTagElement(tagElement);
			} else {
				// This is unexpected. Fail gracefully by just copying the source.
				int start = child.getStartPosition();
				String text = fSource.substring(start, start + child.getLength());
				fBuf.append(removeDocLineIntros(text));
			}
		}
	}

	private String markSnippet(String text, boolean isInSnippet) {
		if (isInSnippet) {
			StringBuilder builder = new StringBuilder();
			text.lines().forEach((l) -> {
				builder.append(JavaDocSnippetStringEvaluator.SNIPPET);
				builder.append(l);
				builder.append("\n");
			});
			return builder.toString();
		}
		return text;
	}

	private String removeDocLineIntros(String textWithStars) {
		String lineBreakGroup = "(\\r\\n?|\\n)"; //$NON-NLS-1$
		String noBreakSpace = "[^\r\n&&\\s]"; //$NON-NLS-1$
		return textWithStars.replaceAll(lineBreakGroup + noBreakSpace + "*\\*" /*+ noBreakSpace + '?'*/, "$1"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void handleText(String text) {
		if (fLiteralContent == 0) {
			fBuf.append(text);
		} else {
			appendEscaped(fBuf, text);
		}
	}

	private static void appendEscaped(StringBuffer buf, String text) {
		int nextToCopy = 0;
		int length = text.length();
		for (int i = 0; i < length; i++) {
			char ch = text.charAt(i);
			String rep = null;
			switch (ch) {
				case '&':
					rep = "&amp;"; //$NON-NLS-1$
					break;
				case '"':
					rep = "&quot;"; //$NON-NLS-1$
					break;
				case '<':
					rep = "&lt;"; //$NON-NLS-1$
					break;
				case '>':
					rep = "&gt;"; //$NON-NLS-1$
					break;
			}
			if (rep != null) {
				if (nextToCopy < i) {
					buf.append(text.substring(nextToCopy, i));
				}
				buf.append(rep);
				nextToCopy = i + 1;
			}
		}
		if (nextToCopy < length) {
			buf.append(text.substring(nextToCopy));
		}
	}

	@SuppressWarnings("unchecked")
	private void handleInlineTagElement(TagElement node) {
		String name = node.getTagName();

		if (TagElement.TAG_VALUE.equals(name) && handleValueTag(node)) {
			return;
		}

		boolean isLink = TagElement.TAG_LINK.equals(name);
		boolean isLinkplain = TagElement.TAG_LINKPLAIN.equals(name);
		boolean isCode = TagElement.TAG_CODE.equals(name);
		boolean isLiteral = TagElement.TAG_LITERAL.equals(name);
		boolean isSummary = TagElement.TAG_SUMMARY.equals(name);
		boolean isIndex = TagElement.TAG_INDEX.equals(name);
		boolean isSnippet = TagElement.TAG_SNIPPET.equals(name);

		if (isLiteral || isCode || isSummary || isIndex) {
			fLiteralContent++;
		}
		if (isCode) {
			fBuf.append("<code>"); //$NON-NLS-1$
		}

		if (isLink || isLinkplain) {
			handleLink(node.fragments());
		} else if (isSummary) {
			handleSummary(node.fragments());
		} else if (isIndex) {
			handleIndex(node.fragments());
		} else if (isCode || isLiteral) {
			handleContentElements(node.fragments(), true);
		} else if (isSnippet) {
			handleSnippet(node);
		}
		else if (handleInheritDoc(node) || handleDocRoot(node)) {
			// Handled
		} else {
			//print uninterpreted source {@tagname ...} for unknown tags
			int start= node.getStartPosition();
			String text= fSource.substring(start, start + node.getLength());
			fBuf.append(removeDocLineIntros(text));
		}

		if (isCode) {
			fBuf.append("</code>"); //$NON-NLS-1$
		}
		if (isSnippet)
		 {
			fBuf.append("</code></pre>"); //$NON-NLS-1$
		}
		if (isLiteral || isCode) {
			fLiteralContent--;
		}

	}

	private boolean handleValueTag(TagElement node) {

		List<? extends ASTNode> fragments = node.fragments();
		try {
			if (!(fElement instanceof IMember)) {
				return false;
			}
			if (fragments.isEmpty()) {
				if (fElement instanceof IField field && JdtFlags.isStatic((IField) fElement) && JdtFlags.isFinal((IField) fElement)) {
					return handleConstantValue(field, false);
				}
			} else if (fragments.size() == 1) {
				Object first = fragments.get(0);
				if (first instanceof MemberRef memberRef) {
					if (memberRef.getQualifier() == null) {
						SimpleName name = memberRef.getName();
						IType type = fElement instanceof IType typeElement ? typeElement : ((IMember) fElement).getDeclaringType();
						while (type != null) {
							IField field = type.getField(name.getIdentifier());
							if (field != null && field.exists()) {
								if (JdtFlags.isStatic(field) && JdtFlags.isFinal(field)) {
									return handleConstantValue(field, true);
								}
								break;
							}
							type = type.getDeclaringType();
						}
					}
				}
			}
		} catch (JavaModelException e) {

		}

		return false;
	}

	private boolean handleConstantValue(IField field, boolean link) throws JavaModelException {
		String text = null;

		ISourceRange nameRange = field.getNameRange();
		if (SourceRange.isAvailable(nameRange)) {
			CompilationUnit cuNode = CoreASTProvider.getInstance().getAST(field.getTypeRoot(), CoreASTProvider.WAIT_YES, new NullProgressMonitor());
			if (cuNode != null) {
				ASTNode nameNode = NodeFinder.perform(cuNode, nameRange);
				if (nameNode instanceof SimpleName simpleName) {
					IBinding binding = simpleName.resolveBinding();
					if (binding instanceof IVariableBinding variableBinding) {
						Object constantValue = variableBinding.getConstantValue();
						if (constantValue != null) {
							if (constantValue instanceof String s) {
								text = ASTNodes.getEscapedStringLiteral(s);
							} else {
								text = constantValue.toString(); // Javadoc tool is even worse for chars...
							}
						}
					}
				}
			}
		}

		if (text == null) {
			Object constant = field.getConstant();
			if (constant != null) {
				text = constant.toString();
			}
		}

		if (text != null) {
			text = convertToHTMLContentWithWhitespace(text);
			if (link) {
				String uri;
				try {
					uri = JavaElementLinks.createURI("eclipse-javadoc", field);
					fBuf.append(JavaElementLinks.createLink(uri, text));
				} catch (URISyntaxException e) {
					return false;
				}
			} else {
				handleText(text);
			}
			return true;
		}
		return false;
	}

	private boolean handleDocRoot(TagElement node) {
		if (!TagElement.TAG_DOCROOT.equals(node.getTagName())) {
			return false;
		}

		try {
			String url = null;
			if (fElement instanceof IMember member && member.isBinary()) {
				URL javadocBaseLocation = JavaDocLocations.getJavadocBaseLocation(fElement);
				if (javadocBaseLocation != null) {
					url = javadocBaseLocation.toExternalForm();
				}
			} else {
				IPackageFragmentRoot srcRoot = JavaModelUtil.getPackageFragmentRoot(fElement);
				if (srcRoot != null) {
					IResource resource = srcRoot.getResource();
					if (resource != null) {
						/*
						 * Too bad: Browser widget knows nothing about EFS and custom URL handlers,
						 * so IResource#getLocationURI() does not work in all cases.
						 * We only support the local file system for now.
						 * A solution could be https://bugs.eclipse.org/bugs/show_bug.cgi?id=149022 .
						 */
						IPath location = resource.getLocation();
						if (location != null) {
							url = location.toFile().toURI().toASCIIString();
						}
					}

				}
			}
			if (url != null) {
				if (url.endsWith("/")) { //$NON-NLS-1$
					url = url.substring(0, url.length() - 1);
				}
				fBuf.append(url);
				return true;
			}
		} catch (JavaModelException e) {
		}
		return false;
	}

	/**
	 * Handle {&#64;inheritDoc}.
	 *
	 * @param node
	 *            the node
	 * @return <code>true</code> iff the node was an {&#64;inheritDoc} node and has
	 *         been handled
	 */
	private boolean handleInheritDoc(TagElement node) {
		if (!TagElement.TAG_INHERITDOC.equals(node.getTagName())) {
			return false;
		}
		try {
			if (fMethod == null) {
				return false;
			}

			TagElement blockTag = (TagElement) node.getParent();
			String blockTagName = blockTag.getTagName();

			if (blockTagName == null) {
				CharSequence inherited = fJavadocLookup.getInheritedMainDescription(fMethod);
				return handleInherited(inherited);

			} else if (TagElement.TAG_PARAM.equals(blockTagName)) {
				List<? extends ASTNode> fragments = blockTag.fragments();
				int size = fragments.size();
				if (size > 0) {
					Object first = fragments.get(0);
					if (first instanceof SimpleName simpleName) {
						String name = simpleName.getIdentifier();
						String[] parameterNames = fMethod.getParameterNames();
						for (int i = 0; i < parameterNames.length; i++) {
							if (name.equals(parameterNames[i])) {
								CharSequence inherited = fJavadocLookup.getInheritedParamDescription(fMethod, i);
								return handleInherited(inherited);
							}
						}
					} else if (size > 2 && first instanceof TextElement firstTextElement) {
						String firstText = firstTextElement.getText();
						if ("<".equals(firstText)) { //$NON-NLS-1$
							Object second = fragments.get(1);
							Object third = fragments.get(2);
							if (second instanceof SimpleName secondSimpleName && third instanceof TextElement thirdTextElement) {
								String thirdText = thirdTextElement.getText();
								if (">".equals(thirdText)) { //$NON-NLS-1$
									String name = secondSimpleName.getIdentifier();
									ITypeParameter[] typeParameters = fMethod.getTypeParameters();
									for (int i = 0; i < typeParameters.length; i++) {
										ITypeParameter typeParameter = typeParameters[i];
										if (name.equals(typeParameter.getElementName())) {
											CharSequence inherited = fJavadocLookup.getInheritedTypeParamDescription(fMethod, i);
											return handleInherited(inherited);
										}
									}
								}
							}
						}
					}
				}

			} else if (TagElement.TAG_RETURN.equals(blockTagName)) {
				CharSequence inherited = fJavadocLookup.getInheritedReturnDescription(fMethod);
				return handleInherited(inherited);

			} else if (TagElement.TAG_THROWS.equals(blockTagName) || TagElement.TAG_EXCEPTION.equals(blockTagName)) {
				List<? extends ASTNode> fragments = blockTag.fragments();
				if (fragments.size() > 0) {
					Object first = fragments.get(0);
					if (first instanceof Name firstName) {
						String name = ASTNodes.getSimpleNameIdentifier(firstName);
						CharSequence inherited = fJavadocLookup.getInheritedExceptionDescription(fMethod, name);
						return handleInherited(inherited);
					}
				}
			}
		} catch (JavaModelException e) {

		}
		return false;
	}

	private boolean handleInherited(CharSequence inherited) {
		if (inherited == null) {
			return false;
		}

		fBuf.append(inherited);
		return true;
	}

	private void handleBlockTags(String title, List<TagElement> tags) {
		if (tags.isEmpty()) {
			return;
		}

		handleBlockTagTitle(title);

		for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
			TagElement tag = iter.next();
			fBuf.append(BLOCK_TAG_START);
			fBuf.append(BlOCK_TAG_ENTRY_START);
			if (TagElement.TAG_SEE.equals(tag.getTagName())) {
				handleSeeTag(tag);
			} else {
				handleContentElements(tag.fragments());
			}
			fBuf.append(BlOCK_TAG_ENTRY_END);
			fBuf.append(BLOCK_TAG_END);
		}
		fBuf.append(BlOCK_TAG_ENTRY_END);
	}

	private void handleReturnTag(TagElement tag, CharSequence returnDescription) {
		if (tag == null && returnDescription == null) {
			return;
		}

		handleBlockTagTitle(JavaDoc2HTMLTextReader_returns_section);

		fBuf.append(BLOCK_TAG_START);
		fBuf.append(BlOCK_TAG_ENTRY_START);
		if (tag != null) {
			handleContentElements(tag.fragments());
		} else {
			fBuf.append(returnDescription);
		}
		fBuf.append(BlOCK_TAG_ENTRY_END);
		fBuf.append(BLOCK_TAG_END);
		fBuf.append(BlOCK_TAG_ENTRY_END);
	}

	private void handleBlockTags(List<TagElement> tags) {
		for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
			TagElement tag = iter.next();
			handleBlockTagTitle(tag.getTagName());
			List fragments = tag.fragments();
			if (!fragments.isEmpty()) {
				fBuf.append(BLOCK_TAG_START);
				fBuf.append(BlOCK_TAG_ENTRY_START);
				handleContentElements(fragments);
				fBuf.append(BlOCK_TAG_ENTRY_END);
				fBuf.append(BLOCK_TAG_END);
			}
			fBuf.append(BlOCK_TAG_ENTRY_END);
		}
	}

	private void handleBlockTagTitle(String title) {
		fBuf.append("<li><b>"); //$NON-NLS-1$
		fBuf.append(title);
		fBuf.append("</b>"); //$NON-NLS-1$
	}

	private void handleSeeTag(TagElement tag) {
		handleLink(tag.fragments());
	}

	private void handleExceptionTags(List<TagElement> tags, List<String> exceptionNames, CharSequence[] exceptionDescriptions) {
		if (tags.size() == 0 && containsOnlyNull(exceptionNames)) {
			return;
		}

		handleBlockTagTitle(JavaDoc2HTMLTextReader_throws_section);
		fBuf.append(BLOCK_TAG_START);
		for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
			TagElement tag = iter.next();
			fBuf.append(BlOCK_TAG_ENTRY_START);
			handleThrowsTag(tag);
			fBuf.append(BlOCK_TAG_ENTRY_END);
		}

		for (int i = 0; i < exceptionDescriptions.length; i++) {
			CharSequence description = exceptionDescriptions[i];
			String name = exceptionNames.get(i);
			if (name != null) {
				fBuf.append(BlOCK_TAG_ENTRY_START);
				handleLink(Collections.singletonList(fJavadoc.getAST().newSimpleName(name)));
				if (description != null) {
					fBuf.append(JavaElementLabels.CONCAT_STRING);
					fBuf.append(description);
				}
				fBuf.append(BlOCK_TAG_ENTRY_END);
			}
		}
		fBuf.append(BLOCK_TAG_END);
		fBuf.append(BlOCK_TAG_ENTRY_END);
	}

	private void handleThrowsTag(TagElement tag) {
		List<? extends ASTNode> fragments = tag.fragments();
		int size = fragments.size();
		if (size > 0) {
			handleLink(fragments.subList(0, 1));
			if (size > 1) {
				fBuf.append(JavaElementLabels.CONCAT_STRING);
				handleContentElements(fragments.subList(1, size));
			}
		}
	}

	private void handleParameterTags(List<TagElement> tags, List<String> parameterNames, CharSequence[] parameterDescriptions, boolean isTypeParameters) {
		if (tags.size() == 0 && containsOnlyNull(parameterNames)) {
			return;
		}

		String tagTitle = isTypeParameters ? JavaDoc2HTMLTextReader_type_parameters_section : JavaDoc2HTMLTextReader_parameters_section;
		handleBlockTagTitle(tagTitle);

		for (Iterator<TagElement> iter = tags.iterator(); iter.hasNext();) {
			TagElement tag = iter.next();
			fBuf.append(BLOCK_TAG_START);
			fBuf.append(BlOCK_TAG_ENTRY_START);
			handleParamTag(tag);
			fBuf.append(BlOCK_TAG_ENTRY_END);
			fBuf.append(BLOCK_TAG_END);
		}
		for (int i = 0; i < parameterDescriptions.length; i++) {
			CharSequence description = parameterDescriptions[i];
			String name = parameterNames.get(i);
			if (name != null) {
				fBuf.append(BLOCK_TAG_START);
				fBuf.append(BlOCK_TAG_ENTRY_START);
				fBuf.append(PARAM_NAME_START);
				if (isTypeParameters) {
					fBuf.append("&lt;"); //$NON-NLS-1$
				}
				fBuf.append(name);
				if (isTypeParameters) {
					fBuf.append("&gt;"); //$NON-NLS-1$
				}
				fBuf.append(PARAM_NAME_END);
				if (description != null) {
					fBuf.append(description);
				}
				fBuf.append(BlOCK_TAG_ENTRY_END);
				fBuf.append(BLOCK_TAG_END);
			}
		}

		fBuf.append(BlOCK_TAG_ENTRY_END);
	}

	private void handleParamTag(TagElement tag) {
		List<? extends ASTNode> fragments = tag.fragments();
		int i = 0;
		int size = fragments.size();
		if (size > 0) {
			Object first = fragments.get(0);
			fBuf.append(PARAM_NAME_START);
			if (first instanceof SimpleName simpleName) {
				String name = simpleName.getIdentifier();
				fBuf.append(name);
				i++;
			} else if (first instanceof TextElement textElement) {
				String firstText = textElement.getText();
				if ("<".equals(firstText)) { //$NON-NLS-1$
					fBuf.append("&lt;"); //$NON-NLS-1$
					i++;
					if (size > 1) {
						Object second = fragments.get(1);
						if (second instanceof SimpleName secondSimpleName) {
							String name = secondSimpleName.getIdentifier();
							fBuf.append(name);
							i++;
							if (size > 2) {
								Object third = fragments.get(2);
								String thirdText = ((TextElement) third).getText();
								if (">".equals(thirdText)) { //$NON-NLS-1$
									fBuf.append("&gt;"); //$NON-NLS-1$
									i++;
								}
							}
						}
					}
				}
			}
			fBuf.append(PARAM_NAME_END);

			handleContentElements(fragments.subList(i, fragments.size()));
		}
	}

	private void handleSummary(List<? extends ASTNode> fragments) {
		int fs= fragments.size();
		if (fs > 0) {
			Object first= fragments.get(0);
			if (first instanceof TextElement memberRef) {
				fBuf.append(BlOCK_TAG_TITLE_START + "Summary: " + memberRef.getText() + BlOCK_TAG_TITLE_END); //$NON-NLS-1$
				return;
			}
		}
	}

	private void handleSnippet(TagElement node) {
		if (node != null) {
			Object val = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_IS_VALID);
			Object valError = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_ERROR);
			if (val instanceof Boolean bool && bool.booleanValue() && valError == null) {
				int fs= node.fragments().size();
				if (fs > 0) {
					fBuf.append("<pre>"); //$NON-NLS-1$
					Object valID = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_ID);
					if (valID instanceof String && !valID.toString().isBlank()) {
						fBuf.append("<code id=" +valID.toString()+">" ); //$NON-NLS-1$ //$NON-NLS-2$
					} else {
						fBuf.append("<code>") ;//$NON-NLS-1$
					}
					fBuf.append(BlOCK_TAG_ENTRY_START);
					fSnippetStringEvaluator.AddTagElementString(node, fBuf);
					fBuf.append(BlOCK_TAG_ENTRY_END);
				}
			} else {
				handleInvalidSnippet(node);
			}
		}
	}

	private void handleInvalidSnippet(TagElement node) {
		fBuf.append("<pre><code>\n"); //$NON-NLS-1$
		fBuf.append("<mark>invalid @Snippet</mark>"); //$NON-NLS-1$
		Object val = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_ERROR);
		if (val instanceof String) {
			fBuf.append("<br><p>"+val+"</p>"); //$NON-NLS-1$ //$NON-NLS-2$

		}


	}

	private void handleIndex(List<? extends ASTNode> fragments) {
		int fs= fragments.size();
		if (fs > 0) {
			Object first= fragments.get(0);
			if (first instanceof TextElement memberRef) {
				fBuf.append(  memberRef.getText() );
				return;
			}
		}
	}

	private void handleLink(List<? extends ASTNode> fragments) {
		//TODO: Javadoc shortens type names to minimal length according to context
		int fs = fragments.size();
		if (fs > 0) {
			Object first = fragments.get(0);
			String refTypeName = null;
			String refMemberName = null;
			String[] refMethodParamTypes = null;
			String[] refMethodParamNames = null;
			int startPosition = -1;
			if (first instanceof Name name) {
				refTypeName = name.getFullyQualifiedName();
				startPosition = name.getStartPosition();
			} else if (first instanceof MemberRef memberRef) {
				Name qualifier = memberRef.getQualifier();
				refTypeName = qualifier == null ? "" : qualifier.getFullyQualifiedName(); //$NON-NLS-1$
				refMemberName = memberRef.getName().getIdentifier();
				startPosition = memberRef.getStartPosition();
			} else if (first instanceof MethodRef methodRef) {
				Name qualifier = methodRef.getQualifier();
				refTypeName = qualifier == null ? "" : qualifier.getFullyQualifiedName(); //$NON-NLS-1$
				refMemberName = methodRef.getName().getIdentifier();
				List<MethodRefParameter> params = methodRef.parameters();
				int ps = params.size();
				refMethodParamTypes = new String[ps];
				refMethodParamNames = new String[ps];
				for (int i = 0; i < ps; i++) {
					MethodRefParameter param = params.get(i);
					refMethodParamTypes[i] = ASTNodes.asString(param.getType());
					SimpleName paramName = param.getName();
					if (paramName != null) {
						refMethodParamNames[i] = paramName.getIdentifier();
					}
				}
				startPosition = methodRef.getStartPosition();
			}

			if (refTypeName != null) {
				fBuf.append("<a href=\""); //$NON-NLS-1$
				try {
					String uri = JavaElementLinks.createURI("file", fElement, refTypeName, refMemberName, refMethodParamTypes, startPosition);
					fBuf.append(uri);
				} catch (URISyntaxException e) {
					//JavaPlugin.log(e);
				}
				fBuf.append("\">"); //$NON-NLS-1$
				if (fs > 1 && !(fs == 2 && isWhitespaceTextElement(fragments.get(1)))) {
					handleContentElements(fragments.subList(1, fs), true);
				} else {
					fBuf.append(refTypeName);
					if (refMemberName != null) {
						if (refTypeName.length() > 0) {
							fBuf.append('.');
						}
						fBuf.append(refMemberName);
						if (refMethodParamTypes != null) {
							fBuf.append('(');
							for (int i = 0; i < refMethodParamTypes.length; i++) {
								String pType = refMethodParamTypes[i];
								fBuf.append(pType);
								String pName = refMethodParamNames[i];
								if (pName != null) {
									fBuf.append(' ').append(pName);
								}
								if (i < refMethodParamTypes.length - 1) {
									fBuf.append(", "); //$NON-NLS-1$
								}
							}
							fBuf.append(')');
						}
					}
				}
				fBuf.append("</a>"); //$NON-NLS-1$
			} else {
				handleContentElements(fragments);
			}
		}
	}

	private static boolean isWhitespaceTextElement(Object fragment) {
		return fragment instanceof TextElement textElement && textElement.getText().trim().length() == 0;
	}

	private boolean containsOnlyNull(List<String> parameterNames) {
		for (Iterator<String> iter = parameterNames.iterator(); iter.hasNext();) {
			if (iter.next() != null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param content
	 *            HTML content produced by <code>getHTMLContent(...)</code>
	 * @return the baseURL to use for the given content, or <code>null</code> if
	 *         none
	 * @since 3.10
	 */
	public static String extractBaseURL(String content) {
		int introStart = content.indexOf(BASE_URL_COMMENT_INTRO);
		if (introStart != -1) {
			int introLength = BASE_URL_COMMENT_INTRO.length();
			int endIndex = content.indexOf('"', introStart + introLength);
			if (endIndex != -1) {
				return content.substring(introStart + introLength, endIndex);
			}
		}
		return null;
	}

	/**
	 * Returns the Javadoc for a PackageDeclaration.
	 *
	 * @param packageDeclaration
	 *            the Java element whose Javadoc has to be retrieved
	 * @return the package documentation in HTML format or <code>null</code> if
	 *         there is no associated Javadoc
	 * @throws CoreException
	 *             if the Java element does not exists or an exception occurs while
	 *             accessing the file containing the package Javadoc
	 * @since 3.9
	 */
	public static String getHTMLContent(IPackageDeclaration packageDeclaration) throws CoreException {
		return packageDeclaration.getAncestor(IJavaElement.PACKAGE_FRAGMENT) instanceof IPackageFragment pkg ? getHTMLContent(pkg) : null;
	}

	/**
	 * Returns the Javadoc for a package which could be present in package.html,
	 * package-info.java or from an attached Javadoc.
	 *
	 * @param packageFragment
	 *            the package which is requesting for the document
	 * @return the document content in HTML format or <code>null</code> if there is
	 *         no associated Javadoc
	 * @throws CoreException
	 *             if the Java element does not exists or an exception occurs while
	 *             accessing the file containing the package Javadoc
	 * @since 3.9
	 */
	public static String getHTMLContent(IPackageFragment packageFragment) throws CoreException {
		String content = readHTMLContent(packageFragment);
		return sanitizePackageJavadoc(content);
	}

	private static String sanitizePackageJavadoc(String content) {
		if (content == null || content.isEmpty()) {
			return content;
		}
		//Since Java 9, Javadoc format has changed and the JDT parsing in org.eclipse.jdt.internal.core.JavadocContent hasn't really caught up
		//so we need to manually remove the list of classes away from the actual package Javadoc, similar to Java < 9.
		//This is a simple, suboptimal but temporary (AHAHAH!) hack until JavadocContent fixes its parsing.
		if (content.indexOf(CONTENT_CONTAINER) == 0) {
			int nextListIndex = content.indexOf(UL_CLASS_BLOCK_LIST);
			if (nextListIndex > 0) {
				content = content.substring(CONTENT_CONTAINER.length(), nextListIndex);
			}
		}
		return content;
	}

	private static String readHTMLContent(IPackageFragment packageFragment) throws CoreException {
		IPackageFragmentRoot root = (IPackageFragmentRoot) packageFragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);

		//1==> Handle the case when the documentation is present in package-info.java or package-info.class file
		ITypeRoot packageInfo;
		boolean isBinary = root.getKind() == IPackageFragmentRoot.K_BINARY;
		if (isBinary) {
			packageInfo = packageFragment.getClassFile(JavaModelUtil.PACKAGE_INFO_CLASS);
		} else {
			packageInfo = packageFragment.getCompilationUnit(JavaModelUtil.PACKAGE_INFO_JAVA);
		}
		if (packageInfo != null && packageInfo.exists()) {
			String cuSource = packageInfo.getSource();
			//the source can be null for some of the class files
			if (cuSource != null) {
				Javadoc packageJavadocNode = getPackageJavadocNode(packageFragment, cuSource);
				if (packageJavadocNode != null) {
					IJavaElement element;
					if (isBinary) {
						element = ((IOrdinaryClassFile) packageInfo).getType();
					} else {
						element = packageInfo.getParent(); // parent is the IPackageFragment
					}
					return new JavadocContentAccess2(element, packageJavadocNode, cuSource).toHTML();
				}
			}
		}

		// 2==> Handle the case when the documentation is done in package.html file. The file can be either in normal source folder or coming from a jar file
		else {
			Object[] nonJavaResources = packageFragment.getNonJavaResources();
			// 2.1 ==>If the package.html file is present in the source or directly in the binary jar
			for (Object nonJavaResource : nonJavaResources) {
				if (nonJavaResource instanceof IFile iFile) {
					if (iFile.exists() && JavaModelUtil.PACKAGE_HTML.equals(iFile.getName())) {
						return getIFileContent(iFile);
					}
				}
			}

			// 2.2==>The file is present in a binary container
			if (isBinary) {
				for (Object nonJavaResource : nonJavaResources) {
					// The content is from an external binary class folder
					if (nonJavaResource instanceof IJarEntryResource jarEntryResource) {
						String encoding = getSourceAttachmentEncoding(root);
						if (JavaModelUtil.PACKAGE_HTML.equals(jarEntryResource.getName()) && jarEntryResource.isFile()) {
							return getHTMLContent(jarEntryResource, encoding);
						}
					}
				}
				//2.3 ==>The file is present in the source attachment path.
				String contents = getHTMLContentFromAttachedSource(root, packageFragment);
				if (contents != null) {
					return contents;
				}
			}
		}

		//3==> Handle the case when the documentation is coming from the attached Javadoc
		if ((root.isArchive() || root.isExternal())) {
			return packageFragment.getAttachedJavadoc(null);

		}

		return null;
	}

	private static String getHTMLContent(IJarEntryResource jarEntryResource, String encoding) throws CoreException {
		InputStream in = jarEntryResource.getContents();
		try {
			return getContentsFromInputStream(in, encoding);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					//ignore
				}
			}
		}
	}

	private static String getHTMLContentFromAttachedSource(IPackageFragmentRoot root, IPackageFragment packageFragment) throws CoreException {
		String filePath = packageFragment.getElementName().replace('.', '/') + '/' + JavaModelUtil.PACKAGE_INFO_JAVA;
		String contents = getFileContentFromAttachedSource(root, filePath);
		if (contents != null) {
			Javadoc packageJavadocNode = getPackageJavadocNode(packageFragment, contents);
			if (packageJavadocNode != null) {
				return new JavadocContentAccess2(packageFragment, packageJavadocNode, contents).toHTML();
			}

		}
		filePath = packageFragment.getElementName().replace('.', '/') + '/' + JavaModelUtil.PACKAGE_HTML;
		return getFileContentFromAttachedSource(root, filePath);
	}

	private static String getFileContentFromAttachedSource(IPackageFragmentRoot root, String filePath) throws CoreException {
		IPath sourceAttachmentPath = root.getSourceAttachmentPath();
		if (sourceAttachmentPath != null) {
			File file = null;
			String encoding = null;

			if (sourceAttachmentPath.getDevice() == null) {
				//the path could be a workspace relative path to a zip or to the source folder
				IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
				IResource res = wsRoot.findMember(sourceAttachmentPath);

				if (res instanceof IFile ifile) {
					// zip in the workspace
					IPath location = res.getLocation();
					if (location == null) {
						return null;
					}
					file = location.toFile();
					encoding = ifile.getCharset(false);

				} else if (res instanceof IContainer container) {
					// folder in the workspace
					res = container.findMember(filePath);
					if (!(res instanceof IFile)) {
						return null;
					}
					encoding = ((IFile) res).getCharset(false);
					if (encoding == null) {
						encoding = getSourceAttachmentEncoding(root);
					}
					return getContentsFromInputStream(((IFile) res).getContents(), encoding);
				}
			}

			if (file == null || !file.exists()) {
				file = sourceAttachmentPath.toFile();
			}

			if (file.isDirectory()) {
				//the path is an absolute filesystem path to the source folder
				IPath packagedocPath = sourceAttachmentPath.append(filePath);
				if (packagedocPath.toFile().exists()) {
					return getFileContent(packagedocPath.toFile());
				}

			} else if (file.exists()) {
				//the package documentation is in a Jar/Zip
				IPath sourceAttachmentRootPath = root.getSourceAttachmentRootPath();
				String packagedocPath;
				//consider the root path also in the search path if it exists
				if (sourceAttachmentRootPath != null) {
					packagedocPath = sourceAttachmentRootPath.append(filePath).toString();
				} else {
					packagedocPath = filePath;
				}
				ZipFile zipFile = null;
				InputStream in = null;
				try {
					zipFile = new ZipFile(file, ZipFile.OPEN_READ);
					ZipEntry packagedocFile = zipFile.getEntry(packagedocPath);
					if (packagedocFile != null) {
						in = zipFile.getInputStream(packagedocFile);
						if (encoding == null) {
							encoding = getSourceAttachmentEncoding(root);
						}
						return getContentsFromInputStream(in, encoding);
					}
				} catch (IOException e) {
					//throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), e.getMessage(), e));
				} finally {
					try {
						if (in != null) {
							in.close();
						}
					} catch (IOException e) {
						//ignore
					}
					try {
						if (zipFile != null) {
							zipFile.close();//this will close the InputStream also
						}
					} catch (IOException e) {
						//ignore
					}
				}
			}
		}

		return null;
	}

	private static String getContentsFromInputStream(InputStream in, String encoding) throws CoreException {
		final int defaultFileSize = 15 * 1024;
		StringBuffer buffer = new StringBuffer(defaultFileSize);
		Reader reader = null;

		try {
			reader = new BufferedReader(new InputStreamReader(in, encoding), defaultFileSize);

			char[] readBuffer = new char[2048];
			int charCount = reader.read(readBuffer);

			while (charCount > 0) {
				buffer.append(readBuffer, 0, charCount);
				charCount = reader.read(readBuffer);
			}

		} catch (IOException e) {
			//throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), e.getMessage(), e));
		} finally {
			try {
				if (reader != null) {
					reader.close();//this will also close the InputStream wrapped in the reader
				}
			} catch (IOException e) {
				//ignore
			}
		}
		return buffer.toString();
	}

	private static String getSourceAttachmentEncoding(IPackageFragmentRoot root) throws JavaModelException {
		String encoding = ResourcesPlugin.getEncoding();
		IClasspathEntry entry = root.getRawClasspathEntry();

		if (entry != null) {
			int kind = entry.getEntryKind();
			if (kind == IClasspathEntry.CPE_LIBRARY || kind == IClasspathEntry.CPE_VARIABLE) {
				IClasspathAttribute[] extraAttributes = entry.getExtraAttributes();
				for (int i = 0; i < extraAttributes.length; i++) {
					IClasspathAttribute attrib = extraAttributes[i];
					if (IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING.equals(attrib.getName())) {
						return attrib.getValue();
					}
				}
			}
		}

		return encoding;
	}

	/**
	 * Reads the content of the IFile.
	 *
	 * @param file
	 *            the file whose content has to be read
	 * @return the content of the file
	 * @throws CoreException
	 *             if the file could not be successfully connected or disconnected
	 */
	private static String getIFileContent(IFile file) throws CoreException {
		String content = null;
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		IPath fullPath = file.getFullPath();
		manager.connect(fullPath, LocationKind.IFILE, null);
		try {
			ITextFileBuffer buffer = manager.getTextFileBuffer(fullPath, LocationKind.IFILE);
			if (buffer != null) {
				content = buffer.getDocument().get();
			}
		} finally {
			manager.disconnect(fullPath, LocationKind.IFILE, null);
		}

		return content;
	}

	/**
	 * Reads the content of the java.io.File.
	 *
	 * @param file
	 *            the file whose content has to be read
	 * @return the content of the file
	 * @throws CoreException
	 *             if the file could not be successfully connected or disconnected
	 */
	private static String getFileContent(File file) throws CoreException {
		String content = null;
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();

		IPath fullPath = new Path(file.getAbsolutePath());
		manager.connect(fullPath, LocationKind.LOCATION, null);
		try {
			ITextFileBuffer buffer = manager.getTextFileBuffer(fullPath, LocationKind.LOCATION);
			if (buffer != null) {
				content = buffer.getDocument().get();
			}
		} finally {
			manager.disconnect(fullPath, LocationKind.LOCATION, null);
		}
		return content;
	}

	/**
	 * Escapes reserved HTML characters in the given string and returns them in a
	 * way that preserves whitespace in a browser.
	 * <p/>
	 * <b>Warning:</b> Whitespace will not be preserved when rendered with an
	 * {@link HTML2TextReader} (e.g. in a {@link DefaultInformationControl} that
	 * renders simple HTML).
	 *
	 * @param content
	 *            the input string
	 * @return the processed string
	 * @see #addPreFormatted(StringBuffer, String)
	 * @see #convertToHTMLContent(String)
	 * @since 3.7
	 */
	public static String convertToHTMLContentWithWhitespace(String content) {
		content = replace(content, '&', "&amp;"); //$NON-NLS-1$
		content = replace(content, '"', "&quot;"); //$NON-NLS-1$
		content = replace(content, '<', "&lt;"); //$NON-NLS-1$
		content = replace(content, '>', "&gt;"); //$NON-NLS-1$
		return "<span style='white-space:pre'>" + content + "</span>"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String replace(String text, char c, String s) {

		int previous = 0;
		int current = text.indexOf(c, previous);

		if (current == -1) {
			return text;
		}

		StringBuffer buffer = new StringBuffer();
		while (current > -1) {
			buffer.append(text.substring(previous, current));
			buffer.append(s);
			previous = current + 1;
			current = text.indexOf(c, previous);
		}
		buffer.append(text.substring(previous));

		return buffer.toString();
	}

	public static Reader getMarkdownContentReader(IJavaElement element) {

		try {
			String rawHtml = JavadocContentAccess2.getHTMLContent(element, true);
			Reader markdownReader = new JavaDoc2MarkdownConverter(rawHtml).getAsReader();
			return markdownReader;
		} catch (IOException | CoreException e) {

		}

		return null;
	}

}