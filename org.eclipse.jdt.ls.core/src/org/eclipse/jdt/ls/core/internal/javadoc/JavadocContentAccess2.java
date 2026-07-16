/*******************************************************************************
 * Copyright (c) 2008, 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Tom Hofmann, Google <eclipse@tom.eicher.name> - [hovering] NPE when hovering over @value reference within a type's javadoc - https://bugs.eclipse.org/bugs/show_bug.cgi?id=320084
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TagProperty;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavaDocSnippetStringEvaluator;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavadocAccess;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavadocAccessImpl;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavadocContentAccessUtility;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreMarkdownAccessImpl;
import org.eclipse.jdt.core.manipulation.internal.javadoc.IJavadocContentFactory;
import org.eclipse.jdt.core.manipulation.internal.javadoc.JavadocLookup;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;
import org.eclipse.jdt.internal.ui.viewsupport.CoreJavaElementLinks;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.Location;

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
	public static final String SNIPPET = "SNIPPET";

	@Deprecated
	public static Reader getPlainTextContentReader(IMember member) throws JavaModelException {
		String content = getPlainTextContent(member);
		return content == null ? null : new StringReader(content);
	}

	@Deprecated
	public static Reader getMarkdownContentReader(IJavaElement member) throws JavaModelException {
		String content = getMarkdownContent(member);
		return content == null ? null : new StringReader(content);
	}

	public static String getPlainTextContent(IMember member) throws JavaModelException {
		Reader contentReader = CoreJavadocContentAccessUtility.getHTMLContentReader(member, true, true);
		if (contentReader != null) {
			try {
				return new JavaDoc2PlainTextConverter(contentReader).getAsString();
			} catch (IOException e) {
				throw new JavaModelException(e, IJavaModelStatusConstants.UNKNOWN_JAVADOC_FORMAT);
			}
		}
		return null;
	}

	public static String getMarkdownContent(IJavaElement element) {

		CoreJavadocAccess access = createJdtLsJavadocAccess();
		try {
			String content = getJavaDocNode(element);
			if (content != null && content.startsWith("///")) { //$NON-NLS-1$
				Javadoc node = CoreJavadocContentAccessUtility.getJavadocNode(element, content);
				if (isPlainMarkdown(element, node)) {
					return renderPlainMarkdown(content, node);
				}
			}
			String rawHtml = access.getHTMLContent(element, true);
			return new JavaDoc2MarkdownConverter(rawHtml).getAsString();
		} catch (IOException | CoreException e) {

		}

		return null;
	}

	private static boolean isPlainMarkdown(IJavaElement element, Javadoc javadoc) {
		try {
			if (javadoc == null || javadoc.tags().size() != 1 || element instanceof ILocalVariable || element instanceof ITypeParameter
					|| element instanceof IField field && field.isRecordComponent()) {
				return false;
			}
			IMember member = getJavadocMember(element);
			if (member == null || hasOverriddenMethod(member)) {
				return false;
			}
		} catch (JavaModelException e) {
			return false;
		}
		Object topLevelTag = javadoc.tags().get(0);
		if (!(topLevelTag instanceof TagElement tag) || tag.getTagName() != null) {
			return false;
		}
		for (Object fragment : tag.fragments()) {
			if (!(fragment instanceof TextElement)) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasOverriddenMethod(IMember member) throws JavaModelException {
		if (!(member instanceof IMethod method) || method.isConstructor()) {
			return false;
		}
		IType declaringType = method.getDeclaringType();
		ITypeHierarchy hierarchy = SuperTypeHierarchyCache.getTypeHierarchy(declaringType);
		MethodOverrideTester overrideTester = SuperTypeHierarchyCache.getMethodOverrideTester(declaringType);
		for (IType superType : hierarchy.getAllSupertypes(declaringType)) {
			if (overrideTester.findOverriddenMethodInType(superType, method) != null) {
				return true;
			}
		}
		return false;
	}

	private static String renderPlainMarkdown(String source, Javadoc javadoc) {
		TagElement tag = (TagElement) javadoc.tags().get(0);
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < tag.fragments().size(); i++) {
			TextElement current = (TextElement) tag.fragments().get(i);
			result.append(current.getText());
			if (i + 1 < tag.fragments().size()) {
				TextElement next = (TextElement) tag.fragments().get(i + 1);
				int currentEnd = current.getStartPosition() + current.getLength();
				int nextStart = next.getStartPosition();
				if (currentEnd == nextStart) {
					result.append(' ');
				} else {
					String gap = source.substring(currentEnd, nextStart);
					result.append(gap.split("///").length > 2 ? "  \n" : "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}
		}
		return result.toString();
	}

	public static String getJavaDocNode(IJavaElement element) throws JavaModelException {
		IMember member = getJavadocMember(element);
		if (member == null) {
			return null;
		}
		IBuffer buffer = member.getOpenable().getBuffer();
		if (buffer == null) {
			return null;
		}
		ISourceRange javadocRange = member.getJavadocRange();
		if (javadocRange == null) {
			return null;
		}
		return buffer.getText(javadocRange.getOffset(), javadocRange.getLength());
	}

	private static IMember getJavadocMember(IJavaElement element) {
		if (element instanceof ILocalVariable localVariable) {
			return localVariable.getDeclaringMember();
		}
		if (element instanceof ITypeParameter typeParameter) {
			return typeParameter.getDeclaringMember();
		}
		return element instanceof IMember member ? member : null;
	}

	/**
	 * @return
	 */
	private static CoreJavadocAccess createJdtLsJavadocAccess() {
		return new CoreJavadocAccess(JDT_LS_JAVADOC_CONTENT_FACTORY) {
			@Override
			public String getHTMLContent(IPackageFragment packageFragment) throws CoreException {
				String content = readHTMLContent(packageFragment);
				return sanitizePackageJavadoc(content);
			}

			protected final String UL_CLASS_BLOCK_LIST = "<ul class=\"blockList\">"; //$NON-NLS-1$
			protected final String CONTENT_CONTAINER = "<div class=\"contentContainer\">"; //$NON-NLS-1$

			protected String sanitizePackageJavadoc(String content) {
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

			@Override
			protected StringBuffer createSuperMethodReferencesHTML(ArrayList<IMethod> superInterfaceMethods, IMethod superClassMethod) {
				// jdtls override to return null
				return null;
			}
		};
	}

	public static final IJavadocContentFactory JDT_LS_JAVADOC_CONTENT_FACTORY = new IJavadocContentFactory() {
		@Override
		public IJavadocAccess createJavadocAccess(IJavaElement element, Javadoc javadoc, String source, JavadocLookup lookup) {
			if (source.startsWith("///")) { //$NON-NLS-1$
				if (lookup == null) {
					return new JdtLsMarkdownAccessImpl(element, javadoc, source);
				} else {
					return new JdtLsMarkdownAccessImpl(element, javadoc, source, lookup);
				}
			}
			if (lookup == null) {
				return new JdtLsJavadocAccessImpl(element, javadoc, source);
			} else {
				return new JdtLsJavadocAccessImpl(element, javadoc, source, lookup);
			}
		}
	};

	private static CoreJavaDocSnippetStringEvaluator createJdtLsSnippetEvaluator(IJavaElement element) {
		return new CoreJavaDocSnippetStringEvaluator(element) {

			@Override
			protected String getOneTagElementString(TagElement snippetTag, Object fragment) {
				String str = super.getOneTagElementString(snippetTag, fragment);
				return SNIPPET + str;
			}

			@Override
			protected String getModifiedStringForTagElement(TagElement tagElement, List<TagElement> tagElements) {
				String str = super.getModifiedString(tagElement, tagElements);
				if (TagElement.TAG_LINK.equals(tagElement.getTagName())) {
					int leadingSpaces = 0;
					while (str.length() > leadingSpaces + 1 && str.charAt(leadingSpaces) == ' ') {
						leadingSpaces++;
					}
					try {
						str = new JavaDoc2MarkdownConverter(str).getAsString();
						for (int i = 0; i < leadingSpaces; i++) {
							str = " " + str; //$NON-NLS-1$
						}
						str = str + "  \n"; //$NON-NLS-1$
					} catch (IOException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				}
				return str;
			}
			@Override
			protected String getDefaultBoldTag() {
				return "**"; //$NON-NLS-1$
			}

			@Override
			protected String getDefaultItalicTag() {
				return "*"; //$NON-NLS-1$
			}

			@Override
			protected String getDefaultHighlightedTag() {
				return "***"; //$NON-NLS-1$
			}

			@Override
			protected String getStartTag(String tag) {
				return tag;
			}

			@Override
			protected String getEndTag(String tag) {
				return tag;
			}
		};
	}

	private static class JdtLsMarkdownAccessImpl extends CoreMarkdownAccessImpl {

		public JdtLsMarkdownAccessImpl(IJavaElement element, Javadoc javadoc, String source) {
			super(element, javadoc, source);
		}

		public JdtLsMarkdownAccessImpl(IJavaElement element, Javadoc javadoc, String source, JavadocLookup lookup) {
			super(element, javadoc, source, lookup);
		}

		@Override
		protected CoreJavaDocSnippetStringEvaluator createSnippetEvaluator(IJavaElement element) {
			return createJdtLsSnippetEvaluator(element);
		}

		@Override
		protected ASTNode getNextSiblingElement(TagElement parent, TagElement child) {
			// Core's line break is needed only by its multiline <pre>{@code ...}</pre> workaround
			// For ordinary inline code it leaks into the Markdown returned by JDT LS
			return fPreCounter > 0 ? super.getNextSiblingElement(parent, child) : null;
		}

		@Override
		protected void handleSummary(List<? extends ASTNode> fragments) {
			super.handleSummary(fragments);
			// This is a bug upstream, core increments fLiteralContent for @summary but does not decrement it
			fLiteralContent--;
		}

		@Override
		protected void handleIndex(List<? extends ASTNode> fragments) {
			super.handleIndex(fragments);
			// This is a bug upstream, core increments fLiteralContent for @index but does not decrement it
			fLiteralContent--;
		}

		@Override
		protected void handleInLineTextElement(TextElement te, boolean skipLeadingWhitespace, TagElement tagElement, ASTNode previousNode) {
			String text = te.getText();
			if (JavaDocHTMLPathHandler.containsHTMLTag(text)) {
				text = JavaDocHTMLPathHandler.getValidatedHTMLSrcAttribute(te, fElement);
			}
			if (skipLeadingWhitespace) {
				text = text.replaceFirst("^\\s", ""); //$NON-NLS-1$ //$NON-NLS-2$
			}
			text = text.replaceAll("(\r\n?|\n)([ \t]*\\*)", "$1"); //$NON-NLS-1$ //$NON-NLS-2$
			text = handlePreCounter(tagElement, text);
			handleInLineText(text, previousNode);
		}

		@Override
		protected String createLinkURI(String scheme, IJavaElement element, String refTypeName, String refMemberName, String[] refParameterTypes) throws URISyntaxException {
			return JdtLsJavadocAccessImpl.createLinkURIHelper(scheme, fElement, refTypeName, refMemberName, refParameterTypes);
		}

		@Override
		protected boolean addCodeTagOnLink() {
			return false;
		}
	}

	private static class JdtLsJavadocAccessImpl extends CoreJavadocAccessImpl {

		/**
		 * @param element
		 * @param javadoc
		 * @param source
		 */
		public JdtLsJavadocAccessImpl(IJavaElement element, Javadoc javadoc, String source) {
			super(element, javadoc, source);
		}

		/**
		 * @param element
		 * @param javadoc
		 * @param source
		 * @param lookup
		 */
		public JdtLsJavadocAccessImpl(IJavaElement element, Javadoc javadoc, String source, JavadocLookup lookup) {
			super(element, javadoc, source, lookup);
		}

		@Override
		protected CoreJavaDocSnippetStringEvaluator createSnippetEvaluator(IJavaElement element) {
			return createJdtLsSnippetEvaluator(element);
		}

		@Override
		protected ASTNode getNextSiblingElement(TagElement parent, TagElement child) {
			return fPreCounter > 0 ? super.getNextSiblingElement(parent, child) : null;
		}

		@Override
		protected void handleSummary(List<? extends ASTNode> fragments) {
			super.handleSummary(fragments);
			// This is a bug upstream
			fLiteralContent--;
		}

		@Override
		protected void handleIndex(List<? extends ASTNode> fragments) {
			super.handleIndex(fragments);
			// This is a bug upstream
			fLiteralContent--;
		}

		@Override
		protected void handleSnippet(TagElement node) {
			if (node != null) {
				Object val = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_IS_VALID);
				Object valError = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_ERROR);
				if (val instanceof Boolean && ((Boolean) val).booleanValue() && valError == null) {
					int fs = node.fragments().size();
					if (fs > 0) {
						fBuf.append("<pre>"); //$NON-NLS-1$
						Object valID = node.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_ID);
						if (valID instanceof String && !valID.toString().isBlank()) {
							fBuf.append("<code id=" + valID.toString() + ">"); //$NON-NLS-1$ //$NON-NLS-2$
						} else {
							fBuf.append("<code>");//$NON-NLS-1$
						}
						// Don't surround snippet content with additional code tags
						fSnippetStringEvaluator.AddTagElementString(node, fBuf);
					}
				} else {
					handleInvalidSnippet(node);
				}
			}
		}

		@Override
		protected void handleInLineTextElement(TextElement te, boolean skipLeadingWhitespace, TagElement tagElement, ASTNode previousNode) {
			// Following lines available in jdtls
			String text = te.getText();
			if (JavaDocHTMLPathHandler.containsHTMLTag(text)) {
				text = JavaDocHTMLPathHandler.getValidatedHTMLSrcAttribute(te, fElement);
			}
			if (skipLeadingWhitespace) {
				text = text.replaceFirst("^\\s", ""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=233481 :
			text = text.replaceAll("(\r\n?|\n)([ \t]*\\*)", "$1"); //$NON-NLS-1$ //$NON-NLS-2$
			text = handlePreCounter(tagElement, text);
			handleInLineText(text, previousNode);
		}

		@Override
		protected void handleLink(List<? extends ASTNode> fragments) {
			if (fragments == null || fragments.isEmpty()) {
				return;
			}
			// Special handling for references starting with ## (anchors to the same page), we want to get rid of them
			if (fragments.get(0) instanceof TextElement textElement) {
				String text = textElement.getText();
				if (text.contains("##")) {
					// Remove the first ##word (## followed by word characters)
					String interestingPart = text.replaceFirst("##\\w+\\s*", "");
					handleText(interestingPart);
					return;
				}
			}
			super.handleLink(fragments);
		}

		@Override
		protected boolean addCodeTagOnLink() {
			return false;
		}

		@Override
		protected void handleInLineText(String text, ASTNode previousNode) {
			boolean isInSnippet = previousNode instanceof TagElement previousTag && TagElement.TAG_SNIPPET.equals(previousTag.getTagName());
			handleText(markSnippet(text, isInSnippet));
		}


		@Override
		protected void handleBlockTags(String title, List<TagElement> tags) {
			if (tags.isEmpty()) {
				return;
			}
			handleBlockTagTitle(title);
			fBuf.append(getBlockTagStart());
			for (TagElement tag : tags) {
				handleSingleTag(tag);
			}
			fBuf.append(getBlockTagEnd());
			fBuf.append(getBlockTagEntryEnd());
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void handleSingleTag(TagElement tag) {
			fBuf.append(getBlockTagEntryStart());
			if (TagElement.TAG_SEE.equals(tag.getTagName())) {
				handleSeeTag(tag);
			} else {
				handleContentElements(tag.fragments());
			}
			fBuf.append(getBlockTagEntryEnd());
		}

		@Override
		protected void handleReturnTagBody(TagElement tag, CharSequence returnDescription) {
			// Only add ul tags if there's a return description
			if (tag == null) {
				return;
			}
			fBuf.append(getBlockTagStart());
			super.handleReturnTagBody(tag, returnDescription);
			fBuf.append(getBlockTagEnd());
			fBuf.append(getBlockTagEntryEnd());
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void handleBlockTagBody(TagElement tag) {
			List fragments = tag.fragments();
			if (!fragments.isEmpty()) {
				fBuf.append(getBlockTagStart());
				fBuf.append(getBlockTagEntryStart());
				super.handleContentElements(fragments);
				fBuf.append(getBlockTagEntryEnd());
				fBuf.append(getBlockTagEnd());
			}
		}

		@Override
		protected void handleBlockTagTitle(String title) {
			fBuf.append("<li><b>"); //$NON-NLS-1$
			fBuf.append(title);
			fBuf.append("</b>"); //$NON-NLS-1$
		}

		@Override
		protected void handleExceptionTagsBody(List<TagElement> tags, List<String> exceptionNames, CharSequence[] exceptionDescriptions) {
			// Only add ul tags if there are exceptions to document
			if (tags.isEmpty() && (exceptionNames == null || exceptionNames.isEmpty())) {
				return;
			}
			fBuf.append(getBlockTagStart());
			super.handleExceptionTagsBody(tags, exceptionNames, exceptionDescriptions);
			fBuf.append(getBlockTagEnd());
			fBuf.append(getBlockTagEntryEnd());
		}

		@Override
		protected void handleParameterTags(List<TagElement> tags, List<String> parameterNames, CharSequence[] parameterDescriptions, boolean isTypeParameters) {
			if (tags.isEmpty() && containsOnlyNull(parameterNames)) {
				return;
			}

			String tagTitle = isTypeParameters ? "Type Parameters:" : "Parameters:";
			handleBlockTagTitle(tagTitle);
			// Only add ul tags if there are actually tags to process
			if (!tags.isEmpty()) {
				fBuf.append(getBlockTagStart());
				for (TagElement tag : tags) {
					handleSingleParameterTag(tag);
				}
				fBuf.append(getBlockTagEnd());
			}
			for (int i = 0; i < parameterDescriptions.length; i++) {
				CharSequence description = parameterDescriptions[i];
				String name = parameterNames.get(i);
				if (name != null) {
					handleSingleParameterDescription(name, description, isTypeParameters);
				}
			}
		}


		@Override
		protected void handleSingleParameterDescription(String name, CharSequence description, boolean isTypeParameters) {
			fBuf.append(getBlockTagStart());
			super.handleSingleParameterDescription(name, description, isTypeParameters);
			fBuf.append(getBlockTagEnd());
		}

		protected String markSnippet(String text, boolean isInSnippet) {
			if (isInSnippet) {
				StringBuilder builder = new StringBuilder();
				text.lines().forEach((l) -> {
					builder.append(SNIPPET);
					builder.append(l);
					builder.append("\n"); //$NON-NLS-1$
				});
				return builder.toString();
			}
			return text;
		}

		@Override
		protected String createLinkURI(String scheme, IJavaElement element, String refTypeName, String refMemberName, String[] refParameterTypes) throws URISyntaxException {
			return createLinkURIHelper(scheme, fElement, refTypeName, refMemberName, refParameterTypes);
		}

		public static String createLinkURIHelper(String scheme, IJavaElement element, String refTypeName, String refMemberName, String[] refParameterTypes) throws URISyntaxException {
			URI javadocURI = CoreJavaElementLinks.createURIAsUri(scheme, element, refTypeName, refMemberName, refParameterTypes);
			IJavaElement linkTarget = CoreJavaElementLinks.parseURI(javadocURI);
			if (linkTarget == null) {
			        return "";
			}
			try {
			        Location locationToElement = JDTUtils.toLocation(linkTarget);
			        if (locationToElement != null) {
			                return locationToElement.getUri() + "#" + (locationToElement.getRange().getStart().getLine() + 1);
			        }
			} catch (JavaModelException e) {
			}
			return "";
		}

		@Override
		protected String getBlockTagStart() {
			return "<ul>";
		}

		@Override
		protected String getBlockTagEnd() {
			return "</ul>";
		}

		@Override
		protected String getBlockTagEntryStart() {
			return "<li>";
		}

		@Override
		protected String getBlockTagEntryEnd() {
			return "</li>";
		}

	}
}
