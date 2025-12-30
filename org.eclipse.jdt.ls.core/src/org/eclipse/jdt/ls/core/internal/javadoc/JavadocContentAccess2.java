/*******************************************************************************
 * Copyright (c) 2008, 2017 IBM Corporation and others.
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
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IDocElement;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
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
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
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
			if (content != null && content.startsWith("///")) {
				Javadoc node = CoreJavadocContentAccessUtility.getJavadocNode(element, content);
				Map<String, List<TagElement>> javadocTags = new HashMap<>();
				StringBuilder buf = new StringBuilder();
				for (Object obj : node.tags()) {
					TagElement tag = (TagElement) obj;
					if (tag.getTagName() != null) {
						javadocTags.computeIfAbsent(tag.getTagName(), k -> new ArrayList<>()).add((tag));
					} else {
						buf.append("\n");
						collectTagElements(content, element, tag, buf);
					}
				}

				for (Map.Entry<String, List<TagElement>> entry : javadocTags.entrySet()) {
					String tagName = entry.getKey();

					String heading = switch (tagName) {
						case TagElement.TAG_API_NOTE -> "API Note:";
						case TagElement.TAG_AUTHOR -> "Author:";
						case TagElement.TAG_IMPL_SPEC -> "Impl Spec:";
						case TagElement.TAG_IMPL_NOTE -> "Impl Note:";
						case TagElement.TAG_PARAM -> "Parameters:";
						case TagElement.TAG_PROVIDES -> "Provides:";
						case TagElement.TAG_RETURN -> "Returns:";
						case TagElement.TAG_THROWS -> "Throws:";
						case TagElement.TAG_EXCEPTION -> "Throws:";
						case TagElement.TAG_SINCE -> "Since:";
						case TagElement.TAG_SEE -> "See:";
						case TagElement.TAG_VERSION -> "See:";
						case TagElement.TAG_USES -> "Uses:";
						default -> "";
					};
					buf.append("\n");
					buf.append("* **" + heading + "**");

					for (TagElement tag : entry.getValue()) {
						buf.append("\n");
						buf.append("  * ");
						collectTagElements(content, element, tag, buf);
					}
				}

				String htmlContent = buf.length() > 0 ? buf.substring(1) : content;
				if (containsHtmlTag(htmlContent)) {
					return new JavaDoc2MarkdownConverter(htmlContent).getAsString();
				}
				return htmlContent;
			} else {
				String rawHtml = access.getHTMLContent(element, true);
				return new JavaDoc2MarkdownConverter(rawHtml).getAsString();
			}
		} catch (IOException | CoreException e) {

		}

		return null;
	}

	protected static boolean containsHtmlTag(String html) {
		if (html == null) {
			return false;
		}
		return html.matches("(?s).*<\\s*/?\\s*[a-zA-Z][^>]*>.*");
	}

	@SuppressWarnings("unchecked")
	private static void collectTagElements(String content, IJavaElement element, TagElement tag, StringBuilder buf) {
		Deque<ASTNode> queue = new LinkedList<>();
		queue.addAll(tag.fragments());
		while (!queue.isEmpty()) {
			ASTNode e = queue.pop();
			if (e instanceof TagElement t) {
				if ("@link".equals(t.getTagName()) || "@linkplain".equals(t.getTagName())) {
					collectLinkedTag(element, t, buf);
				} else {
					collectTagElements(content, element, t, buf);
				}
			} else if (e instanceof TextElement) {
				buf.append(((TextElement) e).getText());
			} else if ("@see".equals(tag.getTagName())) {
				collectLinkedTag(element, tag, buf);
			} else {
			}

			ASTNode next = queue.peek();
			if (next != null) {
				int currEnd = e.getStartPosition() + e.getLength();
				int nextStart = next.getStartPosition();
				if (currEnd != nextStart) {
					if (content.substring(currEnd, nextStart).split("///").length > 2) {
						buf.append("  \n");
					} else {
						buf.append("\n");
					}
				} else {
					buf.append(" ");
				}
			}
		}
	}

	private static void collectLinkedTag(IJavaElement element, TagElement t, StringBuilder buf) {
		@SuppressWarnings("unchecked")
		List<IDocElement> children = t.fragments();
		if (t.fragments().size() > 0) {
			try {
				String[] res;
				String linkTitle;
				if (t.fragments().size() == 2) {
					linkTitle = ((TextElement) t.fragments().get(0)).getText();
					res = collectLinkElement((ASTNode) children.get(1));
				} else {
					res = collectLinkElement((ASTNode) children.get(0));
					linkTitle = res[0];
				}
				buf.append("[" + linkTitle + "]");
				String uri = JdtLsJavadocAccessImpl.createLinkURIHelper(CoreJavaElementLinks.JAVADOC_SCHEME, element, res[0], res.length > 1 ? res[1] : null,
						res.length > 2 ? Arrays.asList(res).subList(2, res.length).toArray(new String[0]) : null);
				buf.append("(" + uri + ")");
			} catch (URISyntaxException ex) {
				JavaManipulationPlugin.log(ex);
			}
		}
	}

	private static String[] collectLinkElement(ASTNode e) {
		String refTypeName = null;
		String refMemberName = null;
		String[] refMethodParamTypes = null;
		String[] refMethodParamNames = null;
		if (e instanceof Name) {
			Name name = (Name) e;
			refTypeName = name.getFullyQualifiedName();
		} else if (e instanceof MemberRef) {
			MemberRef memberRef = (MemberRef) e;
			Name qualifier = memberRef.getQualifier();
			refTypeName = qualifier == null ? "" : qualifier.getFullyQualifiedName(); //$NON-NLS-1$
			refMemberName = memberRef.getName().getIdentifier();
		} else if (e instanceof MethodRef) {
			MethodRef methodRef = (MethodRef) e;
			Name qualifier = methodRef.getQualifier();
			refTypeName = qualifier == null ? "" : qualifier.getFullyQualifiedName(); //$NON-NLS-1$
			refMemberName = methodRef.getName().getIdentifier();
			@SuppressWarnings("unchecked")
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
		} else if (e instanceof TextElement) {
			refTypeName = ((TextElement) e).getText();
		}
		List<String> result = new ArrayList<>();
		result.add(refTypeName);
		if (refMemberName != null) {
			result.add(refMemberName);
		}
		if (refMethodParamTypes != null) {
			result.addAll(Arrays.asList(refMethodParamTypes));
		}
		return result.toArray(new String[0]);
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
					return new CoreMarkdownAccessImpl(element, javadoc, source);
				} else {
					return new CoreMarkdownAccessImpl(element, javadoc, source, lookup);
				}
			}
			if (lookup == null) {
				return new JdtLsJavadocAccessImpl(element, javadoc, source);
			} else {
				return new JdtLsJavadocAccessImpl(element, javadoc, source, lookup);
			}
		}
	};

	public static String getJavaDocNode(IJavaElement element) throws JavaModelException {
		IMember member;
		if (element instanceof ILocalVariable) {
			member = ((ILocalVariable) element).getDeclaringMember();
		} else if (element instanceof ITypeParameter) {
			member = ((ITypeParameter) element).getDeclaringMember();
		} else if (element instanceof IMember) {
			member = (IMember) element;
		} else {
			return null;
		}

		IBuffer buf = member.getOpenable().getBuffer();
		if (buf == null) {
			return null; // no source attachment found
		}

		ISourceRange javadocRange = member.getJavadocRange();
		if (javadocRange == null) {
			return null;
		}
		String rawJavadoc = buf.getText(javadocRange.getOffset(), javadocRange.getLength());
		return rawJavadoc;
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
			return new CoreJavaDocSnippetStringEvaluator(fElement) {

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
								str = " " + str;
							}
							str = str + "  \n";
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

		//Overridden to decrement fLiteralContent when isSummary || isIndex
		@Override
		protected void handleInlineTagElement(TagElement node) {
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
			boolean isReturn = TagElement.TAG_RETURN.equals(name);

			if (isLiteral || isCode || isSummary || isIndex) {
				fLiteralContent++;
			}
			if (isCode || (isLink && addCodeTagOnLink())) {
				if (isCode && fPreCounter > 0 && fBuf.lastIndexOf("<pre>") == fBuf.length() - 5) { //$NON-NLS-1$
					fInPreCodeCounter = fPreCounter - 1;
				}
				fBuf.append("<code>"); //$NON-NLS-1$
			}
			if (isReturn) {
				fBuf.append("Returns");
			}

			if (isLink || isLinkplain) {
				handleLink(node.fragments());
			} else if (isSummary) {
				handleSummary(node.fragments());
			} else if (isIndex) {
				handleIndex(node.fragments());
			} else if (isCode || isLiteral) {
				handleContentElements(node.fragments(), true, node);
			} else if (isReturn) {
				handleContentElements(node.fragments(), false, node);
			} else if (isSnippet) {
				handleSnippet(node);
			} else if (handleInheritDoc(node) || handleDocRoot(node)) {
				// Handled
			} else {
				//print uninterpreted source {@tagname ...} for unknown tags
				int start = node.getStartPosition();
				String text = fSource.substring(start, start + node.getLength());
				fBuf.append(removeDocLineIntros(text));
			}

			if (isReturn) {
				fBuf.append(".");
			}
			if (isCode || (isLink && addCodeTagOnLink())) {
				fBuf.append("</code>"); //$NON-NLS-1$
			}
			if (isSnippet) {
				fBuf.append("</code></pre>"); //$NON-NLS-1$
			}
			// This is a bug upstream, isSummary || isIndex are missing
			if (isLiteral || isCode || isSummary || isIndex) {
				fLiteralContent--;
			}

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
