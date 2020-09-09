/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation. and others.
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

package org.eclipse.jdt.ls.core.internal.preferences;

public class CodeTemplatePreferences {
    private static final String CODETEMPLATES_PREFIX = "org.eclipse.jdt.ui.text.codetemplates."; //$NON-NLS-1$
	public static final String COMMENT_SUFFIX = "comment"; //$NON-NLS-1$
	public static final String BODY_SUFFIX = "body"; //$NON-NLS-1$
	private static final String BLOCK_SUFFIX = "block"; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for file comments
	 */
	public static final String CODETEMPLATE_FILECOMMENT = CODETEMPLATES_PREFIX + "file" + COMMENT_SUFFIX;

	/**
	 * A named preference that defines the template for field comments
	 */
	public static final String CODETEMPLATE_FIELDCOMMENT = CODETEMPLATES_PREFIX + "field" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for constructor comments
	 */
	public static final String CODETEMPLATE_CONSTRUCTORCOMMENT = CODETEMPLATES_PREFIX + "constructor" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for constructor body content
	 */
	public static final String CODETEMPLATE_CONSTRUCTORBODY = CODETEMPLATES_PREFIX + "constructor" + BODY_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for method body content
	 */
	public static final String CODETEMPLATE_METHODBODY = CODETEMPLATES_PREFIX + "method" + BODY_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for delegate method comments
	 */
	public static final String CODETEMPLATE_DELEGATECOMMENT = CODETEMPLATES_PREFIX + "delegate" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for overridden method comments
	 */
	public static final String CODETEMPLATE_OVERRIDECOMMENT = CODETEMPLATES_PREFIX + "override" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for method comments
	 */
	public static final String CODETEMPLATE_METHODCOMMENT = CODETEMPLATES_PREFIX + "method" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for snippet body content
	 */
	public static final String CODETEMPLATE_CODESNIPPET = CODETEMPLATES_PREFIX + "snippet" + BODY_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for type comments
	 */
	public static final String CODETEMPLATE_TYPECOMMENT = CODETEMPLATES_PREFIX + "type" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for getter comments
	 */
	public static final String CODETEMPLATE_GETTERCOMMENT = CODETEMPLATES_PREFIX + "getter" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for setter comments
	 */
	public static final String CODETEMPLATE_SETTERCOMMENT = CODETEMPLATES_PREFIX + "setter" + COMMENT_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for getter method body content
	 */
	public static final String CODETEMPLATE_GETTERBODY = CODETEMPLATES_PREFIX + "getter" + BODY_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for setter method body content
	 */
	public static final String CODETEMPLATE_SETTERBODY = CODETEMPLATES_PREFIX + "setter" + BODY_SUFFIX; //$NON-NLS-1$

	/**
	 * A named preference that defines the template for setter method body content
	 */
	public static final String CODETEMPLATE_CATCHBODY = CODETEMPLATES_PREFIX + "catch" + BLOCK_SUFFIX; //$NON-NLS-1$

	public static final String CLASSSNIPPET_CONTEXTTYPE = "classsnippet_context"; //$NON-NLS-1$

	public static final String INTERFACESNIPPET_CONTEXTTYPE = "interfacesnippet_context"; //$NON-NLS-1$

	public static final String RECORDSNIPPET_CONTEXTTYPE = "recordsnippet_context"; //$NON-NLS-1$

	/**
	 * Default value for field comments
	 */
	public static final String CODETEMPLATE_FIELDCOMMENT_DEFAULT = "/**\n" + " *\n" + " */";

	/**
	 * Default value for constructor comments
	 */
	public static final String CODETEMPLATE_CONSTRUCTORCOMMENT_DEFAULT = "/**\n" + " * ${tags}\n" + " */";

	/**
	 * Default value for delegate comments
	 */
	public static final String CODETEMPLATE_DELEGATECOMMENT_DEFAULT = "/**\n" + " * ${tags}\n" + " * ${see_to_target}\n" + " */\n";

	/**
	 * Default value for override comments
	 */
	public static final String CODETEMPLATE_OVERRIDECOMMENT_DEFAULT = "/* (non-Javadoc)\n" + " * ${see_to_overridden}\n"
			+ " */\n";

	/**
	 * Default value for method comments
	 */
	public static final String CODETEMPLATE_METHODCOMMENT_DEFAULT = "/**\n" + " * ${tags}\n" + " */\n";

	/**
	 * Default value for type comments
	 */
	public static final String CODETEMPLATE_TYPECOMMENT_DEFAULT = "/**\n" + " * ${tags}\n" + " */\n";

	/**
	 * Default value for getter comments
	 */
	public static final String CODETEMPLATE_GETTERCOMMENT_DEFAULT = "/**\n" + " * @return the ${bare_field_name}\n" + " */";

	/**
	 * Default value for setter comments
	 */
	public static final String CODETEMPLATE_SETTERCOMMENT_DEFAULT = "/**\n" + " * @param ${param} the ${bare_field_name} to set\n" + " */";

	/**
	 * Default value for getter method body content
	 */
	public static final String CODETEMPLATE_GETTERBODY_DEFAULT = "return ${field};\n";

	/**
	 * Default value for setter method body content
	 */
	public static final String CODETEMPLATE_SETTERBODY_DEFAULT = "${field} = ${param};\n";

	/**
	 * Default value for constructor method body content
	 */
	public static final String CODETEMPLATE_CONSTRUCTORBODY_DEFAULT = "${body_statement}\n//${todo} Auto-generated constructor stub";

	/**
	 * Default value from method body content
	 */
	public static final String CODETEMPLATE_METHODBODY_DEFAULT = "// ${todo} Auto-generated method stub\n${body_statement}";
	/**
	 * Default value for catch body content
	 */
	public static final String CODETEMPLATE_CATCHBODY_DEFAULT = "// ${todo} Auto-generated catch block\n${exception_var}.printStackTrace();";

	/**
	 * Default value for class snippet body content
	 */
	public static final String CODETEMPLATE_CLASSSNIPPET_DEFAULT = "${package_header}class ${type_name} {\n\n\t${cursor}\n}";

	/**
	 * Default value for public class snippet body content
	 */
	public static final String CODETEMPLATE_CLASSSNIPPET_PUBLIC = "${package_header}/**\n * ${type_name}\n */\npublic class ${type_name} {\n\n\t${cursor}\n}";
	/**
	 * Default value for interface snippet body content
	 */
	public static final String CODETEMPLATE_INTERFACESNIPPET_DEFAULT = "${package_header}interface ${type_name} {\n\n\t${cursor}\n}";
	/**
	 * Default value for public interface snippet body content
	 */
	public static final String CODETEMPLATE_INTERFACESNIPPET_PUBLIC = "${package_header}/**\n * ${type_name}\n */\npublic interface ${type_name} {\n\n\t${cursor}\n}";
	/**
	 * Default value for record snippet body content
	 */
	public static final String CODETEMPLATE_RECORDSNIPPET_DEFAULT = "${package_header}record ${type_name}(${cursor}) {\n}";
	/**
	 * Default value for public record snippet body content
	 */
	public static final String CODETEMPLATE_RECORDSNIPPET_PUBLIC = "${package_header}/**\n * ${type_name}\n */\npublic record ${type_name}(${cursor}) {\n}";

}
