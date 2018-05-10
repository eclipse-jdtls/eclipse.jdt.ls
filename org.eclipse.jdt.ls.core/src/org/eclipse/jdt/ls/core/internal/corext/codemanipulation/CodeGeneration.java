/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/ui/CodeGeneration.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     John Kaplan, johnkaplantech@gmail.com - 108071 [code templates] template for body of newly created class
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.codemanipulation;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;


/**
 * Class that offers access to the templates contained in the 'code templates' preference page.
 *
 * <p>
 * This class is not intended to be subclassed or instantiated by clients.
 * </p>
 *
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class CodeGeneration {

	private static final String[] EMPTY= new String[0];

	private CodeGeneration() {
	}

	/**
	 * Returns the content for a new type comment using the 'type comment' code
	 * template. The returned content is unformatted and is not indented.
	 *
	 * @param cu
	 *            The compilation unit where the type is contained. The
	 *            compilation unit does not need to exist.
	 * @param typeQualifiedName
	 *            The name of the type to which the comment is added. For inner
	 *            types the name must be qualified and include the outer types
	 *            names (dot separated). See
	 *            {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param typeParameterNames
	 *            The type parameter names
	 * @param lineDelimiter
	 *            The line delimiter to be used.
	 * @return Returns the new content or <code>null</code> if the code template
	 *         is undefined or empty. The returned content is unformatted and is
	 *         not indented.
	 * @throws CoreException
	 *             Thrown when the evaluation of the code template fails.
	 * @since 3.1
	 */
	public static String getTypeComment(ICompilationUnit cu, String typeQualifiedName, String[] typeParameterNames,
			String lineDelimiter) throws CoreException {
		return StubUtility.getTypeComment(cu, typeQualifiedName, typeParameterNames, lineDelimiter);
	}

	/**
	 * Returns the content for a new field comment using the 'field comment'
	 * code template. The returned content is unformatted and is not indented.
	 *
	 * @param cu
	 *            The compilation unit where the field is contained. The
	 *            compilation unit does not need to exist.
	 * @param typeName
	 *            The name of the field declared type.
	 * @param fieldName
	 *            The name of the field to which the comment is added.
	 * @param lineDelimiter
	 *            The line delimiter to be used.
	 * @return Returns the new content or <code>null</code> if the code template
	 *         is undefined or empty. The returned content is unformatted and is
	 *         not indented.
	 * @throws CoreException
	 *             Thrown when the evaluation of the code template fails.
	 */
	public static String getFieldComment(ICompilationUnit cu, String typeName, String fieldName, String lineDelimiter) throws CoreException {
		return StubUtility.getFieldComment(cu, typeName, fieldName, lineDelimiter);
	}

	/**
	 * Returns the comment for a method or constructor using the comment code templates (constructor / method / overriding method).
	 * <code>null</code> is returned if the template is empty.
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param decl The MethodDeclaration AST node that will be added as new
	 * method. The node does not need to exist in an AST (no parent needed) and does not need to resolve.
	 * See {@link org.eclipse.jdt.core.dom.AST#newMethodDeclaration()} for how to create such a node.
	 * @param overridden The binding of the method to which to add an "@see" link or
	 * <code>null</code> if no link should be created.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the generated method comment or <code>null</code> if the
	 * code template is empty. The returned content is unformatted and not indented (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 */
	public static String getMethodComment(ICompilationUnit cu, String declaringTypeName, MethodDeclaration decl, IMethodBinding overridden, String lineDelimiter) throws CoreException {
		if (overridden != null) {
			overridden= overridden.getMethodDeclaration();
			String declaringClassQualifiedName= overridden.getDeclaringClass().getQualifiedName();
			String linkToMethodName= overridden.getName();
			String[] parameterTypesQualifiedNames= StubUtility.getParameterTypeNamesForSeeTag(overridden);
			return StubUtility.getMethodComment(cu, declaringTypeName, decl, overridden.isDeprecated(), linkToMethodName, declaringClassQualifiedName, parameterTypesQualifiedNames, false, lineDelimiter);
		} else {
			return StubUtility.getMethodComment(cu, declaringTypeName, decl, false, null, null, null, false, lineDelimiter);
		}
	}

	/**
	 * Returns the content of the body for a method or constructor using the method
	 * body templates. <code>null</code> is returned if the template is empty.
	 * <p>
	 * The returned string is unformatted and not indented.
	 *
	 * @param cu
	 *            The compilation unit to which the method belongs. The compilation
	 *            unit does not need to exist.
	 * @param declaringTypeName
	 *            Name of the type to which the method belongs. For inner types the
	 *            name must be qualified and include the outer types names (dot
	 *            separated). See
	 *            {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName
	 *            Name of the method.
	 * @param isConstructor
	 *            Defines if the created body is for a constructor.
	 * @param bodyStatement
	 *            The code to be entered at the place of the variable
	 *            ${body_statement}.
	 * @param lineDelimiter
	 *            The line delimiter to be used.
	 * @return Returns the constructed body content or <code>null</code> if the
	 *         comment code template is empty. The returned string is unformatted
	 *         and and has no indent (formatting required).
	 * @throws CoreException
	 *             Thrown when the evaluation of the code template fails.
	 */
	public static String getMethodBodyContent(ICompilationUnit cu, String declaringTypeName, String methodName, boolean isConstructor, String bodyStatement, String lineDelimiter) throws CoreException {
		return StubUtility.getMethodBodyContent(isConstructor, cu.getJavaProject(), declaringTypeName, methodName, bodyStatement, lineDelimiter);
	}

	/**
	 * Returns the comment for a method or constructor using the comment code templates (constructor / method / overriding method).
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 * <p>Exception types and return type are in signature notation. e.g. a source method declared as <code>public void foo(String text, int length)</code>
	 * would return the array <code>{"QString;","I"}</code> as parameter types. See {@link org.eclipse.jdt.core.Signature}.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName Name of the method.
	 * @param paramNames Names of the parameters for the method.
	 * @param excTypeSig Thrown exceptions (Signature notation).
	 * @param retTypeSig Return type (Signature notation) or <code>null</code>
	 * for constructors.
	 * @param overridden The method that will be overridden by the created method or
	 * <code>null</code> for non-overriding methods. If not <code>null</code>, the method must exist.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the constructed comment or <code>null</code> if
	 * the comment code template is empty. The returned content is unformatted and not indented (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 */
	public static String getMethodComment(ICompilationUnit cu, String declaringTypeName, String methodName, String[] paramNames, String[] excTypeSig, String retTypeSig, IMethod overridden, String lineDelimiter) throws CoreException {
		return StubUtility.getMethodComment(cu, declaringTypeName, methodName, paramNames, excTypeSig, retTypeSig, EMPTY, overridden, false, lineDelimiter);
	}

	/**
	 * Returns the comment for a method or constructor using the comment code templates (constructor / method / overriding method).
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 * <p>Exception types and return type are in signature notation. e.g. a source method declared as <code>public void foo(String text, int length)</code>
	 * would return the array <code>{"QString;","I"}</code> as parameter types. See {@link org.eclipse.jdt.core.Signature}.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName Name of the method.
	 * @param paramNames Names of the parameters for the method.
	 * @param excTypeSig Thrown exceptions (Signature notation).
	 * @param retTypeSig Return type (Signature notation) or <code>null</code>
	 * for constructors.
	 * @param typeParameterNames Names of the type parameters for the method.
	 * @param overridden The method that will be overridden by the created method or
	 * <code>null</code> for non-overriding methods. If not <code>null</code>, the method must exist.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the constructed comment or <code>null</code> if
	 * the comment code template is empty. The returned content is unformatted and not indented (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 */
	public static String getMethodComment(ICompilationUnit cu, String declaringTypeName, String methodName, String[] paramNames, String[] excTypeSig, String retTypeSig, String[] typeParameterNames, IMethod overridden, String lineDelimiter) throws CoreException {
		return StubUtility.getMethodComment(cu, declaringTypeName, methodName, paramNames, excTypeSig, retTypeSig, typeParameterNames, overridden, false, lineDelimiter);
	}

	/**
	 * Returns the comment for a method or constructor using the comment code templates (constructor / method / overriding method).
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 *
	 * @param method The method to be documented. The method must exist.
	 * @param overridden The method that will be overridden by the created method or
	 * <code>null</code> for non-overriding methods. If not <code>null</code>, the method must exist.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the constructed comment or <code>null</code> if
	 * the comment code template is empty. The returned string is unformatted and and has no indent (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 */
	public static String getMethodComment(IMethod method, IMethod overridden, String lineDelimiter) throws CoreException {
		String retType= method.isConstructor() ? null : method.getReturnType();
		String[] paramNames= method.getParameterNames();
		String[] typeParameterNames= StubUtility.shouldGenerateMethodTypeParameterTags(method.getJavaProject()) ? StubUtility.getTypeParameterNames(method.getTypeParameters()) : new String[0];

		return StubUtility.getMethodComment(method.getCompilationUnit(), method.getDeclaringType().getElementName(),
				method.getElementName(), paramNames, method.getExceptionTypes(), retType, typeParameterNames, overridden, false, lineDelimiter);
	}

	/**
	 * Returns the comment for a method or constructor using the comment code templates (constructor / method / overriding method).
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.

	 * @param decl The MethodDeclaration AST node that will be added as new
	 * method. The node does not need to exist in an AST (no parent needed) and does not need to resolve.
	 * See {@link org.eclipse.jdt.core.dom.AST#newMethodDeclaration()} for how to create such a node.
	 * @param isDeprecated If set, the method is deprecated
	 * @param overriddenMethodName If a method is overridden, the simple name of the overridden method, or <code>null</code> if no method is overridden.
	 * @param overriddenMethodDeclaringTypeName If a method is overridden, the fully qualified type name of the overridden method's declaring type,
	 * or <code>null</code> if no method is overridden.
	 * @param overriddenMethodParameterTypeNames If a method is overridden, the fully qualified parameter type names of the overridden method,
	 * or <code>null</code> if no method is overridden.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the constructed comment or <code>null</code> if
	 * the comment code template is empty. The returned string is unformatted and and has no indent (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 */
	public static String getMethodComment(ICompilationUnit cu, String declaringTypeName, MethodDeclaration decl, boolean isDeprecated, String overriddenMethodName, String overriddenMethodDeclaringTypeName, String[] overriddenMethodParameterTypeNames, String lineDelimiter) throws CoreException {
		return StubUtility.getMethodComment(cu, declaringTypeName, decl, isDeprecated, overriddenMethodName, overriddenMethodDeclaringTypeName, overriddenMethodParameterTypeNames, false, lineDelimiter);
	}

	/**
	 * Returns the content of body for a getter method using the getter method body template.
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName The name of the getter method.
	 * @param fieldName The name of the field to get in the getter method, corresponding to the template variable for ${field}.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the constructed body content or <code>null</code> if
	 * the comment code template is empty. The returned string is unformatted and and has no indent (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 * @since 3.0
	 */
	public static String getGetterMethodBodyContent(ICompilationUnit cu, String declaringTypeName, String methodName, String fieldName, String lineDelimiter) throws CoreException {
		return StubUtility.getGetterMethodBodyContent(cu.getJavaProject(), declaringTypeName, methodName, fieldName, lineDelimiter);
	}

	/**
	 * Returns the content of body for a setter method using the setter method body template.
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName The name of the setter method.
	 * @param fieldName The name of the field to be set in the setter method, corresponding to the template variable for ${field}.
	 * @param paramName The name of the parameter passed to the setter method, corresponding to the template variable for $(param).
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the constructed body content or <code>null</code> if
	 * the comment code template is empty. The returned string is unformatted and and has no indent (formatting required).
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 * @since 3.0
	 */
	public static String getSetterMethodBodyContent(ICompilationUnit cu, String declaringTypeName, String methodName, String fieldName, String paramName, String lineDelimiter) throws CoreException {
		return StubUtility.getSetterMethodBodyContent(cu.getJavaProject(), declaringTypeName, methodName, fieldName, paramName, lineDelimiter);
	}

	/**
	 * Returns the comment for a getter method using the getter comment template.
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName Name of the method.
	 * @param fieldName Name of the field to get.
	 * @param fieldType The type of the field to get.
	 * @param bareFieldName The field name without prefix or suffix.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the generated getter comment or <code>null</code> if the
	 * code template is empty. The returned content is not indented.
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 * @since 3.0
	 */
	public static String getGetterComment(ICompilationUnit cu, String declaringTypeName, String methodName, String fieldName, String fieldType, String bareFieldName, String lineDelimiter) throws CoreException {
		return StubUtility.getGetterComment(cu, declaringTypeName, methodName, fieldName, fieldType, bareFieldName, lineDelimiter);
	}

	/**
	 * Returns the comment for a setter method using the setter method body template.
	 * <code>null</code> is returned if the template is empty.
	 * <p>The returned string is unformatted and not indented.
	 *
	 * @param cu The compilation unit to which the method belongs. The compilation unit does not need to exist.
	 * @param declaringTypeName Name of the type to which the method belongs. For inner types the name must be qualified and include the outer
	 * types names (dot separated). See {@link org.eclipse.jdt.core.IType#getTypeQualifiedName(char)}.
	 * @param methodName Name of the method.
	 * @param fieldName Name of the field that is set.
	 * @param fieldType The type of the field that is to set.
	 * @param paramName The name of the parameter that used to set.
	 * @param bareFieldName The field name without prefix or suffix.
	 * @param lineDelimiter The line delimiter to be used.
	 * @return Returns the generated setter comment or <code>null</code> if the
	 * code template is empty. The returned comment is not indented.
	 * @throws CoreException Thrown when the evaluation of the code template fails.
	 * @since 3.0
	 */
	public static String getSetterComment(ICompilationUnit cu, String declaringTypeName, String methodName, String fieldName, String fieldType, String paramName, String bareFieldName, String lineDelimiter) throws CoreException {
		return StubUtility.getSetterComment(cu, declaringTypeName, methodName, fieldName, fieldType, paramName, bareFieldName, lineDelimiter);
	}
}
