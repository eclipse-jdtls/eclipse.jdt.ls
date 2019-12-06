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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;

public class JdtDomModels {

	public static class LspVariableBinding {
		public String bindingKey;
		public String name;
		public String type;
		public boolean isField;

		public LspVariableBinding(IVariableBinding binding) {
			this.bindingKey = binding.getKey();
			this.name = binding.getName();
			this.type = binding.getType().getName();
			this.isField = binding.isField();
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

	public static IVariableBinding[] convertToVariableBindings(ITypeBinding typeBinding, LspVariableBinding[] fields) {
		Set<String> bindingKeys = Stream.of(fields).map((field) -> field.bindingKey).collect(Collectors.toSet());
		return Arrays.stream(typeBinding.getDeclaredFields()).filter(f -> bindingKeys.contains(f.getKey())).toArray(IVariableBinding[]::new);
	}

	public static LspVariableBinding[] getDeclaredFields(ITypeBinding typeBinding, boolean includeStatic) {
		return Arrays.stream(typeBinding.getDeclaredFields()).filter(f -> includeStatic || !Modifier.isStatic(f.getModifiers())).map(f -> new LspVariableBinding(f)).toArray(LspVariableBinding[]::new);
	}
}
