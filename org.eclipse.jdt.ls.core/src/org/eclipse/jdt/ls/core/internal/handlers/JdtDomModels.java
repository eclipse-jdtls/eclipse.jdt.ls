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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.ls.core.internal.JDTUtils;

public class JdtDomModels {

	public static class LspVariableBinding {
		public String bindingKey;
		public String name;
		public String type;
		public boolean isField;
		public boolean isSelected;
		public String[] parameters;

		public LspVariableBinding(IVariableBinding binding) {
			this(binding, false);
		}

		public LspVariableBinding(IVariableBinding binding, boolean isSelected) {
			this.bindingKey = binding.getKey();
			this.name = binding.getName();
			this.type = binding.getType().getName();
			this.isField = binding.isField();
			this.isSelected = isSelected;
		}

		public LspVariableBinding(IMethodBinding binding) {
			this(binding, false);
		}

		public LspVariableBinding(IMethodBinding binding, boolean isSelected) {
			this.bindingKey = binding.getKey();
			this.name = binding.getName();
			this.type = binding.getReturnType().getName();
			this.isField = false;
			this.parameters = Stream.of(binding.getParameterTypes()).map(type -> type.getName()).toArray(String[]::new);
			this.isSelected = isSelected;
		}
	}

	public static class LspMethodBinding {
		public String bindingKey;
		public String name;
		public String[] parameters;

		public LspMethodBinding(IMethodBinding binding) {
			this.bindingKey = binding.getKey();
			this.name = binding.getName();
			this.parameters = Stream.of(binding.getParameterTypes()).map(type -> type.getName()).toArray(String[]::new);
		}
	}

	public static IBinding[] convertToBindings(ITypeBinding typeBinding, LspVariableBinding[] fields) {
		Set<String> bindingKeys = Stream.of(fields).map((field) -> field.bindingKey).collect(Collectors.toSet());
		IVariableBinding[] bindings = typeBinding.getDeclaredFields();
		List<IVariableBinding> fieldsToBindings = new ArrayList<>();
		for (IVariableBinding variableBinding : bindings) {
			if (!Modifier.isStatic(variableBinding.getModifiers())) {
				fieldsToBindings.add(variableBinding);
			}
		}
		ITypeBinding superTypeBinding = typeBinding;
		while ((superTypeBinding = superTypeBinding.getSuperclass()) != null) {
			for (IVariableBinding candidateField : superTypeBinding.getDeclaredFields()) {
				if (!Modifier.isPrivate(candidateField.getModifiers()) && !Modifier.isStatic(candidateField.getModifiers()) && !contains(fieldsToBindings, candidateField)) {
					fieldsToBindings.add(candidateField);
				}
			}
		}
		List<IMethodBinding> methodsToBindings = new ArrayList<>();
		for (IMethodBinding candidateMethod : typeBinding.getDeclaredMethods()) {
			if (!Modifier.isStatic(candidateMethod.getModifiers()) && candidateMethod.getParameterTypes().length == 0 && !"void".equals(candidateMethod.getReturnType().getName()) && !"toString".equals(candidateMethod.getName()) //$NON-NLS-1$//$NON-NLS-2$
					&& !"clone".equals(candidateMethod.getName())) { //$NON-NLS-1$
				methodsToBindings.add(candidateMethod);
			}
		}
		superTypeBinding = typeBinding;
		while ((superTypeBinding = superTypeBinding.getSuperclass()) != null) {
			for (IMethodBinding candidateMethod : superTypeBinding.getDeclaredMethods()) {
				if (!Modifier.isPrivate(candidateMethod.getModifiers()) && !Modifier.isStatic(candidateMethod.getModifiers()) && candidateMethod.getParameterTypes().length == 0 && !"void".equals(candidateMethod.getReturnType().getName()) //$NON-NLS-1$
						&& !JdtDomModels.contains(methodsToBindings, candidateMethod) && !"clone".equals(candidateMethod.getName())) { //$NON-NLS-1$
					methodsToBindings.add(candidateMethod);
				}
			}
		}
		IBinding[] fieldsMembers = fieldsToBindings.stream().sorted(new BindingComparator()).filter(f -> bindingKeys.contains(f.getKey())).toArray(IVariableBinding[]::new);
		IBinding[] methodsMembers = methodsToBindings.stream().sorted(new BindingComparator()).filter(f -> bindingKeys.contains(f.getKey())).toArray(IMethodBinding[]::new);
		IBinding[] result;
		if (methodsMembers.length == 0) {
			result = fieldsMembers;
		} else if (fieldsMembers.length == 0) {
			result = methodsMembers;
		} else {
			result = new IBinding[fieldsMembers.length + methodsMembers.length];
			System.arraycopy(fieldsMembers, 0, result, 0, fieldsMembers.length);
			System.arraycopy(methodsMembers, 0, result, fieldsMembers.length, methodsMembers.length);
		}
		return result;
	}

	public static IVariableBinding[] convertToVariableBindings(ITypeBinding typeBinding, LspVariableBinding[] fields) {
		Set<String> bindingKeys = Stream.of(fields).map((field) -> field.bindingKey).collect(Collectors.toSet());
		IVariableBinding[] bindings = typeBinding.getDeclaredFields();
		List<IVariableBinding> members = new ArrayList<>();
		for (IVariableBinding variableBinding : bindings) {
			if (!Modifier.isStatic(variableBinding.getModifiers())) {
				members.add(variableBinding);
			}
		}
		return members.stream().sorted(new BindingComparator()).filter(f -> bindingKeys.contains(f.getKey())).toArray(IVariableBinding[]::new);
	}

	public static <T extends IBinding> boolean contains(List<T> inheritedFields, T member) {
		for (T object : inheritedFields) {
			if (object instanceof IVariableBinding && member instanceof IVariableBinding) {
				if (((IVariableBinding) object).getName().equals(((IVariableBinding) member).getName())) {
					return true;
				}
			}
			if (object instanceof IMethodBinding && member instanceof IMethodBinding) {
				if (((IMethodBinding) object).getName().equals(((IMethodBinding) member).getName())) {
					return true;
				}
			}
		}
		return false;
	}

	public static LspVariableBinding[] getDeclaredFields(ITypeBinding typeBinding, boolean includeStatic) {
		return Arrays.stream(typeBinding.getDeclaredFields()).sorted(new BindingComparator()).filter(f -> includeStatic || !Modifier.isStatic(f.getModifiers())).map(f -> new LspVariableBinding(f)).toArray(LspVariableBinding[]::new);
	}

	public static class BindingComparator implements Comparator<IBinding> {
		@Override
		public int compare(IBinding a, IBinding b) {
			try {
				ISourceRange nameRangeA = a.getJavaElement() == null ? null : JDTUtils.getNameRange(a.getJavaElement());
				ISourceRange nameRangeB = b.getJavaElement() == null ? null : JDTUtils.getNameRange(b.getJavaElement());
				if (nameRangeA != null && nameRangeB != null) {
					return nameRangeA.getOffset() - nameRangeB.getOffset();
				} else {
					return 0;
				}
			} catch (JavaModelException e) {
				return 0;
			}
		}
	}

}
