/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java;

import java.io.IOException;
import java.io.Reader;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultLineTracker;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ILineTracker;
import org.eclipse.jface.text.IRegion;
import org.jboss.tools.vscode.java.handlers.JsonRpcHelpers;

import copied.org.eclipse.jdt.ui.JavadocContentAccess;

public class HoverInfoProvider {
	
	private final ITypeRoot unit;
	public HoverInfoProvider(ITypeRoot aUnit) {
		this.unit = aUnit;
	}
	
	public String computeHover(int line, int column) {
		try {
			IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(),line,column),0);
			if(elements == null || elements.length != 1)
				return null;
			IJavaElement curr= elements[0];
//			return computeSourceHover(curr);
			return computeJavadocHover(curr);
			
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String computeJavadocHover(IJavaElement element) throws JavaModelException{
		IMember member;
		if (element instanceof ILocalVariable) {
			member= ((ILocalVariable) element).getDeclaringMember();
		} else if (element instanceof ITypeParameter) {
			member= ((ITypeParameter) element).getDeclaringMember();
		} else if (element instanceof IMember) {
			member= (IMember) element;
		} else {
			return null;
		}
		
		IBuffer buf= member.getOpenable().getBuffer();
		if (buf == null) {
			return null; // no source attachment found
		}

		ISourceRange javadocRange= member.getJavadocRange();
		if(javadocRange == null ) return null;
		Reader r = JavadocContentAccess.getHTMLContentReader(member,true,true);
		if(r == null ) return null;
		return getString(r);
	}
	
	
	/**
	 * Returns source as hover
	 * @param curr
	 * @return
	 */
	private String computeSourceHover(IJavaElement curr) {
		if ((curr instanceof IMember || curr instanceof ILocalVariable || curr instanceof ITypeParameter) && curr instanceof ISourceReference) {
			try {
				String source= ((ISourceReference) curr).getSource();

				String[] sourceLines= getTrimmedSource(source, curr);
				if (sourceLines == null)
					return null;

				String delim= Util.getLineSeparator(source,unit.getJavaProject()); 
				source= concatenate(sourceLines, delim);

				return source;
			} catch (JavaModelException ex) {
				//do nothing
			}
		}
		return null;
	}
	
	
	/**
	 * Returns the trimmed source lines.
	 *
	 * @param source the source string, could be <code>null</code>
	 * @param javaElement the java element
	 * @return the trimmed source lines or <code>null</code>
	 */
	private String[] getTrimmedSource(String source, IJavaElement javaElement) {
		if (source == null)
			return null;
		source= removeLeadingComments(source);
		String[] sourceLines= convertIntoLines(source);
//		Strings.trimIndentation(sourceLines, javaElement.getJavaProject());
		return sourceLines;
	}
	
	private String removeLeadingComments(String source) {
		final JavaCodeReader reader= new JavaCodeReader();
		IDocument document= new Document(source);
		int i;
		try {
			reader.configureForwardReader(document, 0, document.getLength(), true, false);
			int c= reader.read();
			while (c != -1 && (c == '\r' || c == '\n')) {
				c= reader.read();
			}
			i= reader.getOffset();
			reader.close();
		} catch (IOException ex) {
			i= 0;
		} finally {
			try {
				reader.close();
			} catch (IOException ex) {
			}
		}

		if (i < 0)
			return source;
		return source.substring(i);
	}
	/**
	 * Gets the reader content as a String
	 *
	 * @param reader the reader
	 * @return the reader content as string
	 */
	private static String getString(Reader reader) {
		StringBuilder buf= new StringBuilder();
		char[] buffer= new char[1024];
		int count;
		try {
			while ((count= reader.read(buffer)) != -1)
				buf.append(buffer, 0, count);
		} catch (IOException e) {
			return null;
		}
		return buf.toString();
	}
	
	/**
	 * Converts the given string into an array of lines. The lines
	 * don't contain any line delimiter characters.
	 *
	 * @param input the string
	 * @return the string converted into an array of strings. Returns <code>
	 * 	null</code> if the input string can't be converted in an array of lines.
	 */
	public static String[] convertIntoLines(String input) {
		try {
			ILineTracker tracker= new DefaultLineTracker();
			tracker.set(input);
			int size= tracker.getNumberOfLines();
			String result[]= new String[size];
			for (int i= 0; i < size; i++) {
				IRegion region= tracker.getLineInformation(i);
				int offset= region.getOffset();
				result[i]= input.substring(offset, offset + region.getLength());
			}
			return result;
		} catch (BadLocationException e) {
			return null;
		}
	}
	
	/**
	 * Concatenate the given strings into one strings using the passed line delimiter as a
	 * delimiter. No delimiter is added to the last line.
	 * @param lines the lines
	 * @param delimiter line delimiter
	 * @return the concatenated lines
	 */
	public static String concatenate(String[] lines, String delimiter) {
		StringBuilder buffer= new StringBuilder();
		for (int i= 0; i < lines.length; i++) {
			if (i > 0)
				buffer.append(delimiter);
			buffer.append(lines[i]);
		}
		return buffer.toString();
	}
}
