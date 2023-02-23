/**
 * Copyright (c) 2016-2018 TypeFox and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.lsp4j.legacy.typeHierarchy;

import org.eclipse.lsp4j.DynamicRegistrationCapabilities;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

import com.google.common.annotations.Beta;

/**
 * Capabilities specific to the {@code textDocument/typeHierarchy}.
 *
 * <p>
 * <b>Note:</b> the <a href=
 * "https://github.com/Microsoft/vscode-languageserver-node/pull/426">{@code textDocument/typeHierarchy}
 * language feature</a> is not yet part of the official LSP specification.
 */
@Beta
@SuppressWarnings("all")
public class TypeHierarchyCapabilities extends DynamicRegistrationCapabilities {
  public TypeHierarchyCapabilities() {
  }

  public TypeHierarchyCapabilities(final Boolean dynamicRegistration) {
    super(dynamicRegistration);
  }

  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add("dynamicRegistration", getDynamicRegistration());
    return b.toString();
  }

  @Override
  @Pure
  public boolean equals(final Object obj) {
    if (this == obj) {
		return true;
	}
    if (obj == null) {
		return false;
	}
    if (getClass() != obj.getClass()) {
		return false;
	}
    if (!super.equals(obj)) {
		return false;
	}
    return true;
  }

  @Override
  @Pure
  public int hashCode() {
    return super.hashCode();
  }
}
