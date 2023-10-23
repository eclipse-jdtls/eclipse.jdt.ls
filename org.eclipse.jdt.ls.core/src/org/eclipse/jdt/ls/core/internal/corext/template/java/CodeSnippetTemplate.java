/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.text.templates.Template;

public enum CodeSnippetTemplate {

	//@formatter:off
	SYSOUT(TemplatePreferences.SYSOUT_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSOUT_CONTENT, TemplatePreferences.SYSOUT_DESCRIPTION),
	SYSERR(TemplatePreferences.SYSERR_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSERR_CONTENT, TemplatePreferences.SYSERR_DESCRIPTION),
	SYSTRACE(TemplatePreferences.SYSTRACE_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSTRACE_CONTENT, TemplatePreferences.SYSTRACE_DESCRIPTION),
	FOREACH(TemplatePreferences.FOREACH_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.FOREACH_CONTENT, TemplatePreferences.FOREACH_DESCRIPTION),
	FORI(TemplatePreferences.FORI_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.FORI_CONTENT, TemplatePreferences.FORI_DESCRIPTION),
	WHILE(TemplatePreferences.WHILE_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.WHILE_CONTENT, TemplatePreferences.WHILE_DESCRIPTION),
	DOWHILE(TemplatePreferences.DOWHILE_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.DOWHILE_CONTENT, TemplatePreferences.DOWHILE_DESCRIPTION),
	IF(TemplatePreferences.IF_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.IF_CONTENT, TemplatePreferences.IF_DESCRIPTION),
	IFELSE(TemplatePreferences.IFELSE_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.IFELSE_CONTENT, TemplatePreferences.IFELSE_DESCRIPTION),
	IFNULL(TemplatePreferences.IFNULL_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.IFNULL_CONTENT, TemplatePreferences.IFNULL_DESCRIPTION),
	IFNOTNULL(TemplatePreferences.IFNOTNULL_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.IFNOTNULL_CONTENT, TemplatePreferences.IFNOTNULL_DESCRIPTION),
	SWITCH(TemplatePreferences.SWITCH_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SWITCH_CONTENT, TemplatePreferences.SWITCH_DESCRIPTION),
	TRY_CATCH(TemplatePreferences.TRYCATCH_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.TRYCATCH_CONTENT, TemplatePreferences.TRYCATCH_DESCRIPTION),
	TRY_RESOURCES(TemplatePreferences.TRYRESOURCES_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.TRYRESOURCES_CONTENT, TemplatePreferences.TRYRESOURCES_DESCRIPTION),
	CTOR(TemplatePreferences.CTOR_ID, JavaContextType.ID_MEMBERS, TemplatePreferences.CTOR_CONTENT, TemplatePreferences.CTOR_DESCRIPTION),
	METHOD(TemplatePreferences.METHOD_ID, JavaContextType.ID_MEMBERS, TemplatePreferences.METHOD_CONTENT, TemplatePreferences.METHOD_DESCRIPTION),
	STATIC_METHOD(TemplatePreferences.STATICMETHOD_ID, JavaContextType.ID_MEMBERS, TemplatePreferences.STATIC_METHOD_CONTENT, TemplatePreferences.STATIC_METHOD_DESCRIPTION),
	FIELD(TemplatePreferences.FIELD_ID, JavaContextType.ID_MEMBERS, TemplatePreferences.FIELD_CONTENT, TemplatePreferences.FIELD_DESCRIPTION),
	MAIN(TemplatePreferences.MAIN_ID, JavaContextType.ID_MEMBERS, TemplatePreferences.MAIN_CONTENT, TemplatePreferences.MAIN_DESCRIPTION),
	NEW(TemplatePreferences.NEW_ID, JavaContextType.ID_ALL, TemplatePreferences.NEW_CONTENT, TemplatePreferences.NEW_DESCRIPTION),

	// the following snippets are the same as above but with different alias, since users may not easily find them if they come from different IDEs.
	SOUT(TemplatePreferences.SOUT_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSOUT_CONTENT, TemplatePreferences.SYSOUT_DESCRIPTION),
	SERR(TemplatePreferences.SERR_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSERR_CONTENT, TemplatePreferences.SYSERR_DESCRIPTION),
	SOUTM(TemplatePreferences.SOUTM_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSTRACE_CONTENT, TemplatePreferences.SYSTRACE_DESCRIPTION),
	ITER(TemplatePreferences.ITER_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.FOREACH_CONTENT, TemplatePreferences.FOREACH_DESCRIPTION),
	PSVM(TemplatePreferences.PSVM_ID, JavaContextType.ID_MEMBERS, TemplatePreferences.MAIN_CONTENT, TemplatePreferences.MAIN_DESCRIPTION),
	SYS_OUT(TemplatePreferences.SYS_OUT_ID, "System.out.println()", JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSOUT_CONTENT, TemplatePreferences.SYSOUT_DESCRIPTION),
	SYS_ERR(TemplatePreferences.SYS_ERR_ID, "System.err.println()", JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSERR_CONTENT, TemplatePreferences.SYSERR_DESCRIPTION),
	PUBLIC_MAIN(TemplatePreferences.PUBLIC_MAIN_ID, "public static void main(String[] args)", JavaContextType.ID_MEMBERS, TemplatePreferences.MAIN_CONTENT, TemplatePreferences.MAIN_DESCRIPTION);

	//@formatter:on

	private final String templateId;
	private final String contextType;
	private final String defaultContent;
	private final String description;
	private String displayName = null;

	private CodeSnippetTemplate(String templatesId, String contextType, String defaultContent, String description) {
		this.templateId = templatesId;
		this.contextType = contextType;
		this.defaultContent = defaultContent;
		this.description = description;
	}

	private CodeSnippetTemplate(String templatesId, String displayName, String contextType, String defaultContent, String description) {
		this(templatesId, contextType, defaultContent, description);
		this.displayName = displayName;
	}

	public Template createTemplate() {
		return new Template(this.getDisplayName(), this.description, this.contextType, this.defaultContent, false);
	}

	public String getId() {
		return this.templateId;
	}

	public String getDisplayName() {
		return StringUtils.isNotBlank(this.displayName) ? this.displayName : this.name().toLowerCase();
	}
}

class TemplatePreferences {
	// IDs
	public static final String SYSOUT_ID = "org.eclipse.jdt.ls.templates.sysout";
	public static final String SOUT_ID = "org.eclipse.jdt.ls.templates.sout";
	public static final String SYSERR_ID = "org.eclipse.jdt.ls.templates.syserr";
	public static final String SERR_ID = "org.eclipse.jdt.ls.templates.serr";
	public static final String SYSTRACE_ID = "org.eclipse.jdt.ls.templates.systrace";
	public static final String SOUTM_ID = "org.eclipse.jdt.ls.templates.soutm";
	public static final String FOREACH_ID = "org.eclipse.jdt.ls.templates.for_array";
	public static final String ITER_ID = "org.eclipse.jdt.ls.templates.iter";
	public static final String FORI_ID = "org.eclipse.jdt.ls.templates.for_iterable";
	public static final String WHILE_ID = "org.eclipse.jdt.ls.templates.while_condition";
	public static final String DOWHILE_ID = "org.eclipse.jdt.ls.templates.do";
	public static final String IF_ID = "org.eclipse.jdt.ls.templates.if";
	public static final String IFELSE_ID = "org.eclipse.jdt.ls.templates.ifelse";
	public static final String IFNULL_ID = "org.eclipse.jdt.ls.templates.ifnull";
	public static final String IFNOTNULL_ID = "org.eclipse.jdt.ls.templates.ifnotnull";
	public static final String SWITCH_ID = "org.eclipse.jdt.ls.templates.switch";
	public static final String TRYCATCH_ID = "org.eclipse.jdt.ls.templates.trycatch";
	public static final String TRYRESOURCES_ID = "org.eclipse.jdt.ls.templates.tryresources";
	public static final String MAIN_ID = "org.eclipse.jdt.ls.templates.main";
	public static final String PSVM_ID = "org.eclipse.jdt.ls.templates.psvm";
	public static final String CTOR_ID = "org.eclipse.jdt.ls.templates.ctor";
	public static final String METHOD_ID = "org.eclipse.jdt.ls.templates.method";
	public static final String STATICMETHOD_ID = "org.eclipse.jdt.ls.templates.staticmethod";
	public static final String NEW_ID = "org.eclipse.jdt.ls.templates.new";
	public static final String FIELD_ID = "org.eclipse.jdt.ls.templates.field";
	public static final String SYS_OUT_ID = "org.eclipse.jdt.ls.templates.sys_out";
	public static final String SYS_ERR_ID = "org.eclipse.jdt.ls.templates.sys_err";
	public static final String PUBLIC_MAIN_ID = "org.eclipse.jdt.ls.templates.publicmain";

	// DefaultContents
	public static final String SYSOUT_CONTENT = "System.out.println($${0});";
	public static final String SYSERR_CONTENT = "System.err.println($${0});";
	public static final String SYSTRACE_CONTENT = "System.out.println(\"${enclosing_type}.${enclosing_method}()\");";
	public static final String FOREACH_CONTENT = "for ($${1:${iterable_type}} $${2:${iterable_element}} : $${3:${iterable}}) {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "}";
	public static final String FORI_CONTENT = "for ($${1:int} $${2:${index}} = $${3:0}; $${2:${index}} < $${4:${array}.length}; $${2:${index}}++) {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "}";
	public static final String WHILE_CONTENT = "while ($${1:${condition:var(boolean)}}) {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "}";
	public static final String DOWHILE_CONTENT = "do {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "} while ($${1:${condition:var(boolean)}});";
	public static final String IF_CONTENT = "if ($${1:${condition:var(boolean)}}) {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "}";
	public static final String IFELSE_CONTENT = "if ($${1:${condition:var(boolean)}}) {\n" + "\t$${2}\n" + "} else {\n" + "\t$${0}\n" + "}";
	public static final String IFNULL_CONTENT = "if ($${1:${name:var}} == null) {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "}";
	public static final String IFNOTNULL_CONTENT = "if ($${1:${name:var}} != null) {\n" + "\t$$TM_SELECTED_TEXT$${0}\n" + "}";
	public static final String SWITCH_CONTENT = "switch ($${1:${key:var}}) {\n" + "\tcase $${2:value}:\n" + "\t\t$${0}\n" + "\t\tbreak;\n\n" + "\tdefault:\n" + "\t\tbreak;\n" + "}";
	public static final String TRYCATCH_CONTENT = "try {\n" + "\t$$TM_SELECTED_TEXT$${1}\n" + "} catch ($${2:Exception} $${3:e}) {\n" + "\t$${0}// TODO: handle exception\n" + "}";
	public static final String TRYRESOURCES_CONTENT = "try ($${1}) {\n" + "\t$$TM_SELECTED_TEXT$${2}\n" + "} catch ($${3:Exception} $${4:e}) {\n" + "\t$${0}// TODO: handle exception\n" + "}";
	public static final String MAIN_CONTENT = "public static void main(String[] args) {\n" + "\t$${0}\n" + "}";
	public static final String CTOR_CONTENT = "$${1|public,protected,private|} ${enclosing_simple_type}($${2}) {\n" + "\t$${3:super();}$${0}\n" + "}";
	public static final String METHOD_CONTENT = "$${1|public,protected,private|}$${2| , static |}$${3:void} $${4:name}($${5}) {\n" + "\t$${0}\n" + "}";
	public static final String STATIC_METHOD_CONTENT = "$${1|public,private|} static $${2:void} $${3:name}($${4}) {\n" + "\t$${0}\n" + "}";
	public static final String NEW_CONTENT = "$${1:Object} $${2:foo} = new $${1}($${3});\n" + "$${0}";
	public static final String FIELD_CONTENT = "$${1|public,protected,private|} $${2:String} $${3:name};";

	// Descriptions
	public static final String SYSOUT_DESCRIPTION = "print to standard out";
	public static final String SYSERR_DESCRIPTION = "print to standard err";
	public static final String SYSTRACE_DESCRIPTION = "print current method to standard out";
	public static final String FOREACH_DESCRIPTION = "iterate over an array or Iterable";
	public static final String FORI_DESCRIPTION = "iterate over array";
	public static final String WHILE_DESCRIPTION = "while statement";
	public static final String DOWHILE_DESCRIPTION = "do-while statement";
	public static final String IF_DESCRIPTION = "if statement";
	public static final String IFELSE_DESCRIPTION = "if-else statement";
	public static final String IFNULL_DESCRIPTION = "if statement checking for null";
	public static final String IFNOTNULL_DESCRIPTION = "if statement checking for not null";
	public static final String SWITCH_DESCRIPTION = "switch statement";
	public static final String TRYCATCH_DESCRIPTION = "try/catch block";
	public static final String TRYRESOURCES_DESCRIPTION = "try/catch block with resources";
	public static final String MAIN_DESCRIPTION = "public static main method";
	public static final String CTOR_DESCRIPTION = "constructor";
	public static final String METHOD_DESCRIPTION = "method";
	public static final String STATIC_METHOD_DESCRIPTION = "static method";
	public static final String NEW_DESCRIPTION = "create new object";
	public static final String FIELD_DESCRIPTION = "field";
}
