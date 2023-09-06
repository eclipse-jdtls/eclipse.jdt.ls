/*******************************************************************************
 * Copyright (c) 2019-2022 Microsoft Corporation and others.
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

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.corext.template.java.AbstractJavaContextTypeCore;
import org.eclipse.jdt.internal.corext.template.java.CompilationUnitContext;
import org.eclipse.jdt.internal.corext.template.java.IJavaContext;
import org.eclipse.jdt.internal.corext.template.java.JavaTemplateMessages;
import org.eclipse.jdt.internal.corext.template.java.VarResolver;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariableResolver;

/**
 * The context type for templates inside Java code. The same class is used for
 * several context types:
 * <dl>
 * <li>templates for all Java code locations</li>
 * <li>templates for member locations</li>
 * <li>templates for statement locations</li>
 * <li>templates for module-info.java files</li>
 * </dl>
 */
public class JavaContextType extends AbstractJavaContextTypeCore {

	/**
	 * The context type id for templates working on all Java code locations
	 */
	public static final String ID_ALL = "java"; //$NON-NLS-1$

	/**
	 * The context type id for templates working on member locations
	 */
	public static final String ID_MEMBERS = "java-members"; //$NON-NLS-1$

	/**
	 * The context type id for templates working on statement locations
	 */
	public static final String ID_STATEMENTS = "java-statements"; //$NON-NLS-1$

	/**
	 * The context type id for templates working on module-info.java files
	 */
	public static final String ID_MODULE = "module"; //$NON-NLS-1$

	public JavaContextType(String contextType) {
		setId(contextType);
		setName(contextType);
	}

	@Override
	protected void initializeContext(IJavaContext context) {
		// Separate 'module' context type from 'java' context type
		if (getId().equals(ID_MODULE)) {
			return;
		}
		if (!getId().equals(JavaContextType.ID_ALL)) { // a specific context must also allow the templates that work everywhere
			context.addCompatibleContextType(JavaContextType.ID_ALL);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.template.java.AbstractJavaContextTypeCore#initializeContextTypeResolvers()
	 */
	@Override
	public void initializeContextTypeResolvers() {
		super.initializeContextTypeResolvers();
		// TODO: Some of the resolvers are defined in org.eclipse.jdt.ui/plugin.xml, now we have to add them manually.
		// See: https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/dc995e7a0069e1eca58b19a4bc365032c50b0201/org.eclipse.jdt.ui/plugin.xml#L5674-L5752
		addResolver("var", new VarResolver());
		addResolver(new SimpleType());
	}

	public synchronized void addResolver(String type, TemplateVariableResolver resolver) {
		resolver.setType(type);
		addResolver(resolver);
	}

	// Copied from org.eclipse.jdt.internal.corext.template.java.CompilationUnitContextType.EnclosingJavaElement
	protected static class EnclosingJavaElement extends TemplateVariableResolver {
		protected final int fElementType;

		public EnclosingJavaElement(String name, String description, int elementType) {
			super(name, description);
			fElementType = elementType;
		}

		@Override
		protected String resolve(TemplateContext context) {
			IJavaElement element = ((CompilationUnitContext) context).findEnclosingElement(fElementType);
			if (element instanceof IType) {
				// Flag was changed to permit non-qualified types
				return JavaElementLabelsCore.getElementLabel(element, 0);
			}
			return (element == null) ? null : element.getElementName();
		}

		@Override
		protected boolean isUnambiguous(TemplateContext context) {
			return resolve(context) != null;
		}
	}

	// Copied from org.eclipse.jdt.internal.corext.template.java.CompilationUnitContextType.Type
	protected static class SimpleType extends EnclosingJavaElement {
		public SimpleType() {
			super("enclosing_simple_type", JavaTemplateMessages.CompilationUnitContextType_variable_description_enclosing_type, IJavaElement.TYPE);
		}
	}
}

