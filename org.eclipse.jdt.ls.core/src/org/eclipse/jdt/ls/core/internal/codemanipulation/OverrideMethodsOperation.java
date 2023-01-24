/*******************************************************************************
 * Copyright (c) 2019-2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.codemanipulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

public class OverrideMethodsOperation {
	// For test purpose
	public static List<OverridableMethod> listOverridableMethods(IType type) {
		return listOverridableMethods(type, new NullProgressMonitor());
	}

	public static List<OverridableMethod> listOverridableMethods(IType type, IProgressMonitor monitor) {
		if (type == null || type.getCompilationUnit() == null) {
			return Collections.emptyList();
		}

		List<OverridableMethod> overridables = new ArrayList<>();
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
		if (astRoot == null) {
			return Collections.emptyList();
		}

		try {
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return overridables;
			}

			IMethodBinding cloneMethod = null;
			ITypeBinding cloneable = astRoot.getAST().resolveWellKnownType("java.lang.Cloneable");
			if (Bindings.isSuperType(cloneable, typeBinding)) {
				cloneMethod = resolveWellKnownCloneMethod(astRoot.getAST());
			}

			IPackageBinding pack = typeBinding.getPackage();
			IMethodBinding[] methods = StubUtility2Core.getOverridableMethods(astRoot.getAST(), typeBinding, false);
			for (IMethodBinding method : methods) {
				if (Bindings.isVisibleInHierarchy(method, pack)) {
					boolean toImplement = Objects.equals(method, cloneMethod);
					overridables.add(convertToSerializableMethod(method, toImplement));
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("List overridable methods", e);
		}

		return overridables;
	}

	public static TextEdit addOverridableMethods(IType type, OverridableMethod[] overridableMethods, IJavaElement insertPosition, IProgressMonitor monitor) {
		if (type == null || type.getCompilationUnit() == null || overridableMethods == null || overridableMethods.length == 0) {
			return null;
		}

		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
		if (astRoot == null) {
			return null;
		}

		try {
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return null;
			}

			IMethodBinding[] methodBindings = convertToMethodBindings(astRoot, typeBinding, overridableMethods);
			return createTextEditForOverridableMethods(type.getCompilationUnit(), astRoot, typeBinding, methodBindings, insertPosition);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Add overridable methods", e);
		}

		return null;
	}

	private static TextEdit createTextEditForOverridableMethods(ICompilationUnit cu, CompilationUnit astRoot, ITypeBinding typeBinding, IMethodBinding[] methodBindings, IJavaElement insertPosition) throws CoreException {
		ASTRewrite astRewrite = ASTRewrite.create(astRoot.getAST());
		ImportRewrite importRewrite = StubUtility.createImportRewrite(astRoot, true);
		ListRewrite listRewrite = null;
		ASTNode typeNode = astRoot.findDeclaringNode(typeBinding);
		if (typeNode instanceof AnonymousClassDeclaration) {
			listRewrite = astRewrite.getListRewrite(typeNode, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
		} else if (typeNode instanceof AbstractTypeDeclaration typeDeclaration) {
			listRewrite = astRewrite.getListRewrite(typeNode, typeDeclaration.getBodyDeclarationsProperty());
		} else {
			return null;
		}

		CodeGenerationSettings settings = PreferenceManager.getCodeGenerationSettings(cu);
		ImportRewriteContext context = new ContextSensitiveImportRewriteContext(astRoot, typeNode.getStartPosition(), importRewrite);
		ASTNode insertion = StubUtility2Core.getNodeToInsertBefore(listRewrite, insertPosition);
		for (IMethodBinding methodBinding : methodBindings) {
			MethodDeclaration stub = StubUtility2Core.createImplementationStubCore(cu, astRewrite, importRewrite, context, methodBinding, null, typeBinding, settings, typeBinding.isInterface(), !typeBinding.isInterface(), typeNode, false);
			if (stub == null) {
				continue;
			}

			if (insertion != null) {
				listRewrite.insertBefore(stub, insertion, null);
			} else {
				listRewrite.insertLast(stub, null);
			}
		}

		MultiTextEdit edit = new MultiTextEdit();
		edit.addChild(importRewrite.rewriteImports(null));
		edit.addChild(astRewrite.rewriteAST());
		return edit;
	}

	private static IMethodBinding resolveWellKnownCloneMethod(AST ast) {
		IMethodBinding[] objectMethods = ast.resolveWellKnownType("java.lang.Object").getDeclaredMethods();
		for (IMethodBinding method : objectMethods) {
			if (method.getName().equals("clone") && method.getParameterTypes().length == 0) {
				return method;
			}
		}

		return null;
	}

	private static IMethodBinding[] convertToMethodBindings(CompilationUnit astRoot, ITypeBinding typeBinding, OverridableMethod[] overridableMethods) {
		List<IMethodBinding> methodBindings = new ArrayList<>();
		Set<String> bindingKeys = Stream.of(overridableMethods).map((method) -> method.bindingKey).collect(Collectors.toSet());
		IMethodBinding[] methods = StubUtility2Core.getOverridableMethods(astRoot.getAST(), typeBinding, false);
		for (IMethodBinding method : methods) {
			if (bindingKeys.contains(method.getKey())) {
				methodBindings.add(method);
			}
		}

		return methodBindings.toArray(new IMethodBinding[0]);
	}

	private static OverridableMethod convertToSerializableMethod(IMethodBinding binding, boolean unimplemented) {
		OverridableMethod result = new OverridableMethod();
		result.bindingKey = binding.getKey();
		result.name = binding.getName();
		result.parameters = getMethodParameterTypes(binding, false);
		result.unimplemented = unimplemented || Modifier.isAbstract(binding.getModifiers());
		result.declaringClass = binding.getDeclaringClass().getQualifiedName();
		result.declaringClassType = binding.getDeclaringClass().isInterface() ? "interface" : "class";
		return result;
	}

	private static String[] getMethodParameterTypes(IMethodBinding binding, boolean qualifiedName) {
		List<String> parameterTypes = new ArrayList<>();
		for (ITypeBinding type : binding.getParameterTypes()) {
			if (qualifiedName) {
				parameterTypes.add(type.getQualifiedName());
			} else {
				parameterTypes.add(type.getName());
			}
		}

		return parameterTypes.toArray(new String[0]);
	}

	public static class OverridableMethod {
		public String bindingKey;
		public String name;
		public String[] parameters;
		public boolean unimplemented;
		public String declaringClass;
		public String declaringClassType;

		public OverridableMethod() {
		}

		public OverridableMethod(String bindingKey, String methodName, String[] methodParameters, boolean unimplemented, String declaringClass, String declaringClassType) {
			super();
			this.bindingKey = bindingKey;
			this.name = methodName;
			this.parameters = methodParameters;
			this.unimplemented = unimplemented;
			this.declaringClass = declaringClass;
			this.declaringClassType = declaringClassType;
		}
	}
}
