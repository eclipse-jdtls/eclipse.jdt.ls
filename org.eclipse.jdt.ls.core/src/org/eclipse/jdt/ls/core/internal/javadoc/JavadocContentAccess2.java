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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavaDocSnippetStringEvaluator;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavadocAccess;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavadocAccessImpl;
import org.eclipse.jdt.core.manipulation.internal.javadoc.CoreJavadocContentAccessUtility;
import org.eclipse.jdt.core.manipulation.internal.javadoc.IJavadocContentFactory;
import org.eclipse.jdt.core.manipulation.internal.javadoc.JavadocLookup;
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

	public static Reader getPlainTextContentReader(IMember member) throws JavaModelException {
		Reader contentReader = CoreJavadocContentAccessUtility.getHTMLContentReader(member, true, true);
		if (contentReader != null) {
			try {
				return new JavaDoc2PlainTextConverter(contentReader).getAsReader();
			} catch (IOException e) {
				throw new JavaModelException(e, IJavaModelStatusConstants.UNKNOWN_JAVADOC_FORMAT);
			}
		}
		return null;
	}

	public static Reader getMarkdownContentReader(IJavaElement element) {

		try {
			CoreJavadocAccess access = createJdtLsJavadocAccess();
			String rawHtml = access.getHTMLContent(element, true);
			Reader markdownReader = new JavaDoc2MarkdownConverter(rawHtml).getAsReader();
			return markdownReader;
		} catch (IOException | CoreException e) {

		}

		return null;
	}

	/**
	 * @return
	 */
	private static CoreJavadocAccess createJdtLsJavadocAccess() {
		// TODO Auto-generated method stub
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

	public static final IJavadocContentFactory JDT_LS_JAVADOC_CONTENT_FACTORY=new IJavadocContentFactory(){@Override public IJavadocAccess createJavadocAccess(IJavaElement element,Javadoc javadoc,String source,JavadocLookup lookup){if(lookup==null){return new JdtLsJavadocAccessImpl(element,javadoc,source);}else{return new JdtLsJavadocAccessImpl(element,javadoc,source,lookup);}}};

	private static class JdtLsJavadocAccessImpl extends CoreJavadocAccessImpl {

		/**
		 * @param element
		 * @param javadoc
		 * @param source
		 */
		public JdtLsJavadocAccessImpl(IJavaElement element, Javadoc javadoc, String source) {
			super(element, javadoc, source);
			// TODO Auto-generated constructor stub
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
			super.handleBlockTags(title, tags);
			fBuf.append(BlOCK_TAG_ENTRY_END);
		}

		@Override
		protected void handleSingleTag(TagElement tag) {
			fBuf.append(BLOCK_TAG_START);
			super.handleSingleTag(tag);
			fBuf.append(BLOCK_TAG_END);
		}

		@Override
		protected void handleReturnTagBody(TagElement tag, CharSequence returnDescription) {
			fBuf.append(BLOCK_TAG_START);
			super.handleReturnTagBody(tag, returnDescription);
			fBuf.append(BLOCK_TAG_END);
			fBuf.append(BlOCK_TAG_ENTRY_END);
		}

		@Override
		protected void handleBlockTagBody(TagElement tag) {
			List fragments = tag.fragments();
			if (!fragments.isEmpty()) {
				fBuf.append(BLOCK_TAG_START);
				fBuf.append(BlOCK_TAG_ENTRY_START);
				super.handleContentElements(fragments);
				fBuf.append(BlOCK_TAG_ENTRY_END);
				fBuf.append(BLOCK_TAG_END);
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
			fBuf.append(BLOCK_TAG_START);
			super.handleExceptionTagsBody(tags, exceptionNames, exceptionDescriptions);
			fBuf.append(BLOCK_TAG_END);
			fBuf.append(BlOCK_TAG_ENTRY_END);
		}

		@Override
		protected void handleSingleParameterTag(TagElement tag) {
			fBuf.append(BLOCK_TAG_START);
			super.handleSingleParameterTag(tag);
			fBuf.append(BLOCK_TAG_END);
		}

		@Override
		protected void handleSingleParameterDescription(String name, CharSequence description, boolean isTypeParameters) {
			fBuf.append(BLOCK_TAG_START);
			super.handleSingleParameterDescription(name, description, isTypeParameters);
			fBuf.append(BLOCK_TAG_END);
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
			URI javadocURI = CoreJavaElementLinks.createURIAsUri(scheme, fElement, refTypeName, refMemberName, refParameterTypes);
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
	}
}
