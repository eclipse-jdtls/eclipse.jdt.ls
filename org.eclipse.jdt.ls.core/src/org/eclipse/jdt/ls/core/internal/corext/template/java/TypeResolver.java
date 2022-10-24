/*******************************************************************************
 * Copyright (c) 2007, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.template.java;

import java.util.List;

import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.jface.text.templates.TemplateVariableResolver;
import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;

/**
 * Copied from org.eclipse.jdt.internal.corext.template.java.TypeResolver
 */
public class TypeResolver extends TemplateVariableResolver {
	private final String fDefaultType;

	/**
	 * Default ctor for instantiation by the extension point.
	 */
	public TypeResolver() {
		this("java.lang.Object"); //$NON-NLS-1$
	}

	TypeResolver(String defaultType) {
		fDefaultType= defaultType;
	}

	@Override
	public void resolve(TemplateVariable variable, TemplateContext context) {
		List<String> params= variable.getVariableType().getParams();
		String param= fDefaultType;
		JavaContext jc= (JavaContext) context;
		MultiVariable mv= (MultiVariable) variable;
		if (params.size() != 0 && context instanceof JavaContext) {
			param= params.get(0);
			TemplateVariable ref = jc.getTemplateVariable(param);

			if (ref instanceof JavaVariable) {
				// Reference is another variable
				JavaVariable refVar= (JavaVariable) ref;
				jc.addDependency(refVar, mv);
				param= refVar.getParamType();
				if (param != null && "".equals(param) == false) { //$NON-NLS-1$
					String reference;
					if (jc instanceof JavaPostfixContext)
						reference= ((JavaPostfixContext)jc).addImportGenericClass(param);
					else
						reference= jc.addImport(param);
					mv.setValue(reference);
					mv.setUnambiguous(true);
					mv.setResolved(true);
					return;
				}
			}
		}

		String reference= jc.addImport(param);
		mv.setValue(reference);
		mv.setUnambiguous(true);
	}

}
