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
package org.jboss.tools.vscode.java.internal.handlers;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.lsp4j.CompletionItem;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.internal.javadoc.JavadocContentAccess;

public class CompletionResolveHandler {

	public static final String DATA_FIELD_URI = "uri";
	public static final String DATA_FIELD_DECLARATION_SIGNATURE = "decl_signature";
	public static final String DATA_FIELD_SIGNATURE= "signature";
	public static final String DATA_FIELD_NAME = "name";


	CompletionItem resolve(CompletionItem param) {

		@SuppressWarnings("unchecked")
		Map<String, String> data = (Map<String, String>) param.getData();
		// clean resolve data
		param.setData(null);

		if (data.containsKey(DATA_FIELD_URI) && data.containsKey(DATA_FIELD_DECLARATION_SIGNATURE)) {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(data.get(DATA_FIELD_URI));
			String typeName = SignatureUtil.stripSignatureToFQN(String.valueOf(data.get(DATA_FIELD_DECLARATION_SIGNATURE)));
			try {
				IMember member = null;
				IType type = unit.getJavaProject().findType(typeName);

				if (type!=null && data.containsKey(DATA_FIELD_NAME)) {
					String name = data.get(DATA_FIELD_NAME);
					String[] paramSigs = CharOperation.NO_STRINGS;
					if(data.containsKey( DATA_FIELD_SIGNATURE)){
						String[] parameters= Signature.getParameterTypes(String.valueOf(SignatureUtil.fix83600(data.get(DATA_FIELD_SIGNATURE).toCharArray())));
						for (int i= 0; i < parameters.length; i++) {
							parameters[i]= SignatureUtil.getLowerBound(parameters[i]);
						}
						paramSigs = parameters;
					}
					IMethod method = type.getMethod(name, paramSigs);
					if (method.exists()) {
						member = method;
					} else {
						IField field = type.getField(name);
						if (field.exists()) {
							member = field;
						}
					}
				} else {
					member = type;
				}

				if (member!=null && member.exists()) {
					Reader reader = JavadocContentAccess.getHTMLContentReader(member, true, true);
					if (reader != null)
						param.setDocumentation(getString(reader));
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Unable to resolve compilation", e);
			}
		}
		return param;
	}

	/**
	 * Gets the reader content as a String
	 *
	 * @param reader
	 *            the reader
	 * @return the reader content as string
	 */
	private static String getString(Reader reader) {
		StringBuilder buf = new StringBuilder();
		char[] buffer = new char[1024];
		int count;
		try {
			while ((count = reader.read(buffer)) != -1)
				buf.append(buffer, 0, count);
		} catch (IOException e) {
			return null;
		}
		return buf.toString();
	}

}
