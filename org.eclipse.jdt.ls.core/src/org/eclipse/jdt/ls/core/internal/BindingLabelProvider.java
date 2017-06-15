/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/viewsupport/BindingLabelProvider.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;


import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;


/**
 * Label provider to render bindings in viewers.
 */
public class BindingLabelProvider {



	private static void getFieldLabel(IVariableBinding binding, long flags, StringBuffer buffer) {
		if (((flags & JavaElementLabels.F_PRE_TYPE_SIGNATURE) != 0) && !binding.isEnumConstant()) {
			getTypeLabel(binding.getType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
			buffer.append(' ');
		}
		// qualification

		if ((flags & JavaElementLabels.F_FULLY_QUALIFIED) != 0) {
			ITypeBinding declaringClass= binding.getDeclaringClass();
			if (declaringClass != null) { // test for array.length
				getTypeLabel(declaringClass, JavaElementLabels.T_FULLY_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
				buffer.append('.');
			}
		}
		buffer.append(binding.getName());
		if (((flags & JavaElementLabels.F_APP_TYPE_SIGNATURE) != 0) && !binding.isEnumConstant()) {
			buffer.append(JavaElementLabels.DECL_STRING);
			getTypeLabel(binding.getType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
		}
		// post qualification
		if ((flags & JavaElementLabels.F_POST_QUALIFIED) != 0) {
			ITypeBinding declaringClass= binding.getDeclaringClass();
			if (declaringClass != null) { // test for array.length
				buffer.append(JavaElementLabels.CONCAT_STRING);
				getTypeLabel(declaringClass, JavaElementLabels.T_FULLY_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
			}
		}
	}

	private static void getLocalVariableLabel(IVariableBinding binding, long flags, StringBuffer buffer) {
		if (((flags & JavaElementLabels.F_PRE_TYPE_SIGNATURE) != 0)) {
			getTypeLabel(binding.getType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
			buffer.append(' ');
		}
		if (((flags & JavaElementLabels.F_FULLY_QUALIFIED) != 0)) {
			IMethodBinding declaringMethod= binding.getDeclaringMethod();
			if (declaringMethod != null) {
				getMethodLabel(declaringMethod, flags, buffer);
				buffer.append('.');
			}
		}
		buffer.append(binding.getName());
		if (((flags & JavaElementLabels.F_APP_TYPE_SIGNATURE) != 0)) {
			buffer.append(JavaElementLabels.DECL_STRING);
			getTypeLabel(binding.getType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
		}
	}

	private static void appendDimensions(int dim, StringBuffer buffer) {
		for (int i=0 ; i < dim; i++) {
			buffer.append('[').append(']');
		}
	}


	private static void getMethodLabel(IMethodBinding binding, long flags, StringBuffer buffer) {
		// return type
		if ((flags & JavaElementLabels.M_PRE_TYPE_PARAMETERS) != 0) {
			if (binding.isGenericMethod()) {
				ITypeBinding[] typeParameters= binding.getTypeParameters();
				if (typeParameters.length > 0) {
					getTypeParametersLabel(typeParameters, buffer);
					buffer.append(' ');
				}
			}
		}
		// return type
		if (((flags & JavaElementLabels.M_PRE_RETURNTYPE) != 0) && !binding.isConstructor()) {
			getTypeLabel(binding.getReturnType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
			buffer.append(' ');
		}
		// qualification
		if ((flags & JavaElementLabels.M_FULLY_QUALIFIED) != 0) {
			getTypeLabel(binding.getDeclaringClass(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
			buffer.append('.');
		}
		buffer.append(binding.getName());
		if ((flags & JavaElementLabels.M_APP_TYPE_PARAMETERS) != 0) {
			if (binding.isParameterizedMethod()) {
				ITypeBinding[] typeArguments= binding.getTypeArguments();
				if (typeArguments.length > 0) {
					buffer.append(' ');
					getTypeArgumentsLabel(typeArguments, (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
				}
			}
		}


		// parameters
		buffer.append('(');
		if ((flags & JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_PARAMETER_NAMES) != 0) {
			ITypeBinding[] parameters= ((flags & JavaElementLabels.M_PARAMETER_TYPES) != 0) ? binding.getParameterTypes() : null;
			if (parameters != null) {
				for (int index= 0; index < parameters.length; index++) {
					if (index > 0) {
						buffer.append(JavaElementLabels.COMMA_STRING);
					}
					ITypeBinding paramType= parameters[index];
					if (binding.isVarargs() && (index == parameters.length - 1)) {
						getTypeLabel(paramType.getElementType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
						appendDimensions(paramType.getDimensions() - 1, buffer);
						buffer.append(JavaElementLabels.ELLIPSIS_STRING);
					} else {
						getTypeLabel(paramType, (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
					}
				}
			}
		} else {
			if (binding.getParameterTypes().length > 0) {
				buffer.append(JavaElementLabels.ELLIPSIS_STRING);
			}
		}
		buffer.append(')');

		if ((flags & JavaElementLabels.M_EXCEPTIONS) != 0) {
			ITypeBinding[] exceptions= binding.getExceptionTypes();
			if (exceptions.length > 0) {
				buffer.append(" throws "); //$NON-NLS-1$
				for (int index= 0; index < exceptions.length; index++) {
					if (index > 0) {
						buffer.append(JavaElementLabels.COMMA_STRING);
					}
					getTypeLabel(exceptions[index], (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
				}
			}
		}
		if ((flags & JavaElementLabels.M_APP_TYPE_PARAMETERS) != 0) {
			if (binding.isGenericMethod()) {
				ITypeBinding[] typeParameters= binding.getTypeParameters();
				if (typeParameters.length > 0) {
					buffer.append(' ');
					getTypeParametersLabel(typeParameters, buffer);
				}
			}
		}
		if (((flags & JavaElementLabels.M_APP_RETURNTYPE) != 0) && !binding.isConstructor()) {
			buffer.append(JavaElementLabels.DECL_STRING);
			getTypeLabel(binding.getReturnType(), (flags & JavaElementLabels.T_TYPE_PARAMETERS), buffer);
		}
		// post qualification
		if ((flags & JavaElementLabels.M_POST_QUALIFIED) != 0) {
			buffer.append(JavaElementLabels.CONCAT_STRING);
			getTypeLabel(binding.getDeclaringClass(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
		}
	}


	private static void getTypeLabel(ITypeBinding binding, long flags, StringBuffer buffer) {
		if ((flags & JavaElementLabels.T_FULLY_QUALIFIED) != 0) {
			final IPackageBinding pack= binding.getPackage();
			if (pack != null && !pack.isUnnamed()) {
				buffer.append(pack.getName());
				buffer.append('.');
			}
		}
		if ((flags & (JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.T_CONTAINER_QUALIFIED)) != 0) {
			final ITypeBinding declaring= binding.getDeclaringClass();
			if (declaring != null) {
				getTypeLabel(declaring, JavaElementLabels.T_CONTAINER_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
				buffer.append('.');
			}
			final IMethodBinding declaringMethod= binding.getDeclaringMethod();
			if (declaringMethod != null) {
				getMethodLabel(declaringMethod, 0, buffer);
				buffer.append('.');
			}
		}

		if (binding.isCapture()) {
			getTypeLabel(binding.getWildcard(), flags & JavaElementLabels.T_TYPE_PARAMETERS, buffer);
		} else if (binding.isWildcardType()) {
			buffer.append('?');
			ITypeBinding bound= binding.getBound();
			if (bound != null) {
				if (binding.isUpperbound()) {
					buffer.append(" extends "); //$NON-NLS-1$
				} else {
					buffer.append(" super "); //$NON-NLS-1$
				}
				getTypeLabel(bound, flags & JavaElementLabels.T_TYPE_PARAMETERS, buffer);
			}
		} else if (binding.isArray()) {
			getTypeLabel(binding.getElementType(), flags & JavaElementLabels.T_TYPE_PARAMETERS, buffer);
			appendDimensions(binding.getDimensions(), buffer);
		} else { // type variables, primitive, reftype
			String name= binding.getTypeDeclaration().getName();
			if (name.length() == 0) {
				if (binding.isEnum()) {
					buffer.append('{' + JavaElementLabels.ELLIPSIS_STRING + '}');
				} else if (binding.isAnonymous()) {
					ITypeBinding[] superInterfaces= binding.getInterfaces();
					ITypeBinding baseType;
					if (superInterfaces.length > 0) {
						baseType= superInterfaces[0];
					} else {
						baseType= binding.getSuperclass();
					}
					if (baseType != null) {
						StringBuffer anonymBaseType= new StringBuffer();
						getTypeLabel(baseType, flags & JavaElementLabels.T_TYPE_PARAMETERS, anonymBaseType);
						buffer.append(Messages.format("new {0}() '{'...}", anonymBaseType.toString()));
					} else {
						buffer.append("new Anonymous");
					}
				} else {
					buffer.append("UNKNOWN"); //$NON-NLS-1$
				}
			} else {
				buffer.append(name);
			}

			if ((flags & JavaElementLabels.T_TYPE_PARAMETERS) != 0) {
				if (binding.isGenericType()) {
					getTypeParametersLabel(binding.getTypeParameters(), buffer);
				} else if (binding.isParameterizedType()) {
					getTypeArgumentsLabel(binding.getTypeArguments(), flags, buffer);
				}
			}
		}


		if ((flags & JavaElementLabels.T_POST_QUALIFIED) != 0) {
			final IMethodBinding declaringMethod= binding.getDeclaringMethod();
			final ITypeBinding declaringType= binding.getDeclaringClass();
			if (declaringMethod != null) {
				buffer.append(JavaElementLabels.CONCAT_STRING);
				getMethodLabel(declaringMethod, JavaElementLabels.T_FULLY_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
			} else if (declaringType != null) {
				buffer.append(JavaElementLabels.CONCAT_STRING);
				getTypeLabel(declaringType, JavaElementLabels.T_FULLY_QUALIFIED | (flags & JavaElementLabels.P_COMPRESSED), buffer);
			} else {
				final IPackageBinding pack= binding.getPackage();
				if (pack != null && !pack.isUnnamed()) {
					buffer.append(JavaElementLabels.CONCAT_STRING);
					buffer.append(pack.getName());
				}
			}
		}
	}

	private static void getTypeArgumentsLabel(ITypeBinding[] typeArgs, long flags, StringBuffer buf) {
		if (typeArgs.length > 0) {
			buf.append('<');
			for (int i = 0; i < typeArgs.length; i++) {
				if (i > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				getTypeLabel(typeArgs[i], flags & JavaElementLabels.T_TYPE_PARAMETERS, buf);
			}
			buf.append('>');
		}
	}


	private static void getTypeParametersLabel(ITypeBinding[] typeParameters, StringBuffer buffer) {
		if (typeParameters.length > 0) {
			buffer.append('<');
			for (int index= 0; index < typeParameters.length; index++) {
				if (index > 0) {
					buffer.append(JavaElementLabels.COMMA_STRING);
				}
				buffer.append(typeParameters[index].getName());
			}
			buffer.append('>');
		}
	}

	/**
	 * Returns the label for a Java element with the flags as defined by {@link JavaElementLabels}.
	 * @param binding The binding to render.
	 * @param flags The text flags as defined in {@link JavaElementLabels}
	 * @return the label of the binding
	 */
	public static String getBindingLabel(IBinding binding, long flags) {
		StringBuffer buffer= new StringBuffer(60);
		if (binding instanceof ITypeBinding) {
			getTypeLabel(((ITypeBinding) binding), flags, buffer);
		} else if (binding instanceof IMethodBinding) {
			getMethodLabel(((IMethodBinding) binding), flags, buffer);
		} else if (binding instanceof IVariableBinding) {
			final IVariableBinding variable= (IVariableBinding) binding;
			if (variable.isField()) {
				getFieldLabel(variable, flags, buffer);
			} else {
				getLocalVariableLabel(variable, flags, buffer);
			}
		}
		return Strings.markLTR(buffer.toString());
	}


	public static final long DEFAULT_TEXTFLAGS= JavaElementLabels.ALL_DEFAULT;
}
