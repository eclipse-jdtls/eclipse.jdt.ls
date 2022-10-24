/*******************************************************************************
 * Copyright (c) 2019 Nicolaj Hoess.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Nicolaj Hoess - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.template.java;

import java.util.List;

import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.jdt.internal.corext.template.java.JavaVariable;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;

/**
 * Copied from org.eclipse.jdt.internal.corext.template.java.ActualTypeResolver
 */
public class ActualTypeResolver extends TypeResolver  {
	private static final String EMPTY= ""; //$NON-NLS-1$

	private static final String ARRAY_BRACKETS= "[]"; //$NON-NLS-1$

	private static final String GENERIC_CLASS_SERPATOR= ","; //$NON-NLS-1$

	private static final String GENERIC_CLASS_OPEN_DIAMOND= "<"; //$NON-NLS-1$

	private static final String GENERIC_CLASS_CLOSE_DIAMOND= ">"; //$NON-NLS-1$

	@Override
	public void resolve(TemplateVariable variable, TemplateContext context) {
		List<String> params= variable.getVariableType().getParams();
		if (params.size() > 0 && context instanceof JavaPostfixContext) {
			String param= params.get(0);
			JavaPostfixContext jc= (JavaPostfixContext) context;
			TemplateVariable ref= jc.getTemplateVariable(param);
			MultiVariable mv= (MultiVariable) variable;

			if (ref instanceof JavaVariable) {
				// Reference is another variable
				JavaVariable refVar= (JavaVariable) ref;
				jc.addDependency(refVar, mv);

				param= refVar.getParamType();
				if (param != null && EMPTY.equals(param) == false) {
					param= param.replace("? extends ", EMPTY); //$NON-NLS-1$
					if (param.endsWith(ARRAY_BRACKETS)) { // In case of List<Integer[]> we must not remove []
						// Variable is an array, i.e. String[] or List<String>[]
						// Actual type is supposed to be:
						// String[]							=> String
						// List<String>[]					=> List<String>
						// String[][]						=> String[]
						param= param.substring(0, param.length() - 2);
					} else if (param.endsWith(">")) { // Generic //$NON-NLS-1$
						// Actual type of a generic is supposed to be:
						// List<Integer>					=> Integer
						// List<List<Integer>>				=> List<Integer>
						// List<Map<Integer,String>>		=> Map<Integer,String>
						// Map<Integer,String>>				=> Integer
						// Something<Integer,Float,String>	=> Integer
						param= param.substring(param.indexOf(GENERIC_CLASS_OPEN_DIAMOND) + 1,
								param.lastIndexOf(GENERIC_CLASS_CLOSE_DIAMOND));
						if (!param.contains(GENERIC_CLASS_OPEN_DIAMOND) && param.contains(GENERIC_CLASS_SERPATOR)) {
							param= param.substring(0, param.indexOf(GENERIC_CLASS_SERPATOR));
						}
					} else {
						// The given parameter is already an actual type
					}

					String reference= jc.addImportGenericClass(param);
					mv.setValue(reference);
					mv.setUnambiguous(true);

					mv.setResolved(true);
					return;
				}
			}
		}
		super.resolve(variable, context);
	}
}
