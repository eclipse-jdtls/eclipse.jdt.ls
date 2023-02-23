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

import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

import com.google.common.annotations.Beta;

/**
 * The type hierarchy request is sent from the client resolve a {@link TypeHierarchyItem type hierarchy item} for
 * a give cursor location in the text document. The request would also allow to specify if the item should be resolved
 * and whether sub- or supertypes are to be resolved.
 */
@Beta
@SuppressWarnings("all")
public class TypeHierarchyParams extends TextDocumentPositionParams {
  /**
   * The number of hierarchy levels to resolve. {@code 0} indicates no hierarchy level. It defaults to {@code 0}.
   */
  private int resolve;

  /**
   * The direction of the type hierarchy resolution. If not defined, defaults to {@link TypeHierarchyDirection#Children Children}.
   */
  private TypeHierarchyDirection direction;

  /**
   * The number of hierarchy levels to resolve. {@code 0} indicates no hierarchy level. It defaults to {@code 0}.
   */
  @Pure
  public int getResolve() {
    return this.resolve;
  }

  /**
   * The number of hierarchy levels to resolve. {@code 0} indicates no hierarchy level. It defaults to {@code 0}.
   */
  public void setResolve(final int resolve) {
    this.resolve = resolve;
  }

  /**
   * The direction of the type hierarchy resolution. If not defined, defaults to {@link TypeHierarchyDirection#Children Children}.
   */
  @Pure
  public TypeHierarchyDirection getDirection() {
    return this.direction;
  }

  /**
   * The direction of the type hierarchy resolution. If not defined, defaults to {@link TypeHierarchyDirection#Children Children}.
   */
  public void setDirection(final TypeHierarchyDirection direction) {
    this.direction = direction;
  }

  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add("resolve", this.resolve);
    b.add("direction", this.direction);
    b.add("textDocument", getTextDocument());
    b.add("position", getPosition());
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
    TypeHierarchyParams other = (TypeHierarchyParams) obj;
    if (other.resolve != this.resolve) {
		return false;
	}
    if (this.direction == null) {
      if (other.direction != null) {
		return false;
	}
    } else if (!this.direction.equals(other.direction)) {
		return false;
	}
    return true;
  }

  @Override
  @Pure
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + this.resolve;
    return prime * result + ((this.direction== null) ? 0 : this.direction.hashCode());
  }
}
