package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.ls.core.internal.javadoc.html.SubstitutionTextReader;

/**
 * Processes JavaDoc tags.
 */
public class JavaDoc2HTMLTextReader extends SubstitutionTextReader {


	static private class Pair {
		String fTag;
		String fContent;

		Pair(String tag, String content) {
			fTag= tag;
			fContent= content;
		}
	}

	private List<String> fParameters;
	private String fReturn;
	private List<String> fExceptions;
	private List<String> fAuthors;
	private List<String> fSees;
	private List<String> fSince;
	private List<Pair> fRest; // list of Pair objects

	public JavaDoc2HTMLTextReader(Reader reader) {
		super(reader);
		setSkipWhitespace(false);
	}

	private int getTag(StringBuilder buffer) throws IOException {
		int c= nextChar();
		while (c == '.' || c != -1 && Character.isLetter((char) c)) {
			buffer.append((char) c);
			c= nextChar();
		}
		return c;
	}

	private int getContent(StringBuilder buffer, char stopChar) throws IOException {
		int c= nextChar();
		while (c != -1 && c != stopChar) {
			buffer.append((char) c);
			c= nextChar();
		}
		return c;
	}

	private int getContentUntilNextTag(StringBuilder buffer) throws IOException {
		int c= nextChar();
		boolean blockStartRead= false;
		while (c != -1) {
			if (c == '@') {
				int index= buffer.length();
				while (--index >= 0 && Character.isWhitespace(buffer.charAt(index))) {
					switch (buffer.charAt(index)) {
					case '\n':
					case '\r':
						return c;
					}
					if (index <= 0) {
						return c;
					}
				}
			}
			if (blockStartRead) {
				buffer.append(processBlockTag());
				blockStartRead= false;
			} else {
				buffer.append((char) c);
			}

			c= nextChar();
			blockStartRead= c == '{';
		}
		return c;
	}

	private String substituteQualification(String qualification) {
		String result;
		if (qualification.indexOf("<a") == -1) { //$NON-NLS-1$
			// No tag at all, use smart way
			result= qualification.replace('#', '.');
		} else {
			// Handle tags
			int length= qualification.length();
			result= qualification;
			boolean insideTag= false;
			for (int i= 0; i < length; i++) {
				char charAt= result.charAt(i);
				if (charAt == '<' && result.charAt(i + 1) == 'a')
					insideTag= true;
				if (charAt == '>')
					insideTag= false;
				if (charAt == '#' && !insideTag)
					result= result.substring(0, i) + "." + result.substring(i + 1); //$NON-NLS-1$
			}
		}

		if (result.startsWith(".")) //$NON-NLS-1$
			result= result.substring(1);
		return result;
	}

	private void printDefinitions(StringBuilder buffer, List<String> list, boolean firstword) {
		Iterator<String> e= list.iterator();
		while (e.hasNext()) {
			String s= e.next();
			buffer.append("<li>"); //$NON-NLS-1$
			if (!firstword)
				buffer.append(s);
			else {
				buffer.append("<b>"); //$NON-NLS-1$

				int i= getParamEndOffset(s);
				if (i <= s.length()) {
					buffer.append(convertToHTMLContent(s.substring(0, i)));
					buffer.append("</b>"); //$NON-NLS-1$
					buffer.append(s.substring(i));
				} else {
					buffer.append("</b>"); //$NON-NLS-1$
				}
			}
			buffer.append("</li>"); //$NON-NLS-1$
		}
	}

	private int getParamEndOffset(String s) {
		int i= 0;
		final int length= s.length();
		// \s*
		while (i < length && Character.isWhitespace(s.charAt(i)))
			++i;
		if (i < length && s.charAt(i) == '<') {
			// generic type parameter
			// read <\s*\w*\s*>
			while (i < length && Character.isWhitespace(s.charAt(i)))
				++i;
			while (i < length && Character.isJavaIdentifierPart(s.charAt(i)))
				++i;
			while (i < length && s.charAt(i) != '>')
				++i;
			++i; // >
		} else {
			// simply read an identifier
			while (i < length && Character.isJavaIdentifierPart(s.charAt(i)))
				++i;
		}

		return i;
	}

	private void print(StringBuilder buffer, String tag, List<String> elements, boolean firstword) {
		if ( !elements.isEmpty()) {
			buffer.append("<li><b>"); //$NON-NLS-1$
			buffer.append(tag);
			buffer.append("</b><ul>");
			printDefinitions(buffer, elements, firstword);
			buffer.append("</ul></li>"); //$NON-NLS-1$
		}
	}

	private void print(StringBuilder buffer, String tag, String content) {
		if  (content != null) {
			buffer.append("<li><b>"); //$NON-NLS-1$
			buffer.append(tag);
			buffer.append("</b><ul><li>"); //$NON-NLS-1$
			buffer.append(content);
			buffer.append("</li></ul></li>"); //$NON-NLS-1$
		}
	}

	private void printRest(StringBuilder buffer) {
		if ( !fRest.isEmpty()) {
			Iterator<Pair> e= fRest.iterator();
			while (e.hasNext()) {
				Pair p= e.next();
				buffer.append("<li>"); //$NON-NLS-1$
				if (p.fTag != null)
					buffer.append(p.fTag);
				buffer.append("<ul><li>"); //$NON-NLS-1$
				if (p.fContent != null)
					buffer.append(p.fContent);
				buffer.append("</li></ul></li>"); //$NON-NLS-1$
			}
		}
	}

	private String printSimpleTag() {
		StringBuilder buffer= new StringBuilder();
		buffer.append("<ul>"); //$NON-NLS-1$
		print(buffer, "See Also:",fSees, false);
		print(buffer, "Parameters:", fParameters, true);
		print(buffer, "Returns:", fReturn);
		print(buffer, "Throws:", fExceptions, false);
		print(buffer, "Author:", fAuthors, false);
		print(buffer, "Since:", fSince, false);
		printRest(buffer);
		buffer.append("</ul>"); //$NON-NLS-1$

		return buffer.toString();
	}

	private void handleTag(String tag, String tagContent) {

		tagContent= tagContent.trim();

		if (TagElement.TAG_PARAM.equals(tag))
			fParameters.add(tagContent);
		else if (TagElement.TAG_RETURN.equals(tag))
			fReturn= tagContent;
		else if (TagElement.TAG_EXCEPTION.equals(tag))
			fExceptions.add(tagContent);
		else if (TagElement.TAG_THROWS.equals(tag))
			fExceptions.add(tagContent);
		else if (TagElement.TAG_AUTHOR.equals(tag))
			fAuthors.add(substituteQualification(tagContent));
		else if (TagElement.TAG_SEE.equals(tag))
			fSees.add(substituteQualification(tagContent));
		else if (TagElement.TAG_SINCE.equals(tag))
			fSince.add(substituteQualification(tagContent));
		else if (tagContent != null)
			fRest.add(new Pair(tag, tagContent));
	}

	/*
	 * A '@' has been read. Process a javadoc tag
	 */
	private String processSimpleTag() throws IOException {

		fParameters= new ArrayList<>();
		fExceptions= new ArrayList<>();
		fAuthors= new ArrayList<>();
		fSees= new ArrayList<>();
		fSince= new ArrayList<>();
		fRest= new ArrayList<>();

		StringBuilder buffer= new StringBuilder();
		int c= '@';
		while (c != -1) {

			buffer.setLength(0);
			buffer.append((char) c);
			c= getTag(buffer);
			String tag= buffer.toString();

			buffer.setLength(0);
			if (c != -1) {
				c= getContentUntilNextTag(buffer);
			}

			handleTag(tag, buffer.toString());
		}

		return printSimpleTag();
	}

	private String printBlockTag(String tag, String tagContent) {

		if (TagElement.TAG_LINK.equals(tag) || TagElement.TAG_LINKPLAIN.equals(tag)) {

			char[] contentChars= tagContent.toCharArray();
			boolean inParentheses= false;
			int labelStart= 0;

			for (int i= 0; i < contentChars.length; i++) {
				char nextChar= contentChars[i];

				// tagContent always has a leading space
				if (i == 0 && Character.isWhitespace(nextChar)) {
					labelStart= 1;
					continue;
				}

				if (nextChar == '(') {
					inParentheses= true;
					continue;
				}

				if (nextChar == ')') {
					inParentheses= false;
					continue;
				}

				// Stop at first whitespace that is not in parentheses
				if (!inParentheses && Character.isWhitespace(nextChar)) {
					labelStart= i+1;
					break;
				}
			}
			if (TagElement.TAG_LINK.equals(tag))
				return "<code>" + substituteQualification(tagContent.substring(labelStart)) + "</code>";  //$NON-NLS-1$//$NON-NLS-2$
			else
				return substituteQualification(tagContent.substring(labelStart));

		} else if (TagElement.TAG_LITERAL.equals(tag) || TagElement.TAG_CODE.equals(tag)) {
			return printLiteral(tagContent);

		} else if (TagElement.TAG_CODE.equals(tag)) {
			return "<code>" + printLiteral(tagContent) + "</code>"; //$NON-NLS-1$//$NON-NLS-2$
		}

		// If something went wrong at least replace the {} with the content
		return substituteQualification(tagContent);
	}

	private String printLiteral(String tagContent) {
		int contentStart= 0;
		for (int i= 0; i < tagContent.length(); i++) {
			if (! Character.isWhitespace(tagContent.charAt(i))) {
				contentStart= i;
				break;
			}
		}
		return convertToHTMLContent(tagContent.substring(contentStart));
	}

	/*
	 * A '{' has been read. Process a block tag
	 */
	private String processBlockTag() throws IOException {

		int c= nextChar();

		if (c != '@') {
			StringBuilder buffer= new StringBuilder();
			buffer.append('{');
			buffer.append((char) c);
			return buffer.toString();
		}

		StringBuilder buffer= new StringBuilder();
		if (c != -1) {

			buffer.setLength(0);
			buffer.append((char) c);

			c= getTag(buffer);
			String tag= buffer.toString();

			buffer.setLength(0);
			if (c != -1 && c != '}') {
				buffer.append((char) c);
				c= getContent(buffer, '}');
			}

			return printBlockTag(tag, buffer.toString());
		}

		return null;
	}

	/*
	 * @see SubstitutionTextReaderr#computeSubstitution(int)
	 */
	@Override
	protected String computeSubstitution(int c) throws IOException {
		if (c == '@' && fWasWhiteSpace)
			return processSimpleTag();

		if (c == '{')
			return processBlockTag();

		return null;
	}

	/**
	 * Escapes reserved HTML characters in the given string.
	 * <p>
	 * <b>Warning:</b> Does not preserve whitespace.
	 *
	 * @param content the input string
	 * @return the string with escaped characters
	 *
	 * @see #convertToHTMLContentWithWhitespace(String) for use in browsers
	 * @see #addPreFormatted(StringBuilder, String) for rendering with an {@link HTML2TextReader}
	 */
	private static String convertToHTMLContent(String content) {
		content= replace(content, '&', "&amp;"); //$NON-NLS-1$
		content= replace(content, '"', "&quot;"); //$NON-NLS-1$
		content= replace(content, '<', "&lt;"); //$NON-NLS-1$
		return replace(content, '>', "&gt;"); //$NON-NLS-1$
	}

	private static String replace(String text, char c, String s) {

		int previous= 0;
		int current= text.indexOf(c, previous);

		if (current == -1)
			return text;

		StringBuilder buffer= new StringBuilder();
		while (current > -1) {
			buffer.append(text.substring(previous, current));
			buffer.append(s);
			previous= current + 1;
			current= text.indexOf(c, previous);
		}
		buffer.append(text.substring(previous));

		return buffer.toString();
	}
}
