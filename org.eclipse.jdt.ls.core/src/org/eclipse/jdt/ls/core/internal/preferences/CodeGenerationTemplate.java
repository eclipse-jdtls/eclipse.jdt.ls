/*******************************************************************************
 * Copyright (c) 2017-2026 Microsoft Corporation. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *     Red Hat, Inc. - added record snippet
 *     IBM Corporation - Markdown templates
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

import java.util.Objects;

import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jface.text.templates.Template;

public enum CodeGenerationTemplate {
	/**
	 * New type template
	 */
	NEWTYPE(CodeTemplatePreferences.CODETEMPLATE_NEWTYPE,
			CodeTemplateContextType.NEWTYPE_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_NEWTYPE_DEFAULT),
	/**
	 * File comment template
	 */
	FILECOMMENT(CodeTemplatePreferences.CODETEMPLATE_FILECOMMENT, CodeTemplateContextType.FILECOMMENT_CONTEXTTYPE, ""),

	/**
	 * Field comment template
	 */
	FIELDCOMMENT(CodeTemplatePreferences.CODETEMPLATE_FIELDCOMMENT, CodeTemplateContextType.FIELDCOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_FIELDCOMMENT_DEFAULT),

	/**
	 * Field markdown comment template
	 */
	FIELDMARKDOWNCOMMENT(CodeTemplatePreferences.CODETEMPLATE_FIELDMARKDOWNCOMMENT, CodeTemplateContextType.MARKDOWNFIELDCOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_FIELDCOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Method comment template
	 */
	METHODCOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_METHODCOMMENT,
			CodeTemplateContextType.METHODCOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_METHODCOMMENT_DEFAULT),

	/**
	 * Method markdown comment template
	 */
	METHODMARKDOWNCOMMENT(CodeTemplatePreferences.CODETEMPLATE_METHODMARKDOWNCOMMENT, CodeTemplateContextType.MARKDOWNMETHODCOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_METHODCOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Constructor comment template
	 */
	CONSTRUCTORCOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORCOMMENT,
			CodeTemplateContextType.CONSTRUCTORCOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORCOMMENT_DEFAULT),

	/**
	 * Constructor markdown comment template
	 */
	CONSTRUCTORMARKDOWNCOMMENT(CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORMARKDOWNCOMMENT, CodeTemplateContextType.MARKDOWNCONSTRUCTORCOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORCOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Constructor method body template
	 */
	CONSTRUCTORBODY(
			CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORBODY,
			CodeTemplateContextType.CONSTRUCTORBODY_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_CONSTRUCTORBODY_DEFAULT),

	/**
	 * Delegate comment template
	 */
	DELEGATECOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_DELEGATECOMMENT,
			CodeTemplateContextType.DELEGATECOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_DELEGATECOMMENT_DEFAULT),

	/**
	 * Delegate markdown comment template
	 */
	DELEGATEMARKDOWNCOMMENT(CodeTemplatePreferences.CODETEMPLATE_MARKDOWN_DELEGATECOMMENT, CodeTemplateContextType.MARKDOWNDELEGATECOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_DELEGATECOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Override comment template
	 */
	OVERRIDECOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_OVERRIDECOMMENT,
			CodeTemplateContextType.OVERRIDECOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_OVERRIDECOMMENT_DEFAULT),

	/**
	 * Override markdown comment template
	 */
	OVERRIDEMARKDOWNCOMMENT(CodeTemplatePreferences.CODETEMPLATE_OVERRIDEMARKDOWNCOMMENT, CodeTemplateContextType.MARKDOWNOVERRIDECOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_OVERRIDECOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Type comment template
	 */
	TYPECOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_TYPECOMMENT,
			CodeTemplateContextType.TYPECOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_TYPECOMMENT_DEFAULT),

	/**
	 * Type markdown comment template
	 */
	TYPEMARKDOWNCOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_TYPEMARKDOWNCOMMENT,
			CodeTemplateContextType.MARKDOWNTYPECOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_TYPECOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Getter comment template
	 */
	GETTERCOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_GETTERCOMMENT,
			CodeTemplateContextType.GETTERCOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_GETTERCOMMENT_DEFAULT),

	/**
	 * Getter markdown comment template
	 */
	GETTERMARKDOWNCOMMENT(CodeTemplatePreferences.CODETEMPLATE_GETTERMARKDOWNCOMMENT, CodeTemplateContextType.MARKDOWNGETTERCOMMENT_CONTEXTTYPE, CodeTemplatePreferences.CODETEMPLATE_GETTERCOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Getter comment template
	 */
	SETTERCOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_SETTERCOMMENT,
			CodeTemplateContextType.SETTERCOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_SETTERCOMMENT_DEFAULT),

	/**
	 * Getter comment template
	 */
	SETTERMARKDOWNCOMMENT(
			CodeTemplatePreferences.CODETEMPLATE_SETTERMARKDOWNCOMMENT,
			CodeTemplateContextType.MARKDOWNSETTERCOMMENT_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_SETTERCOMMENT_MARKDOWN_DEFAULT),

	/**
	 * Getter method body content
	 */
	GETTERBODY(
			CodeTemplatePreferences.CODETEMPLATE_GETTERBODY,
			CodeTemplateContextType.GETTERBODY_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_GETTERBODY_DEFAULT),

	/**
	 * Setter method body content
	 */
	SETTERBOY(
			CodeTemplatePreferences.CODETEMPLATE_SETTERBODY,
			CodeTemplateContextType.SETTERBODY_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_SETTERBODY_DEFAULT),

	/**
	 * Catch body content
	 */
	CATCHBODY(
			CodeTemplatePreferences.CODETEMPLATE_CATCHBODY,
			CodeTemplateContextType.CATCHBLOCK_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_CATCHBODY_DEFAULT),

	/**
	 * Method body content template
	 */
	METHODBODY(
			CodeTemplatePreferences.CODETEMPLATE_METHODBODY,
			CodeTemplateContextType.METHODBODY_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_METHODBODY_DEFAULT),

	/**
	 * Method body content template for super implementations
	 */
	METHODBODYSUPER(
			CodeTemplatePreferences.CODETEMPLATE_METHODBODY_SUPER,
			CodeTemplateContextType.METHODBODY_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_METHODBODY_SUPER_DEFAULT),

	/**
	 * Snippet `public class` content template
	 */
	CLASSSNIPPET_PUBLIC(
			CodeTemplatePreferences.CODETEMPLATE_CODESNIPPET,
			CodeTemplatePreferences.CLASSSNIPPET_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_CLASSSNIPPET_PUBLIC),

	/**
	 * Snippet `class` content template
	 */
	CLASSSNIPPET_DEFAULT(
			CodeTemplatePreferences.CODETEMPLATE_CODESNIPPET,
			CodeTemplatePreferences.CLASSSNIPPET_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_CLASSSNIPPET_DEFAULT),

	/**
	 * Snippet `public interface` content template
	 */
	INTERFACESNIPPET_PUBLIC(
			CodeTemplatePreferences.CODETEMPLATE_CODESNIPPET,
			CodeTemplatePreferences.INTERFACESNIPPET_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_INTERFACESNIPPET_PUBLIC),

	/**
	 * Snippet `interface` content template
	 */
	INTERFACESNIPPET_DEFAULT(
			CodeTemplatePreferences.CODETEMPLATE_CODESNIPPET,
			CodeTemplatePreferences.INTERFACESNIPPET_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_INTERFACESNIPPET_DEFAULT),

	/**
	 * Snippet `public record` content template
	 */
	RECORDSNIPPET_PUBLIC(
			CodeTemplatePreferences.CODETEMPLATE_CODESNIPPET,
			CodeTemplatePreferences.RECORDSNIPPET_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_RECORDSNIPPET_PUBLIC),

	/**
	 * Snippet `record` content template
	 */
	RECORDSNIPPET_DEFAULT(
			CodeTemplatePreferences.CODETEMPLATE_CODESNIPPET,
			CodeTemplatePreferences.RECORDSNIPPET_CONTEXTTYPE,
			CodeTemplatePreferences.CODETEMPLATE_RECORDSNIPPET_DEFAULT);


	private final String preferenceId;
	private final String contextType;
	private final String defaultContent;

	private CodeGenerationTemplate(String preferenceId, String contextType, String defaultContent) {
		this.preferenceId = preferenceId;
		this.contextType = contextType;
		this.defaultContent = defaultContent;

	}

	public Template createTemplate() {
		return new Template(this.name(), this.preferenceId, this.contextType, this.defaultContent, false);
	}

	public Template createTemplate(String content) {
		return new Template(this.name(), this.preferenceId, this.contextType, content, false);
	}

	public static CodeGenerationTemplate getValueById(String preferenceId) {
		for (CodeGenerationTemplate value : values()) {
			if (Objects.equals(preferenceId, value.preferenceId)) {
				return value;
			}
		}

		return null;
	}
}
