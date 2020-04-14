/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.semantictokens;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.Modifier;

public class TokenModifiers {
    private ITokenModifier[] modifiers;
    private Map<ITokenModifier, Integer> modifierIndices;

    public TokenModifiers() {
        modifiers = new ITokenModifier[] {
            new StaticModifier(),
            new FinalModifier(),
            new DeprecatedModifier(),
            new PublicModifier(),
            new PrivateModifier(),
            new ProtectedModifier(),
            new AbstractModifier()
        };
        modifierIndices = new HashMap<>();
        for (int i = 0; i < modifiers.length; i++) {
            modifierIndices.putIfAbsent(modifiers[i], i);
        }
    }

    public Set<ITokenModifier> values() {
        return modifierIndices.keySet();
    }

    public List<ITokenModifier> list() {
        return Arrays.asList(modifiers);
    }

    public int indexOf(ITokenModifier modifier) {
        return modifierIndices.getOrDefault(modifier, -1);
    }

    class StaticModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            int flags = binding.getModifiers();
            if ((flags & Modifier.STATIC) != 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "static";
        }
    }

    class FinalModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            int flags = binding.getModifiers();
            if ((flags & Modifier.FINAL) != 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "final";
        }
    }

    class DeprecatedModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            if (binding != null) {
                return binding.isDeprecated();
            }
            return false;
        }

        @Override
        public String toString() {
            return "deprecated";
        }
    }

    class PublicModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            int flags = binding.getModifiers();
            if ((flags & Modifier.PUBLIC) != 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "public";
        }
    }

    class PrivateModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            int flags = binding.getModifiers();
            if ((flags & Modifier.PRIVATE) != 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "private";
        }
    }

    class ProtectedModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            int flags = binding.getModifiers();
            if ((flags & Modifier.PROTECTED) != 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "protected";
        }
    }

    class AbstractModifier implements ITokenModifier {
        @Override
        public boolean applies(IBinding binding) {
            int flags = binding.getModifiers();
            if ((flags & Modifier.ABSTRACT) != 0) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "abstract";
        }
    }
}
