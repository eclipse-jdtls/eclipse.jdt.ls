/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.template.java;

import org.eclipse.jface.text.templates.Template;

public enum PostfixTemplate {

	//@formatter:on
	CAST(PostfixPreferences.CAST_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.CAST_CONTENT, PostfixPreferences.CAST_DESCRIPTION),
	IF(PostfixPreferences.IF_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.IF_CONTENT, PostfixPreferences.IF_DESCRIPTION),
	ELSE(PostfixPreferences.ELSE_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.ELSE_CONTENT, PostfixPreferences.ELSE_DESCRIPTION),
	FOR(PostfixPreferences.FOR_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.FOR_CONTENT, PostfixPreferences.FOR_DESCRIPTION),
	FORI(PostfixPreferences.FORI_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.FORI_CONTENT, PostfixPreferences.FORI_DESCRIPTION),
	FORR(PostfixPreferences.FORR_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.FORR_CONTENT, PostfixPreferences.FORR_DESCRIPTION),
	NNULL(PostfixPreferences.NNULL_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.NNULL_CONTENT, PostfixPreferences.NNULL_DESCRIPTION),
	NULL(PostfixPreferences.NULL_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.NULL_CONTENT, PostfixPreferences.NULL_DESCRIPTION),
	SYSOUT(PostfixPreferences.SYSOUT_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.SYSOUT_CONTENT, PostfixPreferences.SYSOUT_DESCRIPTION),
	THROW(PostfixPreferences.THROW_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.THROW_CONTENT, PostfixPreferences.THROW_DESCRIPTION),
	VAR(PostfixPreferences.VAR_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.VAR_CONTENT, PostfixPreferences.VAR_DESCRIPTION),
	WHILE(PostfixPreferences.WHILE_ID, JavaPostfixContextType.ID_ALL, PostfixPreferences.WHILE_CONTENT, PostfixPreferences.WHILE_DESCRIPTION);
	//@formatter:off

	private final String templateId;
	private final String contextType;
	private final String defaultContent;
	private final String description;

	private PostfixTemplate(String templatesId, String contextType, String defaultContent, String description) {
		this.templateId = templatesId;
		this.contextType = contextType;
		this.defaultContent = defaultContent;
		this.description = description;
	}

	public Template createTemplate() {
		return new Template(this.name().toLowerCase(), this.description, this.contextType, this.defaultContent, false);
	}

	public String getId() {
		return this.templateId;
	}
}

class PostfixPreferences {
	// IDs
	public static final String CAST_ID = "org.eclipse.jdt.postfixcompletion.cast";
	public static final String ELSE_ID = "org.eclipse.jdt.ls.postfixcompletion.else";
	public static final String FOR_ID = "org.eclipse.jdt.postfixcompletion.for";
	public static final String FORI_ID = "org.eclipse.jdt.postfixcompletion.fori";
	public static final String FORR_ID = "org.eclipse.jdt.postfixcompletion.forr";
	public static final String IF_ID = "org.eclipse.jdt.ls.postfixcompletion.if";
	public static final String NNULL_ID = "org.eclipse.jdt.postfixcompletion.nnull";
	public static final String NULL_ID = "org.eclipse.jdt.postfixcompletion.null";
	public static final String SYSOUT_ID = "org.eclipse.jdt.postfixcompletion.sysout";
	public static final String THROW_ID = "org.eclipse.jdt.postfixcompletion.throw";
	public static final String VAR_ID = "org.eclipse.jdt.postfixcompletion.var";
	public static final String WHILE_ID = "org.eclipse.jdt.postfixcompletion.while";

	// Default Contents
	public static final String CAST_CONTENT = "(($${1})${inner_expression})$${0}";
	public static final String ELSE_CONTENT = "if (!${i:inner_expression(boolean)}) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String FOR_CONTENT = "for (${type:newActualType(i)} $${1:${n:newName(i)}} : ${i:inner_expression(java.util.Collection,array)}) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String FORI_CONTENT = "for (int $${1:${index}} = 0; $${1:${index}} < ${i:inner_expression(array)}.length; $${1:${index}}++) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String FORR_CONTENT = "for (int $${1:${index}} = ${i:inner_expression(array)}.length - 1; $${1:${index}} >= 0; $${1:${index}}--) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String IF_CONTENT = "if (${i:inner_expression(boolean)}) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String NNULL_CONTENT = "if (${i:inner_expression(java.lang.Object,array)} != null) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String NULL_CONTENT = "if (${i:inner_expression(java.lang.Object,array)} == null) {\n" +
		"\t$${0}\n" +
	"}";
	public static final String SYSOUT_CONTENT = "System.out.println(${i:inner_expression(java.lang.Object)}${});$${0}";
	public static final String THROW_CONTENT = "throw ${true:inner_expression(java.lang.Throwable)};";
	public static final String VAR_CONTENT = "${field:newType(inner_expression)} $${1:${var:newName(inner_expression)}} = ${inner_expression};$${0}";
	public static final String WHILE_CONTENT = "while (${i:inner_expression(boolean)}) {\n" +
		"\t$${0}\n" +
	"}";

	// Descriptions
	public static final String CAST_DESCRIPTION = "Casts the expression to a new type";
	public static final String ELSE_DESCRIPTION = "Creates a negated if statement";
	public static final String FOR_DESCRIPTION = "Creates a for statement";
	public static final String FORI_DESCRIPTION = "Creates a for statement which iterates over an array";
	public static final String FORR_DESCRIPTION = "Creates a for statement which iterates over an array in reverse order";
	public static final String IF_DESCRIPTION = "Creates a if statement";
	public static final String NNULL_DESCRIPTION = "Creates an if statement and checks if the expression does not resolve to null";
	public static final String NULL_DESCRIPTION = "Creates an if statement which checks if expression resolves to null";
	public static final String SYSOUT_DESCRIPTION = "Sends the affected object to a System.out.println(..) call";
	public static final String THROW_DESCRIPTION = "Throws the given Exception";
	public static final String VAR_DESCRIPTION = "Creates a new variable";
	public static final String WHILE_DESCRIPTION = "Creates a while loop";
}
