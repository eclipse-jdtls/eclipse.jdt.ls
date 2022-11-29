/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Code copied from org.eclipse.jdt.internal.ui.text.javadoc.JavaDocSnippetStringEvaluator
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.JavaDocRegion;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TagProperty;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

public class JavaDocSnippetStringEvaluator {

	public static final String SNIPPET = "SNIPPET";

	/**
	 * Either an IMember or an IPackageFragment.
	 */
	private final IJavaElement fElement;

	public enum ReplacementStringIntervalStatus {
		/**
		 * replacement interval occurs before the given ActionElement interval
		 */
		BEFORE,
		/**
		 * replacement interval occurs after the given ActionElement interval
		 */
		AFTER,
		/**
		 * replacement interval occurs within the given ActionElement interval
		 */
		WITHIN,
		/**
		 * replacement interval contains the given ActionElement interval
		 */
		ENCOMPASS,
		/**
		 * replacement interval starts before the given ActionElement interval and ends within the ActionElement interval
		 */
		PREV_OVERLAP,
		/**
		 * replacement interval starts within the given ActionElement interval and ends after the ActionElement interval
		 */
		POST_OVERLAP,
		/**
		 * Default Value, do nothing
		 */
		DEFAULT
	}

	public class ActionElement{
		public int start;
		public int end;
		public String startTag;
		public String endTag;

		public ActionElement(int start, int end, String startTag, String endTag) {
			this.start= start;
			this.end= end;
			this.startTag= startTag;
			this.endTag= endTag;
		}

		public ReplacementStringIntervalStatus getIntervalStatus(int replacementIntervalStart, int replacementIntervalEnd) {
			ReplacementStringIntervalStatus intervalStatus= ReplacementStringIntervalStatus.DEFAULT;
			int startDiff = replacementIntervalStart - this.start;
			int endDiff = replacementIntervalEnd - this.end;

			if (this.end <= replacementIntervalStart) {
				intervalStatus= ReplacementStringIntervalStatus.AFTER;
			} else if (this.start >= replacementIntervalEnd) {
				intervalStatus= ReplacementStringIntervalStatus.BEFORE;
			} else if (startDiff <= 0 && endDiff >= 0) {
				intervalStatus= ReplacementStringIntervalStatus.ENCOMPASS;
			} else if (startDiff > 0 && endDiff < 0) {
				intervalStatus= ReplacementStringIntervalStatus.WITHIN;
			} else if (startDiff > 0) {
				intervalStatus= ReplacementStringIntervalStatus.POST_OVERLAP;
			} else if (endDiff < 0) {
				intervalStatus= ReplacementStringIntervalStatus.PREV_OVERLAP;
			}

			return intervalStatus;
		}

	}

	public class StringItem{
		public int index;
		public String tag;
		public StringItem(int index, String tag) {
			this.index= index;
			this.tag= tag;
		}
	}

	public JavaDocSnippetStringEvaluator(IJavaElement element) {
		this.fElement= element;
	}

	public void AddTagElementString(TagElement snippetTag, StringBuffer buffer) {
		if (snippetTag != null) {
			List<Object> fragments = snippetTag.fragments();
			for( Object fragment : fragments) {
				String str= ""; //$NON-NLS-1$
				if (fragment instanceof TextElement textElement) {
					List<TagElement> tagElements= getTagElementsForTextElement(snippetTag, textElement);
					str = getModifiedString(textElement, tagElements);
				} else if (fragment instanceof JavaDocRegion region) {
					if (region.isDummyRegion()) {
						List<TagElement> tagElements= getTagElementsForDummyJavaDocRegion(snippetTag, region);
						str = getModifiedString(region, tagElements);
					}
				} else if (fragment instanceof TagElement tagElement) {
					List<TagElement> tagElements= getTagElementsForTagElement(snippetTag, tagElement);
					str = getModifiedString(tagElement, tagElements);
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
				}
				buffer.append(SNIPPET);
				buffer.append(str);
			}
		}
	}

	private String getModifiedString(TextElement textElement, List<TagElement> tags) {
		return getModifiedString(textElement.getText(), tags);
	}

	private String getModifiedString(TagElement tagElement, List<TagElement> tags) {
		return getModifiedString(((TextElement)tagElement.fragments().get(0)).getText(), tags);
	}

	private String getModifiedString(JavaDocRegion region, List<TagElement> tags) {
		return getModifiedString(((TextElement)region.fragments().get(0)).getText(), tags);
	}

	private String getModifiedString(String str, List<TagElement> tags) {
		List<ActionElement> actionElements= new ArrayList<>();
		String modifiedStr= str;
		for (TagElement tag : tags) {
			String name= tag.getTagName();
			if (TagElement.TAG_HIGHLIGHT.equals(name)) {
				handleSnippetHighlight(modifiedStr, tag, actionElements);
			} else if (TagElement.TAG_REPLACE.equals(name)) {
				modifiedStr= handleSnippetReplace(modifiedStr, tag, actionElements);
			} else if (TagElement.TAG_LINK.equals(name)) {
				handleSnippetLink(modifiedStr, tag, actionElements);
			}
		}
		return getString( modifiedStr, actionElements);
	}

	private String getString(String str, List<ActionElement> actionElements) {
		String modifiedStr = str;
		List<StringItem> items= new ArrayList<>();
		for (ActionElement actElem : actionElements) {
			StringItem startItem = new StringItem(actElem.start, actElem.startTag);
			StringItem endItem = new StringItem(actElem.end, actElem.endTag);
			ListIterator<StringItem> iterator = items.listIterator();
			boolean endIndexAdded = false;
			boolean startIndexAdded = false;
			while (iterator.hasNext()) {
				StringItem elem= iterator.next();
				if (!endIndexAdded && elem.index < endItem.index) {
					iterator.previous();
					iterator.add(endItem);
					endIndexAdded = true;
					iterator.next();
				}
				if (!startIndexAdded && elem.index < startItem.index) {
					iterator.previous();
					iterator.add(startItem);
					startIndexAdded = true;
					iterator.next();
				}
				if (startIndexAdded && endIndexAdded) {
					break;
				}
			}
			if (!endIndexAdded) {
				items.add(endItem);
			}
			if (!startIndexAdded) {
				items.add(startItem);
			}
		}
		for (StringItem item : items) {
			modifiedStr = modifiedStr.substring(0, item.index) + item.tag + modifiedStr.substring(item.index);
		}
		return modifiedStr;
	}

	private void modifyPrevActionItemsReplacement(int replacementIntervalStart, int relacementIntervalEnd, int replacementStrNewLength, List<ActionElement> actionElements) {
		ListIterator<ActionElement> iterator = actionElements.listIterator();
		int oldLength = relacementIntervalEnd - replacementIntervalStart;
		int diff = replacementStrNewLength - oldLength;
		while (iterator.hasNext()) {
			ActionElement elem= iterator.next();
			ReplacementStringIntervalStatus intervalStatus= elem.getIntervalStatus(replacementIntervalStart, relacementIntervalEnd);
			switch(intervalStatus) {
				case AFTER:
					//do nothing., nothing needs to be done here
					break;
				case BEFORE:
					if (diff != 0) {
						elem.start+= diff;
						elem.end+= diff;
					}
					break;
				case ENCOMPASS:
					iterator.remove();
					break;
				case POST_OVERLAP:
					elem.end= replacementIntervalStart;
					break;
				case PREV_OVERLAP:
					elem.end += diff;
					elem.start = relacementIntervalEnd + diff;
					break;
				case WITHIN:
					// modify the old ActionElement to end before the replacement string start
					// Create a new Element element to start from the replacement string end
					int newEnd = elem.end + diff;
					elem.end= replacementIntervalStart;
					int newStart = relacementIntervalEnd + diff;
					ActionElement newElem= new  ActionElement(newStart, newEnd, elem.startTag, elem.endTag);
					iterator.add(newElem);
					break;
				case DEFAULT:
				default:
					break;
			}
		}
	}

	private List<TagElement> getTagElementsForTextElement(TagElement snippetTag, TextElement textElement) {
		List<TagElement> tagElements= new ArrayList<>();
		List<JavaDocRegion> regions= snippetTag.tagRegionsStartingAtTextElement(textElement);
		List<JavaDocRegion> masterList= snippetTag.tagRegionsContainingTextElement(textElement);
		masterList.removeAll(regions);
		for (JavaDocRegion region : masterList) {
			for (Object tagObj : region.tags()) {
				tagElements.add((TagElement) tagObj);
			}
		}
		for (JavaDocRegion region : regions) {
			for (Object tagObj : region.tags()) {
				if (tagObj instanceof TagElement tagElem) {
					Object prop= tagElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
					if (prop instanceof Integer intProp) {
						int val = intProp.intValue();
						ListIterator<TagElement> listIterator= tagElements.listIterator();
						TagElement addBefore= null;
						while (listIterator.hasNext()) {
				           TagElement tElem=  listIterator.next();
				           Object prop2= tElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
							if (prop2 instanceof Integer intProp2) {
								int val2 = intProp2.intValue();
				        	   if (val2 > val) {
				        		   addBefore= tElem;
				        		   break;
				        	   }
				           }
				        }
						if (addBefore == null) {
							tagElements.add(tagElem);
						} else {
							tagElements.add(tagElements.indexOf(addBefore), tagElem);
						}
					}
				}
			}
		}
		return tagElements;
	}

	private List<TagElement> getTagElementsForDummyJavaDocRegion(TagElement snippetTag, JavaDocRegion javaDocRegion) {
		List<TagElement> tagElements= new ArrayList<>();
		TextElement textElement= (TextElement) javaDocRegion.fragments().get(0);
		List<JavaDocRegion> regions= snippetTag.tagRegionsStartingAtTextElement(textElement);
		List<JavaDocRegion> masterList= snippetTag.tagRegionsContainingTextElement(textElement);
		masterList.removeAll(regions);
		for (JavaDocRegion region : masterList) {
			for (Object tagObj : region.tags()) {
				tagElements.add((TagElement) tagObj);
			}
		}
		regions.add(javaDocRegion);
		for (JavaDocRegion region : regions) {
			for (Object tagObj : region.tags()) {
				if (tagObj instanceof TagElement tagElem) {
					Object prop= tagElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
					if (prop instanceof Integer intProp) {
						int val = intProp.intValue();
						ListIterator<TagElement> listIterator= tagElements.listIterator();
						TagElement addBefore= null;
						while (listIterator.hasNext()) {
				           TagElement tElem=  listIterator.next();
				           Object prop2= tElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
							if (prop2 instanceof Integer intProp2) {
								int val2 = intProp2.intValue();
				        	   if (val2 > val) {
				        		   addBefore= tElem;
				        		   break;
				        	   }
				           }
				        }
						if (addBefore == null) {
							tagElements.add(tagElem);
						} else {
							tagElements.add(tagElements.indexOf(addBefore), tagElem);
						}
					}
				}
			}
		}

		return tagElements;
	}

	private List<TagElement> getTagElementsForTagElement(TagElement snippetTag, TagElement tag) {
		List<TagElement> tagElements= new ArrayList<>();
		TextElement textElement= (TextElement) tag.fragments().get(0);
		List<JavaDocRegion> regions= snippetTag.tagRegionsStartingAtTextElement(textElement);
		List<JavaDocRegion> masterList= snippetTag.tagRegionsContainingTextElement(textElement);
		masterList.removeAll(regions);
		for (JavaDocRegion region : masterList) {
			for (Object tagObj : region.tags()) {
				tagElements.add((TagElement) tagObj);
			}
		}
		for (JavaDocRegion region : regions) {
			for (Object tagObj : region.tags()) {
				if (tagObj instanceof TagElement tagElem) {
					Object prop= tagElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
					if (prop instanceof Integer intProp) {
						int val = intProp.intValue();
						ListIterator<TagElement> listIterator= tagElements.listIterator();
						TagElement addBefore= null;
						while (listIterator.hasNext()) {
				           TagElement tElem=  listIterator.next();
				           Object prop2= tElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
							if (prop2 instanceof Integer intProp2) {
								int val2 = intProp2.intValue();
				        	   if (val2 > val) {
				        		   addBefore= tElem;
				        		   break;
				        	   }
				           }
				        }
						if (addBefore == null) {
							tagElements.add(tagElem);
						} else {
							tagElements.add(tagElements.indexOf(addBefore), tagElem);
						}
					}
				}
			}
		}
		Object prop3= tag.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
		if (prop3 instanceof Integer intProp3) {
			int val = intProp3.intValue();
			ListIterator<TagElement> listIterator= tagElements.listIterator();
			TagElement addBefore= null;
			while (listIterator.hasNext()) {
		          TagElement tElem=  listIterator.next();
		          Object prop2= tElem.getProperty(TagProperty.TAG_PROPERTY_SNIPPET_INLINE_TAG_COUNT);
					if (prop2 instanceof Integer intProp2) {
						int val2 = intProp2.intValue();
		       	   if (val2 > val) {
		       		   addBefore= tElem;
		       		   break;
		       	   }
		          }
		       }
			if (addBefore == null) {
				tagElements.add(tag);
			} else {
				tagElements.add(tagElements.indexOf(addBefore), tag);
			}
		}
		return tagElements;
	}

	private void handleSnippetHighlight(String text, TagElement tagElement, List<ActionElement> actionElements) {
		try {
			List<? extends TagProperty> tagProperties= tagElement.tagProperties();
			String defHighlight= getHighlightHtmlTag(tagProperties);
			String startDefHighlight = defHighlight; // $NON-NLS-1$
			String endDefHighlight = defHighlight; // $NON-NLS-1$
			String regExValue = getPropertyValue("regex", tagProperties); //$NON-NLS-1$
			String subStringValue = getPropertyValue("substring", tagProperties); //$NON-NLS-1$
			Pattern regexPattern = null;
			if (regExValue != null) {
				regexPattern = Pattern.compile(regExValue);
			}
			if (regexPattern != null) {
				Matcher matcher = regexPattern.matcher(text);
				while (matcher.find()) {
					actionElements.add(new ActionElement(matcher.start(), matcher.end(), startDefHighlight, endDefHighlight));
				}
			} else if (subStringValue != null) {
				int startIndex = 0;
				while ((startIndex = text.indexOf(subStringValue, startIndex)) != -1) {
					actionElements.add(new ActionElement(startIndex, startIndex + subStringValue.length(), startDefHighlight, endDefHighlight));
					startIndex += subStringValue.length();
				}
			} else {
				actionElements.add(new ActionElement(0, text.length(), startDefHighlight, endDefHighlight));
			}
			return;
		} catch (PatternSyntaxException e) {
			// do nothing
		}
		return;
	}

	private String handleSnippetReplace(String text, TagElement tagElement, List<ActionElement> actionElements) {
		try {
			List<? extends TagProperty> tagProperties= tagElement.tagProperties();
			String regExValue = getPropertyValue("regex", tagProperties); //$NON-NLS-1$
			String subStringValue = getPropertyValue("substring", tagProperties); //$NON-NLS-1$
			String substitution = getPropertyValue("replacement", tagProperties); //$NON-NLS-1$
			Pattern regexPattern = null;
			if (regExValue != null) {
				regexPattern = Pattern.compile(regExValue);
			}
			String modifiedText = text;
			if (regexPattern != null) {
				Matcher matcher = regexPattern.matcher(modifiedText);
				StringBuilder strBuild= new StringBuilder();
				int finalMatchIndex = 0;
				while (matcher.find()) {
					finalMatchIndex = matcher.end();
					modifyPrevActionItemsReplacement(matcher.start(), matcher.end(), substitution.length(), actionElements);
					matcher.appendReplacement(strBuild, substitution);
				}
				modifiedText = strBuild.toString() + modifiedText.substring(finalMatchIndex);
			} else if (subStringValue != null) {
				int startIndex = 0;
				while ((startIndex = modifiedText.indexOf(subStringValue, startIndex)) != -1) {
					modifyPrevActionItemsReplacement(startIndex, startIndex+ subStringValue.length(), substitution.length(), actionElements);
					modifiedText = modifiedText.substring(0, startIndex) + substitution + modifiedText.substring(startIndex + subStringValue.length());
					startIndex += substitution.length() ;
				}
			} else {
				actionElements.clear();
				modifiedText = substitution;
			}
			return modifiedText;
		} catch (PatternSyntaxException e) {
			// do nothing
		}
		return text;
	}


	private void handleSnippetLink(String text, TagElement tagElement, List<ActionElement> actionElements) {
		try {
			List<? extends TagProperty> tagProperties= tagElement.tagProperties();
			String regExValue = getPropertyValue("regex", tagProperties); //$NON-NLS-1$
			String subStringValue = getPropertyValue("substring", tagProperties); //$NON-NLS-1$
			String additionalStartTag= getLinkHtmlTag(tagProperties);
			String additionalEndTag= ""; //$NON-NLS-1$
			if (additionalStartTag.length() > 0) {
				additionalEndTag= "</" + additionalStartTag + '>'; //$NON-NLS-1$
				additionalStartTag= '<' + additionalStartTag + '>';
			}
			ASTNode target = getPropertyNodeValue("target", tagProperties); //$NON-NLS-1$
			String linkRefTxt = getLinkRef(target);
			String startDefLink = linkRefTxt + additionalStartTag;
			String endDefLink = additionalEndTag+"</a>"; //$NON-NLS-1$
			Pattern regexPattern = null;
			if (regExValue != null) {
				regexPattern = Pattern.compile(regExValue);
			}
			String modifiedText = text;
			if (regexPattern != null) {
				Matcher matcher = regexPattern.matcher(modifiedText);
				while (matcher.find()) {
					actionElements.add(new ActionElement(matcher.start(), matcher.end(), startDefLink, endDefLink));
				}
			} else if (subStringValue != null) {
				int startIndex = 0;
				while ((startIndex = modifiedText.indexOf(subStringValue, startIndex)) != -1) {
					actionElements.add(new ActionElement(startIndex, startIndex+ subStringValue.length(), startDefLink, endDefLink));
					startIndex = startIndex+ subStringValue.length() ;
				}
			} else {
				String subText = modifiedText.trim();
				if (subText.length() < text.length()) {
					int startIndex = text.indexOf(subText);
					actionElements.add(new ActionElement(startIndex, startIndex + subText.length(), startDefLink, endDefLink));
				}
			}
			return;
		} catch (PatternSyntaxException e) {
			// do nothing
		}
		return;
	}

	private String getLinkRef(ASTNode node) {
		String str= ""; //$NON-NLS-1$
		String refTypeName= null;
		String refMemberName= null;
		String[] refMethodParamTypes= null;
		String[] refMethodParamNames= null;
		int startPosition = -1;
		if (node instanceof Name name) {
			refTypeName= name.getFullyQualifiedName();
			startPosition = name.getStartPosition();
		} else if (node instanceof MemberRef memberRef) {
			Name qualifier= memberRef.getQualifier();
			refTypeName= qualifier == null ? "" : qualifier.getFullyQualifiedName(); //$NON-NLS-1$
			refMemberName= memberRef.getName().getIdentifier();
			startPosition = memberRef.getStartPosition();
		} else if (node instanceof MethodRef methodRef) {
			Name qualifier= methodRef.getQualifier();
			refTypeName= qualifier == null ? "" : qualifier.getFullyQualifiedName(); //$NON-NLS-1$
			refMemberName= methodRef.getName().getIdentifier();
			List<MethodRefParameter> params= methodRef.parameters();
			int ps= params.size();
			refMethodParamTypes= new String[ps];
			refMethodParamNames= new String[ps];
			for (int i= 0; i < ps; i++) {
				MethodRefParameter param= params.get(i);
				refMethodParamTypes[i]= ASTNodes.asString(param.getType());
				SimpleName paramName= param.getName();
				if (paramName != null) {
					refMethodParamNames[i]= paramName.getIdentifier();
				}
			}
			startPosition = methodRef.getStartPosition();
		}

		if (refTypeName != null) {
			str += "<a href='"; //$NON-NLS-1$
			try {
				String scheme = "file"; // JavaElementLinks.JAVADOC_SCHEME;
				// String uri= JavaElementLinks.createURI(scheme, fElement, refTypeName, refMemberName, refMethodParamTypes);
				String uri = JavaElementLinks.createURI("file", fElement, refTypeName, refMemberName, refMethodParamTypes, startPosition);
				str += uri;
			} catch (URISyntaxException e) {
				JavaLanguageServerPlugin.logException(e);
			}
			str += "'>"; //$NON-NLS-1$
		}
		return str;
	}

	private String getLinkHtmlTag(List<? extends ASTNode> tagProperties) {
		// String defaultTag= "code"; //$NON-NLS-1$
		String defaultTag = ""; //$NON-NLS-1$
		if (tagProperties != null) {
			for (ASTNode node : tagProperties) {
				if (node instanceof TagProperty tagProp) {
					if ("type".equals(tagProp.getName())) { //$NON-NLS-1$
						String tagValue = tagProp.getStringValue();
						switch (tagValue) {
							case "linkplain" :  //$NON-NLS-1$
								defaultTag= ""; //$NON-NLS-1$
								break;
							default:
								break;
						}
						break;
					}
				}
			}
		}
		return defaultTag;
	}

	private String getPropertyValue(String property, List<? extends ASTNode> tagProperties) {
		String defaultTag= null;
		if (tagProperties != null && property!= null) {
			for (ASTNode node : tagProperties) {
				if (node instanceof TagProperty tagProp) {
					if (property.equals(tagProp.getName())) {
						defaultTag= tagProp.getStringValue();
						break;
					}
				}
			}
		}
		return defaultTag;
	}
	private ASTNode getPropertyNodeValue(String property, List<? extends ASTNode> tagProperties) {
		ASTNode defaultTag= null;
		if (tagProperties != null && property!= null) {
			for (ASTNode node : tagProperties) {
				if (node instanceof TagProperty tagProp) {
					if (property.equals(tagProp.getName())) {
						defaultTag= tagProp.getNodeValue();
						break;
					}
				}
			}
		}
		return defaultTag;
	}

	private String getHighlightHtmlTag(List<? extends ASTNode> tagProperties) {
		String defaultTag = "**"; //$NON-NLS-1$
		if (tagProperties != null) {
			for (ASTNode node : tagProperties) {
				if (node instanceof TagProperty tagProp) {
					if ("type".equals(tagProp.getName())) { //$NON-NLS-1$
						String tagValue = tagProp.getStringValue();
						switch (tagValue) {
							case "bold" :  //$NON-NLS-1$
								defaultTag = "**"; //$NON-NLS-1$
								break;
							case "italic" :  //$NON-NLS-1$
								defaultTag = "*"; //$NON-NLS-1$
								break;
							case "highlighted" :  //$NON-NLS-1$
								defaultTag = "***"; //$NON-NLS-1$
								break;
							default :
								defaultTag= ""; //$NON-NLS-1$
								break;
						}
						break;
					}
				}
			}
		}
		return defaultTag;
	}

}
