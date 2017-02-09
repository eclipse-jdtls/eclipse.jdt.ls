/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.hover;

import java.util.jar.Attributes.Name;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.osgi.util.NLS;

/**
 * Implementation of {@link JavaElementLabels}.
 *
 * @since 3.5
 */
public class JavaElementLabelComposer {

	/**
	 * An adapter for builder supported by the label composer.
	 */
	public static abstract class FlexibleBuilder {

		/**
		 * Appends the string representation of the given character to the builder.
		 *
		 * @param ch the character to append
		 * @return a reference to this object
		 */
		public abstract FlexibleBuilder append(char ch);

		/**
		 * Appends the given string to the builder.
		 *
		 * @param string the string to append
		 * @return a reference to this object
		 */
		public abstract FlexibleBuilder append(String string);

		/**
		 * Returns the length of the the builder.
		 *
		 * @return the length of the current string
		 */
		public abstract int length();
	}

	public static class FlexibleStringBuilder extends FlexibleBuilder {
		private final StringBuilder builder;

		public FlexibleStringBuilder(StringBuilder builder) {
			this.builder= builder;
		}

		@Override
		public FlexibleBuilder append(char ch) {
			builder.append(ch);
			return this;
		}

		@Override
		public FlexibleBuilder append(String string) {
			builder.append(string);
			return this;
		}

		@Override
		public int length() {
			return builder.length();
		}

		@Override
		public String toString() {
			return builder.toString();
		}
	}

	final static long QUALIFIER_FLAGS= JavaElementLabels.P_COMPRESSED | JavaElementLabels.USE_RESOLVED;

	/*
	 * Package name compression
	 */
	private static String fgPkgNamePrefix;
	private static String fgPkgNamePostfix;
	private static int fgPkgNameChars;
	private static int fgPkgNameLength= -1;

	protected final FlexibleBuilder fBuilder;

	protected static final boolean getFlag(long flags, long flag) {
		return (flags & flag) != 0;
	}

	/**
	 * Creates a new java element composer based on the given builder.
	 *
	 * @param builder the builder
	 */
	public JavaElementLabelComposer(FlexibleBuilder builder) {
		fBuilder= builder;
	}

	/**
	 * Creates a new java element composer based on the given builder.
	 *
	 * @param builder the builder
	 */
	public JavaElementLabelComposer(StringBuilder builder) {
		this(new FlexibleStringBuilder(builder));
	}

	/**
	 * Appends the label for a Java element with the flags as defined by this class.
	 *
	 * @param element the element to render
	 * @param flags the rendering flags.
	 */
	public void appendElementLabel(IJavaElement element, long flags) {
		int type= element.getElementType();
		IPackageFragmentRoot root= null;

		if (type != IJavaElement.JAVA_MODEL && type != IJavaElement.JAVA_PROJECT && type != IJavaElement.PACKAGE_FRAGMENT_ROOT)
			root= JavaModelUtil.getPackageFragmentRoot(element);
		if (root != null && getFlag(flags, JavaElementLabels.PREPEND_ROOT_PATH)) {
			appendPackageFragmentRootLabel(root, JavaElementLabels.ROOT_QUALIFIED);
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
		}

		switch (type) {
		case IJavaElement.METHOD:
			appendMethodLabel((IMethod) element, flags);
			break;
		case IJavaElement.FIELD:
			appendFieldLabel((IField) element, flags);
			break;
		case IJavaElement.LOCAL_VARIABLE:
			appendLocalVariableLabel((ILocalVariable) element, flags);
			break;
		case IJavaElement.TYPE_PARAMETER:
			appendTypeParameterLabel((ITypeParameter) element, flags);
			break;
		case IJavaElement.INITIALIZER:
			appendInitializerLabel((IInitializer) element, flags);
			break;
		case IJavaElement.TYPE:
			appendTypeLabel((IType) element, flags);
			break;
		case IJavaElement.CLASS_FILE:
			appendClassFileLabel((IClassFile) element, flags);
			break;
		case IJavaElement.COMPILATION_UNIT:
			appendCompilationUnitLabel((ICompilationUnit) element, flags);
			break;
		case IJavaElement.PACKAGE_FRAGMENT:
			appendPackageFragmentLabel((IPackageFragment) element, flags);
			break;
		case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			appendPackageFragmentRootLabel((IPackageFragmentRoot) element, flags);
			break;
		case IJavaElement.IMPORT_CONTAINER:
		case IJavaElement.IMPORT_DECLARATION:
		case IJavaElement.PACKAGE_DECLARATION:
			appendDeclarationLabel(element, flags);
			break;
		case IJavaElement.JAVA_PROJECT:
		case IJavaElement.JAVA_MODEL:
			fBuilder.append(element.getElementName());
			break;
		default:
			fBuilder.append(element.getElementName());
		}

		if (root != null && getFlag(flags, JavaElementLabels.APPEND_ROOT_PATH)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			appendPackageFragmentRootLabel(root, JavaElementLabels.ROOT_QUALIFIED);
		}
	}



	/**
	 * Appends the label for a method. Considers the M_* flags.
	 *
	 * @param method the element to render
	 * @param flags the rendering flags. Flags with names starting with 'M_' are considered.
	 */
	public void appendMethodLabel(IMethod method, long flags) {
		try {
			BindingKey resolvedKey= getFlag(flags, JavaElementLabels.USE_RESOLVED) && method.isResolved() ? new BindingKey(method.getKey()) : null;
			String resolvedSig= (resolvedKey != null) ? resolvedKey.toSignature() : null;

			// type parameters
			if (getFlag(flags, JavaElementLabels.M_PRE_TYPE_PARAMETERS)) {
				if (resolvedKey != null) {
					if (resolvedKey.isParameterizedMethod()) {
						String[] typeArgRefs= resolvedKey.getTypeArguments();
						if (typeArgRefs.length > 0) {
							appendTypeArgumentSignaturesLabel(method, typeArgRefs, flags);
							fBuilder.append(' ');
						}
					} else {
						String[] typeParameterSigs= Signature.getTypeParameters(resolvedSig);
						if (typeParameterSigs.length > 0) {
							appendTypeParameterSignaturesLabel(typeParameterSigs, flags);
							fBuilder.append(' ');
						}
					}
				} else if (method.exists()) {
					ITypeParameter[] typeParameters= method.getTypeParameters();
					if (typeParameters.length > 0) {
						appendTypeParametersLabels(typeParameters, flags);
						fBuilder.append(' ');
					}
				}
			}

			// return type
			if (getFlag(flags, JavaElementLabels.M_PRE_RETURNTYPE) && method.exists() && !method.isConstructor()) {
				String returnTypeSig= resolvedSig != null ? Signature.getReturnType(resolvedSig) : method.getReturnType();
				appendTypeSignatureLabel(method, returnTypeSig, flags);
				fBuilder.append(' ');
			}

			// qualification
			if (getFlag(flags, JavaElementLabels.M_FULLY_QUALIFIED)) {
				appendTypeLabel(method.getDeclaringType(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
				fBuilder.append('.');
			}

			fBuilder.append(getElementName(method));

			// constructor type arguments
			if (getFlag(flags, JavaElementLabels.T_TYPE_PARAMETERS) && method.exists() && method.isConstructor()) {
				if (resolvedSig != null && resolvedKey.isParameterizedType()) {
					BindingKey declaringType= resolvedKey.getDeclaringType();
					if (declaringType != null) {
						String[] declaringTypeArguments= declaringType.getTypeArguments();
						appendTypeArgumentSignaturesLabel(method, declaringTypeArguments, flags);
					}
				}
			}

			// parameters
			fBuilder.append('(');
			String[] declaredParameterTypes= method.getParameterTypes();
			if (getFlag(flags, JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_PARAMETER_NAMES)) {
				String[] types= null;
				int nParams= 0;
				boolean renderVarargs= false;
				boolean isPolymorphic= false;
				if (getFlag(flags, JavaElementLabels.M_PARAMETER_TYPES)) {
					if (resolvedSig != null) {
						types= Signature.getParameterTypes(resolvedSig);
					} else {
						types= declaredParameterTypes;
					}
					nParams= types.length;
					renderVarargs= method.exists() && Flags.isVarargs(method.getFlags());
					if (renderVarargs
							&& resolvedSig != null
							&& declaredParameterTypes.length == 1
							&& JavaModelUtil.isPolymorphicSignature(method)) {
						renderVarargs= false;
						isPolymorphic= true;
					}
				}
				String[] names= null;
				if (getFlag(flags, JavaElementLabels.M_PARAMETER_NAMES) && method.exists()) {
					names= method.getParameterNames();
					if (isPolymorphic) {
						// handled specially below
					} else	if (types == null) {
						nParams= names.length;
					} else { // types != null
						if (nParams != names.length) {
							if (resolvedSig != null && types.length > names.length) {
								// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=99137
								nParams= names.length;
								String[] typesWithoutSyntheticParams= new String[nParams];
								System.arraycopy(types, types.length - nParams, typesWithoutSyntheticParams, 0, nParams);
								types= typesWithoutSyntheticParams;
							} else {
								// https://bugs.eclipse.org/bugs/show_bug.cgi?id=101029
								// JavaPlugin.logErrorMessage("JavaElementLabels: Number of param types(" + nParams + ") != number of names(" + names.length + "): " + method.getElementName());   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
								names= null; // no names rendered
							}
						}
					}
				}

				ILocalVariable[] annotatedParameters= null;
				if (nParams > 0 && getFlag(flags, JavaElementLabels.M_PARAMETER_ANNOTATIONS)) {
					annotatedParameters= method.getParameters();
				}

				for (int i= 0; i < nParams; i++) {
					if (i > 0) {
						fBuilder.append(JavaElementLabels.COMMA_STRING);
					}
					if (annotatedParameters != null && i < annotatedParameters.length) {
						appendAnnotationLabels(annotatedParameters[i].getAnnotations(), flags);
					}

					if (types != null) {
						String paramSig= types[i];
						if (renderVarargs && (i == nParams - 1)) {
							int newDim= Signature.getArrayCount(paramSig) - 1;
							appendTypeSignatureLabel(method, Signature.getElementType(paramSig), flags);
							for (int k= 0; k < newDim; k++) {
								fBuilder.append('[').append(']');
							}
							fBuilder.append(JavaElementLabels.ELLIPSIS_STRING);
						} else {
							appendTypeSignatureLabel(method, paramSig, flags);
						}
					}
					if (names != null) {
						if (types != null) {
							fBuilder.append(' ');
						}
						if (isPolymorphic) {
							fBuilder.append(names[0] + i);
						} else {
							fBuilder.append(names[i]);
						}
					}
				}
			} else {
				if (declaredParameterTypes.length > 0) {
					fBuilder.append(JavaElementLabels.ELLIPSIS_STRING);
				}
			}
			fBuilder.append(')');

			if (getFlag(flags, JavaElementLabels.M_EXCEPTIONS)) {
				String[] types;
				if (resolvedKey != null) {
					types= resolvedKey.getThrownExceptions();
				} else {
					types= method.exists() ? method.getExceptionTypes() : new String[0];
				}
				if (types.length > 0) {
					fBuilder.append(" throws "); //$NON-NLS-1$
					for (int i= 0; i < types.length; i++) {
						if (i > 0) {
							fBuilder.append(JavaElementLabels.COMMA_STRING);
						}
						appendTypeSignatureLabel(method, types[i], flags);
					}
				}
			}


			if (getFlag(flags, JavaElementLabels.M_APP_TYPE_PARAMETERS)) {
				if (resolvedKey != null) {
					if (resolvedKey.isParameterizedMethod()) {
						String[] typeArgRefs= resolvedKey.getTypeArguments();
						if (typeArgRefs.length > 0) {
							fBuilder.append(' ');
							appendTypeArgumentSignaturesLabel(method, typeArgRefs, flags);
						}
					} else {
						String[] typeParameterSigs= Signature.getTypeParameters(resolvedSig);
						if (typeParameterSigs.length > 0) {
							fBuilder.append(' ');
							appendTypeParameterSignaturesLabel(typeParameterSigs, flags);
						}
					}
				} else if (method.exists()) {
					ITypeParameter[] typeParameters= method.getTypeParameters();
					if (typeParameters.length > 0) {
						fBuilder.append(' ');
						appendTypeParametersLabels(typeParameters, flags);
					}
				}
			}

			if (getFlag(flags, JavaElementLabels.M_APP_RETURNTYPE) && method.exists() && !method.isConstructor()) {
				fBuilder.append(JavaElementLabels.DECL_STRING);
				String returnTypeSig= resolvedSig != null ? Signature.getReturnType(resolvedSig) : method.getReturnType();
				appendTypeSignatureLabel(method, returnTypeSig, flags);
			}

			// post qualification
			if (getFlag(flags, JavaElementLabels.M_POST_QUALIFIED)) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				appendTypeLabel(method.getDeclaringType(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
			}

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("", e); // NotExistsException will not reach this point
		}
	}

	protected void appendAnnotationLabels(IAnnotation[] annotations, long flags) throws JavaModelException {
		for (int j= 0; j < annotations.length; j++) {
			IAnnotation annotation= annotations[j];
			appendAnnotationLabel(annotation, flags);
			fBuilder.append(' ');
		}
	}

	public void appendAnnotationLabel(IAnnotation annotation, long flags) throws JavaModelException {
		fBuilder.append('@');
		appendTypeSignatureLabel(annotation, Signature.createTypeSignature(annotation.getElementName(), false), flags);
		IMemberValuePair[] memberValuePairs= annotation.getMemberValuePairs();
		if (memberValuePairs.length == 0)
			return;
		fBuilder.append('(');
		for (int i= 0; i < memberValuePairs.length; i++) {
			if (i > 0)
				fBuilder.append(JavaElementLabels.COMMA_STRING);
			IMemberValuePair memberValuePair= memberValuePairs[i];
			fBuilder.append(getMemberName(annotation, annotation.getElementName(), memberValuePair.getMemberName()));
			fBuilder.append('=');
			appendAnnotationValue(annotation, memberValuePair.getValue(), memberValuePair.getValueKind(), flags);
		}
		fBuilder.append(')');
	}

	private void appendAnnotationValue(IAnnotation annotation, Object value, int valueKind, long flags) throws JavaModelException {
		// Note: To be bug-compatible with Javadoc from Java 5/6/7, we currently don't escape HTML tags in String-valued annotations.
		if (value instanceof Object[]) {
			fBuilder.append('{');
			Object[] values= (Object[]) value;
			for (int j= 0; j < values.length; j++) {
				if (j > 0)
					fBuilder.append(JavaElementLabels.COMMA_STRING);
				value= values[j];
				appendAnnotationValue(annotation, value, valueKind, flags);
			}
			fBuilder.append('}');
		} else {
			switch (valueKind) {
			case IMemberValuePair.K_CLASS:
				appendTypeSignatureLabel(annotation, Signature.createTypeSignature((String) value, false), flags);
				fBuilder.append(".class"); //$NON-NLS-1$
				break;
			case IMemberValuePair.K_QUALIFIED_NAME:
				String name = (String) value;
				int lastDot= name.lastIndexOf('.');
				if (lastDot != -1) {
					String type= name.substring(0, lastDot);
					String field= name.substring(lastDot + 1);
					appendTypeSignatureLabel(annotation, Signature.createTypeSignature(type, false), flags);
					fBuilder.append('.');
					fBuilder.append(getMemberName(annotation, type, field));
					break;
				}
				//				case IMemberValuePair.K_SIMPLE_NAME: // can't implement, since parent type is not known
				//$FALL-THROUGH$
			case IMemberValuePair.K_ANNOTATION:
				appendAnnotationLabel((IAnnotation) value, flags);
				break;
			case IMemberValuePair.K_STRING:
				fBuilder.append(ASTNodes.getEscapedStringLiteral((String) value));
				break;
			case IMemberValuePair.K_CHAR:
				fBuilder.append(ASTNodes.getEscapedCharacterLiteral(((Character) value).charValue()));
				break;
			default:
				fBuilder.append(String.valueOf(value));
				break;
			}
		}
	}

	/**
	 * Appends labels for type parameters from type binding array.
	 *
	 * @param typeParameters the type parameters
	 * @param flags flags with render options
	 * @throws JavaModelException ...
	 */
	private void appendTypeParametersLabels(ITypeParameter[] typeParameters, long flags) throws JavaModelException {
		if (typeParameters.length > 0) {
			fBuilder.append(getLT());
			for (int i = 0; i < typeParameters.length; i++) {
				if (i > 0) {
					fBuilder.append(JavaElementLabels.COMMA_STRING);
				}
				appendTypeParameterWithBounds(typeParameters[i], flags);
			}
			fBuilder.append(getGT());
		}
	}

	/**
	 * Appends the style label for a field. Considers the F_* flags.
	 *
	 * @param field the element to render
	 * @param flags the rendering flags. Flags with names starting with 'F_' are considered.
	 */
	public void appendFieldLabel(IField field, long flags) {
		try {

			if (getFlag(flags, JavaElementLabels.F_PRE_TYPE_SIGNATURE) && field.exists() && !Flags.isEnum(field.getFlags())) {
				if (getFlag(flags, JavaElementLabels.USE_RESOLVED) && field.isResolved()) {
					appendTypeSignatureLabel(field, new BindingKey(field.getKey()).toSignature(), flags);
				} else {
					appendTypeSignatureLabel(field, field.getTypeSignature(), flags);
				}
				fBuilder.append(' ');
			}

			// qualification
			if (getFlag(flags, JavaElementLabels.F_FULLY_QUALIFIED)) {
				appendTypeLabel(field.getDeclaringType(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
				fBuilder.append('.');
			}
			fBuilder.append(getElementName(field));

			if (getFlag(flags, JavaElementLabels.F_APP_TYPE_SIGNATURE) && field.exists() && !Flags.isEnum(field.getFlags())) {
				fBuilder.append(JavaElementLabels.DECL_STRING);
				if (getFlag(flags, JavaElementLabels.USE_RESOLVED) && field.isResolved()) {
					appendTypeSignatureLabel(field, new BindingKey(field.getKey()).toSignature(), flags);
				} else {
					appendTypeSignatureLabel(field, field.getTypeSignature(), flags);
				}
			}

			// post qualification
			if (getFlag(flags, JavaElementLabels.F_POST_QUALIFIED)) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				appendTypeLabel(field.getDeclaringType(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
			}

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("", e); // NotExistsException will not reach this point
		}
	}

	/**
	 * Appends the styled label for a local variable.
	 *
	 * @param localVariable the element to render
	 * @param flags the rendering flags. Flags with names starting with 'F_' are considered.
	 */
	public void appendLocalVariableLabel(ILocalVariable localVariable, long flags) {
		if (getFlag(flags, JavaElementLabels.F_PRE_TYPE_SIGNATURE)) {
			appendTypeSignatureLabel(localVariable, localVariable.getTypeSignature(), flags);
			fBuilder.append(' ');
		}

		if (getFlag(flags, JavaElementLabels.F_FULLY_QUALIFIED)) {
			appendElementLabel(localVariable.getDeclaringMember(), JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
			fBuilder.append('.');
		}

		fBuilder.append(getElementName(localVariable));

		if (getFlag(flags, JavaElementLabels.F_APP_TYPE_SIGNATURE)) {
			fBuilder.append(JavaElementLabels.DECL_STRING);
			appendTypeSignatureLabel(localVariable, localVariable.getTypeSignature(), flags);
		}

		// post qualification
		if (getFlag(flags, JavaElementLabels.F_POST_QUALIFIED)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			appendElementLabel(localVariable.getDeclaringMember(), JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
		}
	}

	/**
	 * Appends the styled label for a type parameter.
	 *
	 * @param typeParameter the element to render
	 * @param flags the rendering flags. Flags with names starting with 'T_' are considered.
	 */
	public void appendTypeParameterLabel(ITypeParameter typeParameter, long flags) {
		try {
			appendTypeParameterWithBounds(typeParameter, flags);

			// post qualification
			if (getFlag(flags, JavaElementLabels.TP_POST_QUALIFIED)) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				IMember declaringMember= typeParameter.getDeclaringMember();
				appendElementLabel(declaringMember, JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_FULLY_QUALIFIED | JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
			}

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("", e); // NotExistsException will not reach this point
		}
	}

	private void appendTypeParameterWithBounds(ITypeParameter typeParameter, long flags) throws JavaModelException {
		fBuilder.append(getElementName(typeParameter));

		if (typeParameter.exists()) {
			String[] bounds= typeParameter.getBoundsSignatures();
			if (bounds.length > 0 &&
					! (bounds.length == 1 && "Ljava.lang.Object;".equals(bounds[0]))) { //$NON-NLS-1$
				fBuilder.append(" extends "); //$NON-NLS-1$
				for (int j= 0; j < bounds.length; j++) {
					if (j > 0) {
						fBuilder.append(" & "); //$NON-NLS-1$
					}
					appendTypeSignatureLabel(typeParameter, bounds[j], flags);
				}
			}
		}
	}

	/**
	 * Appends the label for a initializer. Considers the I_* flags.
	 *
	 * @param initializer the element to render
	 * @param flags the rendering flags. Flags with names starting with 'I_' are considered.
	 */
	public void appendInitializerLabel(IInitializer initializer, long flags) {
		// qualification
		if (getFlag(flags, JavaElementLabels.I_FULLY_QUALIFIED)) {
			appendTypeLabel(initializer.getDeclaringType(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
			fBuilder.append('.');
		}
		fBuilder.append("{...}");

		// post qualification
		if (getFlag(flags, JavaElementLabels.I_POST_QUALIFIED)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			appendTypeLabel(initializer.getDeclaringType(), JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
		}
	}

	protected void appendTypeSignatureLabel(IJavaElement enclosingElement, String typeSig, long flags) {
		int sigKind= Signature.getTypeSignatureKind(typeSig);
		switch (sigKind) {
		case Signature.BASE_TYPE_SIGNATURE:
			fBuilder.append(Signature.toString(typeSig));
			break;
		case Signature.ARRAY_TYPE_SIGNATURE:
			appendTypeSignatureLabel(enclosingElement, Signature.getElementType(typeSig), flags);
			for (int dim= Signature.getArrayCount(typeSig); dim > 0; dim--) {
				fBuilder.append('[').append(']');
			}
			break;
		case Signature.CLASS_TYPE_SIGNATURE:
			String baseType= getSimpleTypeName(enclosingElement, typeSig);
			fBuilder.append(baseType);

			String[] typeArguments= Signature.getTypeArguments(typeSig);
			appendTypeArgumentSignaturesLabel(enclosingElement, typeArguments, flags);
			break;
		case Signature.TYPE_VARIABLE_SIGNATURE:
			fBuilder.append(getSimpleTypeName(enclosingElement, typeSig));
			break;
		case Signature.WILDCARD_TYPE_SIGNATURE:
			char ch= typeSig.charAt(0);
			if (ch == Signature.C_STAR) { //workaround for bug 85713
				fBuilder.append('?');
			} else {
				if (ch == Signature.C_EXTENDS) {
					fBuilder.append("? extends "); //$NON-NLS-1$
					appendTypeSignatureLabel(enclosingElement, typeSig.substring(1), flags);
				} else if (ch == Signature.C_SUPER) {
					fBuilder.append("? super "); //$NON-NLS-1$
					appendTypeSignatureLabel(enclosingElement, typeSig.substring(1), flags);
				}
			}
			break;
		case Signature.CAPTURE_TYPE_SIGNATURE:
			appendTypeSignatureLabel(enclosingElement, typeSig.substring(1), flags);
			break;
		case Signature.INTERSECTION_TYPE_SIGNATURE:
			String[] typeBounds= Signature.getIntersectionTypeBounds(typeSig);
			appendTypeBoundsSignaturesLabel(enclosingElement, typeBounds, flags);
			break;
		default:
			// unknown
		}
	}

	/**
	 * Returns the simple name of the given type signature.
	 *
	 * @param enclosingElement the enclosing element in which to resolve the signature
	 * @param typeSig a {@link Signature#CLASS_TYPE_SIGNATURE} or {@link Signature#TYPE_VARIABLE_SIGNATURE}
	 * @return the simple name of the given type signature
	 */
	protected String getSimpleTypeName(IJavaElement enclosingElement, String typeSig) {
		return Signature.getSimpleName(Signature.toString(Signature.getTypeErasure(typeSig)));
	}

	/**
	 * Returns the simple name of the given member.
	 *
	 * @param enclosingElement the enclosing element
	 * @param typeName the name of the member's declaring type
	 * @param memberName the name of the member
	 * @return the simple name of the member
	 */
	protected String getMemberName(IJavaElement enclosingElement, String typeName, String memberName) {
		return memberName;
	}

	private void appendTypeArgumentSignaturesLabel(IJavaElement enclosingElement, String[] typeArgsSig, long flags) {
		if (typeArgsSig.length > 0) {
			fBuilder.append(getLT());
			for (int i = 0; i < typeArgsSig.length; i++) {
				if (i > 0) {
					fBuilder.append(JavaElementLabels.COMMA_STRING);
				}
				appendTypeSignatureLabel(enclosingElement, typeArgsSig[i], flags);
			}
			fBuilder.append(getGT());
		}
	}

	private void appendTypeBoundsSignaturesLabel(IJavaElement enclosingElement, String[] typeArgsSig, long flags) {
		for (int i = 0; i < typeArgsSig.length; i++) {
			if (i > 0) {
				fBuilder.append(" | "); //$NON-NLS-1$
			}
			appendTypeSignatureLabel(enclosingElement, typeArgsSig[i], flags);
		}
	}

	/**
	 * Appends labels for type parameters from a signature.
	 *
	 * @param typeParamSigs the type parameter signature
	 * @param flags flags with render options
	 */
	private void appendTypeParameterSignaturesLabel(String[] typeParamSigs, long flags) {
		if (typeParamSigs.length > 0) {
			fBuilder.append(getLT());
			for (int i = 0; i < typeParamSigs.length; i++) {
				if (i > 0) {
					fBuilder.append(JavaElementLabels.COMMA_STRING);
				}
				fBuilder.append(Signature.getTypeVariable(typeParamSigs[i]));
			}
			fBuilder.append(getGT());
		}
	}

	/**
	 * Returns the string for rendering the '<code>&lt;</code>' character.
	 *
	 * @return the string for rendering '<code>&lt;</code>'
	 */
	protected String getLT() {
		return "<"; //$NON-NLS-1$
	}

	/**
	 * Returns the string for rendering the '<code>&gt;</code>' character.
	 *
	 * @return the string for rendering '<code>&gt;</code>'
	 */
	protected String getGT() {
		return ">"; //$NON-NLS-1$
	}

	/**
	 * Appends the label for a type. Considers the T_* flags.
	 *
	 * @param type the element to render
	 * @param flags the rendering flags. Flags with names starting with 'T_' are considered.
	 */
	public void appendTypeLabel(IType type, long flags) {

		if (getFlag(flags, JavaElementLabels.T_FULLY_QUALIFIED)) {
			IPackageFragment pack= type.getPackageFragment();
			if (!pack.isDefaultPackage()) {
				appendPackageFragmentLabel(pack, (flags & QUALIFIER_FLAGS));
				fBuilder.append('.');
			}
		}
		IJavaElement parent= type.getParent();
		if (getFlag(flags, JavaElementLabels.T_FULLY_QUALIFIED | JavaElementLabels.T_CONTAINER_QUALIFIED)) {
			IType declaringType= type.getDeclaringType();
			if (declaringType != null) {
				appendTypeLabel(declaringType, JavaElementLabels.T_CONTAINER_QUALIFIED | (flags & QUALIFIER_FLAGS));
				fBuilder.append('.');
			}
			int parentType= parent.getElementType();
			if (parentType == IJavaElement.METHOD || parentType == IJavaElement.FIELD || parentType == IJavaElement.INITIALIZER) { // anonymous or local
				appendElementLabel(parent, 0);
				fBuilder.append('.');
			}
		}

		String typeName;
		boolean isAnonymous= false;
		if (type.isLambda()) {
			typeName= "() -> {...}"; //$NON-NLS-1$
			try {
				String[] superInterfaceSignatures= type.getSuperInterfaceTypeSignatures();
				if (superInterfaceSignatures.length > 0) {
					typeName= typeName + ' ' + getSimpleTypeName(type, superInterfaceSignatures[0]);
				}
			} catch (JavaModelException e) {
				//ignore
			}

		} else {
			typeName= getElementName(type);
			try {
				isAnonymous= type.isAnonymous();
			} catch (JavaModelException e1) {
				// should not happen, but let's play safe:
				isAnonymous= typeName.length() == 0;
			}
			if (isAnonymous) {
				try {
					if (parent instanceof IField && type.isEnum()) {
						typeName= '{' + JavaElementLabels.ELLIPSIS_STRING + '}';
					} else {
						String supertypeName= null;
						String[] superInterfaceSignatures= type.getSuperInterfaceTypeSignatures();
						if (superInterfaceSignatures.length > 0) {
							supertypeName= getSimpleTypeName(type, superInterfaceSignatures[0]);
						} else {
							String supertypeSignature= type.getSuperclassTypeSignature();
							if (supertypeSignature != null) {
								supertypeName= getSimpleTypeName(type, supertypeSignature);
							}
						}
						if (supertypeName == null) {
							typeName= "new Anonymous";
						} else {
							typeName= NLS.bind("new {0}() '{'...}", supertypeName);
						}
					}
				} catch (JavaModelException e) {
					//ignore
					typeName= "new Anonymous";
				}
			}
		}
		fBuilder.append(typeName);

		if (getFlag(flags, JavaElementLabels.T_TYPE_PARAMETERS)) {
			if (getFlag(flags, JavaElementLabels.USE_RESOLVED) && type.isResolved()) {
				BindingKey key= new BindingKey(type.getKey());
				if (key.isParameterizedType()) {
					String[] typeArguments= key.getTypeArguments();
					appendTypeArgumentSignaturesLabel(type, typeArguments, flags);
				} else {
					String[] typeParameters= Signature.getTypeParameters(key.toSignature());
					appendTypeParameterSignaturesLabel(typeParameters, flags);
				}
			} else if (type.exists()) {
				try {
					appendTypeParametersLabels(type.getTypeParameters(), flags);
				} catch (JavaModelException e) {
					// ignore
				}
			}
		}

		// post qualification
		if (getFlag(flags, JavaElementLabels.T_POST_QUALIFIED)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			IType declaringType= type.getDeclaringType();
			if (declaringType == null && type.isBinary() && isAnonymous) {
				// workaround for Bug 87165: [model] IType#getDeclaringType() does not work for anonymous binary type
				String tqn= type.getTypeQualifiedName();
				int lastDollar= tqn.lastIndexOf('$');
				if (lastDollar != 1) {
					String declaringTypeCF= tqn.substring(0, lastDollar) + ".class"; //$NON-NLS-1$
					declaringType= type.getPackageFragment().getClassFile(declaringTypeCF).getType();
					try {
						ISourceRange typeSourceRange= type.getSourceRange();
						if (declaringType.exists() && SourceRange.isAvailable(typeSourceRange)) {
							IJavaElement realParent= declaringType.getTypeRoot().getElementAt(typeSourceRange.getOffset() - 1);
							if (realParent != null) {
								parent= realParent;
							}
						}
					} catch (JavaModelException e) {
						// ignore
					}
				}
			}
			if (declaringType != null) {
				appendTypeLabel(declaringType, JavaElementLabels.T_FULLY_QUALIFIED | (flags & QUALIFIER_FLAGS));
				int parentType= parent.getElementType();
				if (parentType == IJavaElement.METHOD || parentType == IJavaElement.FIELD || parentType == IJavaElement.INITIALIZER) { // anonymous or local
					fBuilder.append('.');
					appendElementLabel(parent, 0);
				}
			} else {
				appendPackageFragmentLabel(type.getPackageFragment(), flags & QUALIFIER_FLAGS);
			}
		}
	}

	/**
	 * Returns the string for rendering the {@link IJavaElement#getElementName() element name} of
	 * the given element.
	 * <p>
	 * <strong>Note:</strong> This class only calls this helper for those elements where (
	 * {@link JavaElementLinks}) has the need to render the name differently.
	 * </p>
	 *
	 * @param element the element to render
	 * @return the string for rendering the element name
	 */
	protected String getElementName(IJavaElement element) {
		return element.getElementName();
	}

	/**
	 * Appends the label for a import container, import or package declaration. Considers the D_* flags.
	 *
	 * @param declaration the element to render
	 * @param flags the rendering flags. Flags with names starting with 'D_' are considered.
	 */
	public void appendDeclarationLabel(IJavaElement declaration, long flags) {
		if (getFlag(flags, JavaElementLabels.D_QUALIFIED)) {
			IJavaElement openable= (IJavaElement) declaration.getOpenable();
			if (openable != null) {
				appendElementLabel(openable, JavaElementLabels.CF_QUALIFIED | JavaElementLabels.CU_QUALIFIED | (flags & QUALIFIER_FLAGS));
				fBuilder.append('/');
			}
		}
		if (declaration.getElementType() == IJavaElement.IMPORT_CONTAINER) {
			fBuilder.append("import declarations");
		} else {
			fBuilder.append(getElementName(declaration));
		}
		// post qualification
		if (getFlag(flags, JavaElementLabels.D_POST_QUALIFIED)) {
			IJavaElement openable= (IJavaElement) declaration.getOpenable();
			if (openable != null) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				appendElementLabel(openable, JavaElementLabels.CF_QUALIFIED | JavaElementLabels.CU_QUALIFIED | (flags & QUALIFIER_FLAGS));
			}
		}
	}

	/**
	 * Appends the label for a class file. Considers the CF_* flags.
	 *
	 * @param classFile the element to render
	 * @param flags the rendering flags. Flags with names starting with 'CF_' are considered.
	 */
	public void appendClassFileLabel(IClassFile classFile, long flags) {
		if (getFlag(flags, JavaElementLabels.CF_QUALIFIED)) {
			IPackageFragment pack= (IPackageFragment) classFile.getParent();
			if (!pack.isDefaultPackage()) {
				appendPackageFragmentLabel(pack, (flags & QUALIFIER_FLAGS));
				fBuilder.append('.');
			}
		}
		fBuilder.append(classFile.getElementName());

		if (getFlag(flags, JavaElementLabels.CF_POST_QUALIFIED)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			appendPackageFragmentLabel((IPackageFragment) classFile.getParent(), flags & QUALIFIER_FLAGS);
		}
	}

	/**
	 * Appends the label for a compilation unit. Considers the CU_* flags.
	 *
	 * @param cu the element to render
	 * @param flags the rendering flags. Flags with names starting with 'CU_' are considered.
	 */
	public void appendCompilationUnitLabel(ICompilationUnit cu, long flags) {
		if (getFlag(flags, JavaElementLabels.CU_QUALIFIED)) {
			IPackageFragment pack= (IPackageFragment) cu.getParent();
			if (!pack.isDefaultPackage()) {
				appendPackageFragmentLabel(pack, (flags & QUALIFIER_FLAGS));
				fBuilder.append('.');
			}
		}
		fBuilder.append(cu.getElementName());

		if (getFlag(flags, JavaElementLabels.CU_POST_QUALIFIED)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			appendPackageFragmentLabel((IPackageFragment) cu.getParent(), flags & QUALIFIER_FLAGS);
		}
	}

	/**
	 * Appends the label for a package fragment. Considers the P_* flags.
	 *
	 * @param pack the element to render
	 * @param flags the rendering flags. Flags with names starting with P_' are considered.
	 */
	public void appendPackageFragmentLabel(IPackageFragment pack, long flags) {
		if (getFlag(flags, JavaElementLabels.P_QUALIFIED)) {
			appendPackageFragmentRootLabel((IPackageFragmentRoot) pack.getParent(), JavaElementLabels.ROOT_QUALIFIED);
			fBuilder.append('/');
		}
		if (pack.isDefaultPackage()) {
			fBuilder.append(JavaElementLabels.DEFAULT_PACKAGE);
		} else if (getFlag(flags, JavaElementLabels.P_COMPRESSED)) {
			appendCompressedPackageFragment(pack);
		} else {
			fBuilder.append(getElementName(pack));
		}
		if (getFlag(flags, JavaElementLabels.P_POST_QUALIFIED)) {
			fBuilder.append(JavaElementLabels.CONCAT_STRING);
			appendPackageFragmentRootLabel((IPackageFragmentRoot) pack.getParent(), JavaElementLabels.ROOT_QUALIFIED);
		}
	}

	private void appendCompressedPackageFragment(IPackageFragment pack) {
		appendCompressedPackageFragment(pack.getElementName());
	}

	private void appendCompressedPackageFragment(String elementName) {
		if (fgPkgNameLength < 0) {
			fBuilder.append(elementName);
			return;
		}
		String name= elementName;
		int start= 0;
		int dot= name.indexOf('.', start);
		while (dot > 0) {
			if (dot - start > fgPkgNameLength-1) {
				fBuilder.append(fgPkgNamePrefix);
				if (fgPkgNameChars > 0)
					fBuilder.append(name.substring(start, Math.min(start+ fgPkgNameChars, dot)));
				fBuilder.append(fgPkgNamePostfix);
			} else
				fBuilder.append(name.substring(start, dot + 1));
			start= dot + 1;
			dot= name.indexOf('.', start);
		}
		fBuilder.append(name.substring(start));
	}

	/**
	 * Appends the label for a package fragment root. Considers the ROOT_* flags.
	 *
	 * @param root the element to render
	 * @param flags the rendering flags. Flags with names starting with ROOT_' are considered.
	 */
	public void appendPackageFragmentRootLabel(IPackageFragmentRoot root, long flags) {
		// Handle variables different
		if (getFlag(flags, JavaElementLabels.ROOT_VARIABLE) && appendVariableLabel(root, flags))
			return;
		if (root.isArchive())
			appendArchiveLabel(root, flags);
		else
			appendFolderLabel(root, flags);
	}

	private void appendArchiveLabel(IPackageFragmentRoot root, long flags) {
		boolean external= root.isExternal();
		if (external)
			appendExternalArchiveLabel(root, flags);
		else
			appendInternalArchiveLabel(root, flags);
	}

	private boolean appendVariableLabel(IPackageFragmentRoot root, long flags) {
		try {
			IClasspathEntry rawEntry= root.getRawClasspathEntry();
			if (rawEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				IClasspathEntry entry= JavaModelUtil.getClasspathEntry(root);
				if (entry.getReferencingEntry() != null) {
					return false; // not the variable entry itself, but a referenced entry
				}
				IPath path= rawEntry.getPath().makeRelative();

				if (getFlag(flags, JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED)) {
					int segements= path.segmentCount();
					if (segements > 0) {
						fBuilder.append(path.segment(segements - 1));
						if (segements > 1) {
							fBuilder.append(JavaElementLabels.CONCAT_STRING);
							fBuilder.append(path.removeLastSegments(1).toOSString());
						}
					} else {
						fBuilder.append(path.toString());
					}
				} else {
					fBuilder.append(path.toString());
				}
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				if (root.isExternal())
					fBuilder.append(root.getPath().toOSString());
				else
					fBuilder.append(root.getPath().makeRelative().toString());

				return true;
			}
		} catch (JavaModelException e) {
			// problems with class path, ignore (bug 202792)
			return false;
		}
		return false;
	}

	private void appendExternalArchiveLabel(IPackageFragmentRoot root, long flags) {
		IPath path;
		IClasspathEntry classpathEntry= null;
		try {
			classpathEntry= JavaModelUtil.getClasspathEntry(root);
			IPath rawPath= classpathEntry.getPath();
			if (classpathEntry.getEntryKind() != IClasspathEntry.CPE_CONTAINER && !rawPath.isAbsolute())
				path= rawPath;
			else
				path= root.getPath();
		} catch (JavaModelException e) {
			path= root.getPath();
		}
		if (getFlag(flags, JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED)) {
			int segments= path.segmentCount();
			if (segments > 0) {
				fBuilder.append(path.segment(segments - 1));
				if (segments > 1 || path.getDevice() != null) {
					fBuilder.append(JavaElementLabels.CONCAT_STRING);
					fBuilder.append(path.removeLastSegments(1).toOSString());
				}
				if (classpathEntry != null) {
					IClasspathEntry referencingEntry= classpathEntry.getReferencingEntry();
					if (referencingEntry != null) {
						fBuilder.append(NLS.bind(" (from {0} of {1})", new Object[] { Name.CLASS_PATH.toString(), referencingEntry.getPath().lastSegment() }));
					}
				}
			} else {
				fBuilder.append(path.toOSString());
			}
		} else {
			fBuilder.append(path.toOSString());
		}
	}

	private void appendInternalArchiveLabel(IPackageFragmentRoot root, long flags) {
		IResource resource= root.getResource();
		boolean rootQualified= getFlag(flags, JavaElementLabels.ROOT_QUALIFIED);
		if (rootQualified) {
			fBuilder.append(root.getPath().makeRelative().toString());
		} else {
			fBuilder.append(root.getElementName());
			boolean referencedPostQualified= getFlag(flags, JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED);
			if (referencedPostQualified && isReferenced(root)) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				fBuilder.append(resource.getParent().getFullPath().makeRelative().toString());
			} else if (getFlag(flags, JavaElementLabels.ROOT_POST_QUALIFIED)) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				fBuilder.append(root.getParent().getPath().makeRelative().toString());
			}
			if (referencedPostQualified) {
				try {
					IClasspathEntry referencingEntry= JavaModelUtil.getClasspathEntry(root).getReferencingEntry();
					if (referencingEntry != null) {
						fBuilder.append(NLS.bind(" (from {0} of {1})", new Object[] { Name.CLASS_PATH.toString(), referencingEntry.getPath().lastSegment() }));
					}
				} catch (JavaModelException e) {
					// ignore
				}
			}
		}
	}

	private void appendFolderLabel(IPackageFragmentRoot root, long flags) {
		IResource resource= root.getResource();
		if (resource == null) {
			appendExternalArchiveLabel(root, flags);
			return;
		}

		boolean rootQualified= getFlag(flags, JavaElementLabels.ROOT_QUALIFIED);
		boolean referencedQualified= getFlag(flags, JavaElementLabels.REFERENCED_ROOT_POST_QUALIFIED) && isReferenced(root);
		if (rootQualified) {
			fBuilder.append(root.getPath().makeRelative().toString());
		} else {
			IPath projectRelativePath= resource.getProjectRelativePath();
			if (projectRelativePath.segmentCount() == 0) {
				fBuilder.append(resource.getName());
				referencedQualified= false;
			} else {
				fBuilder.append(projectRelativePath.toString());
			}

			if (referencedQualified) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				fBuilder.append(resource.getProject().getName());
			} else if (getFlag(flags, JavaElementLabels.ROOT_POST_QUALIFIED)) {
				fBuilder.append(JavaElementLabels.CONCAT_STRING);
				fBuilder.append(root.getParent().getElementName());
			} else {
				return;
			}
		}
	}

	/**
	 * Returns <code>true</code> if the given package fragment root is
	 * referenced. This means it is a descendant of a different project but is referenced
	 * by the root's parent. Returns <code>false</code> if the given root
	 * doesn't have an underlying resource.
	 *
	 * @param root the package fragment root
	 * @return returns <code>true</code> if the given package fragment root is referenced
	 */
	private boolean isReferenced(IPackageFragmentRoot root) {
		IResource resource= root.getResource();
		if (resource != null) {
			IProject jarProject= resource.getProject();
			IProject container= root.getJavaProject().getProject();
			return !container.equals(jarProject);
		}
		return false;
	}
}
