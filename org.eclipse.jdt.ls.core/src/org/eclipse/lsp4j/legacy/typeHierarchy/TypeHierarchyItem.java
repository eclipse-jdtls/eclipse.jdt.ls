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

import java.util.List;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.json.adapters.JsonElementTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.lsp4j.util.Preconditions;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

import com.google.common.annotations.Beta;
import com.google.gson.annotations.JsonAdapter;

/**
 * Representation of an item that carries type information (such as class, interface, enumeration, etc) with additional parentage details.
 */
@Beta
@SuppressWarnings("all")
public class TypeHierarchyItem {
  /**
   * The human readable name of the hierarchy item.
   */
  @NonNull
  private String name;

  /**
   * Optional detail for the hierarchy item. It can be, for instance, the signature of a function or method.
   */
  private String detail;

  /**
   * The kind of the hierarchy item. For instance, class or interface.
   */
  @NonNull
  private SymbolKind kind;

  /**
   * {@code true} if the hierarchy item is deprecated. Otherwise, {@code false}. It is {@code false} by default.
   */
  private Boolean deprecated;

  /**
   * The URI of the text document where this type hierarchy item belongs to.
   */
  @NonNull
  private String uri;

  /**
   * The range enclosing this type hierarchy item not including leading/trailing whitespace but everything else
   * like comments. This information is typically used to determine if the clients cursor is inside the type
   * hierarchy item to reveal in the symbol in the UI.
   *
   * @see TypeHierarchyItem#selectionRange
   */
  @NonNull
  private Range range;

  /**
   * The range that should be selected and revealed when this type hierarchy item is being picked, e.g the name of a function.
   * Must be contained by the the {@link TypeHierarchyItem#getRange range}.
   *
   * @see TypeHierarchyItem#range
   */
  @NonNull
  private Range selectionRange;

  /**
   * If this type hierarchy item is resolved, it contains the direct parents. Could be empty if the item does not have any
   * direct parents. If not defined, the parents have not been resolved yet.
   */
  private List<TypeHierarchyItem> parents;

  /**
   * If this type hierarchy item is resolved, it contains the direct children of the current item.
   * Could be empty if the item does not have any descendants. If not defined, the children have not been resolved.
   */
  private List<TypeHierarchyItem> children;

  /**
   * An optional data field can be used to identify a type hierarchy item in a resolve request.
   */
  @JsonAdapter(JsonElementTypeAdapter.Factory.class)
  private Object data;

  /**
   * The human readable name of the hierarchy item.
   */
  @Pure
  @NonNull
  public String getName() {
    return this.name;
  }

  /**
   * The human readable name of the hierarchy item.
   */
  public void setName(@NonNull final String name) {
    this.name = Preconditions.checkNotNull(name, "name");
  }

  /**
   * Optional detail for the hierarchy item. It can be, for instance, the signature of a function or method.
   */
  @Pure
  public String getDetail() {
    return this.detail;
  }

  /**
   * Optional detail for the hierarchy item. It can be, for instance, the signature of a function or method.
   */
  public void setDetail(final String detail) {
    this.detail = detail;
  }

  /**
   * The kind of the hierarchy item. For instance, class or interface.
   */
  @Pure
  @NonNull
  public SymbolKind getKind() {
    return this.kind;
  }

  /**
   * The kind of the hierarchy item. For instance, class or interface.
   */
  public void setKind(@NonNull final SymbolKind kind) {
    this.kind = Preconditions.checkNotNull(kind, "kind");
  }

  /**
   * {@code true} if the hierarchy item is deprecated. Otherwise, {@code false}. It is {@code false} by default.
   */
  @Pure
  public Boolean getDeprecated() {
    return this.deprecated;
  }

  /**
   * {@code true} if the hierarchy item is deprecated. Otherwise, {@code false}. It is {@code false} by default.
   */
  public void setDeprecated(final Boolean deprecated) {
    this.deprecated = deprecated;
  }

  /**
   * The URI of the text document where this type hierarchy item belongs to.
   */
  @Pure
  @NonNull
  public String getUri() {
    return this.uri;
  }

  /**
   * The URI of the text document where this type hierarchy item belongs to.
   */
  public void setUri(@NonNull final String uri) {
    this.uri = Preconditions.checkNotNull(uri, "uri");
  }

  /**
   * The range enclosing this type hierarchy item not including leading/trailing whitespace but everything else
   * like comments. This information is typically used to determine if the clients cursor is inside the type
   * hierarchy item to reveal in the symbol in the UI.
   *
   * @see TypeHierarchyItem#selectionRange
   */
  @Pure
  @NonNull
  public Range getRange() {
    return this.range;
  }

  /**
   * The range enclosing this type hierarchy item not including leading/trailing whitespace but everything else
   * like comments. This information is typically used to determine if the clients cursor is inside the type
   * hierarchy item to reveal in the symbol in the UI.
   *
   * @see TypeHierarchyItem#selectionRange
   */
  public void setRange(@NonNull final Range range) {
    this.range = Preconditions.checkNotNull(range, "range");
  }

  /**
   * The range that should be selected and revealed when this type hierarchy item is being picked, e.g the name of a function.
   * Must be contained by the the {@link TypeHierarchyItem#getRange range}.
   *
   * @see TypeHierarchyItem#range
   */
  @Pure
  @NonNull
  public Range getSelectionRange() {
    return this.selectionRange;
  }

  /**
   * The range that should be selected and revealed when this type hierarchy item is being picked, e.g the name of a function.
   * Must be contained by the the {@link TypeHierarchyItem#getRange range}.
   *
   * @see TypeHierarchyItem#range
   */
  public void setSelectionRange(@NonNull final Range selectionRange) {
    this.selectionRange = Preconditions.checkNotNull(selectionRange, "selectionRange");
  }

  /**
   * If this type hierarchy item is resolved, it contains the direct parents. Could be empty if the item does not have any
   * direct parents. If not defined, the parents have not been resolved yet.
   */
  @Pure
  public List<TypeHierarchyItem> getParents() {
    return this.parents;
  }

  /**
   * If this type hierarchy item is resolved, it contains the direct parents. Could be empty if the item does not have any
   * direct parents. If not defined, the parents have not been resolved yet.
   */
  public void setParents(final List<TypeHierarchyItem> parents) {
    this.parents = parents;
  }

  /**
   * If this type hierarchy item is resolved, it contains the direct children of the current item.
   * Could be empty if the item does not have any descendants. If not defined, the children have not been resolved.
   */
  @Pure
  public List<TypeHierarchyItem> getChildren() {
    return this.children;
  }

  /**
   * If this type hierarchy item is resolved, it contains the direct children of the current item.
   * Could be empty if the item does not have any descendants. If not defined, the children have not been resolved.
   */
  public void setChildren(final List<TypeHierarchyItem> children) {
    this.children = children;
  }

  /**
   * An optional data field can be used to identify a type hierarchy item in a resolve request.
   */
  @Pure
  public Object getData() {
    return this.data;
  }

  /**
   * An optional data field can be used to identify a type hierarchy item in a resolve request.
   */
  public void setData(final Object data) {
    this.data = data;
  }

  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add("name", this.name);
    b.add("detail", this.detail);
    b.add("kind", this.kind);
    b.add("deprecated", this.deprecated);
    b.add("uri", this.uri);
    b.add("range", this.range);
    b.add("selectionRange", this.selectionRange);
    b.add("parents", this.parents);
    b.add("children", this.children);
    b.add("data", this.data);
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
    TypeHierarchyItem other = (TypeHierarchyItem) obj;
    if (this.name == null) {
      if (other.name != null) {
		return false;
	}
    } else if (!this.name.equals(other.name)) {
		return false;
	}
    if (this.detail == null) {
      if (other.detail != null) {
		return false;
	}
    } else if (!this.detail.equals(other.detail)) {
		return false;
	}
    if (this.kind == null) {
      if (other.kind != null) {
		return false;
	}
    } else if (!this.kind.equals(other.kind)) {
		return false;
	}
    if (this.deprecated == null) {
      if (other.deprecated != null) {
		return false;
	}
    } else if (!this.deprecated.equals(other.deprecated)) {
		return false;
	}
    if (this.uri == null) {
      if (other.uri != null) {
		return false;
	}
    } else if (!this.uri.equals(other.uri)) {
		return false;
	}
    if (this.range == null) {
      if (other.range != null) {
		return false;
	}
    } else if (!this.range.equals(other.range)) {
		return false;
	}
    if (this.selectionRange == null) {
      if (other.selectionRange != null) {
		return false;
	}
    } else if (!this.selectionRange.equals(other.selectionRange)) {
		return false;
	}
    if (this.parents == null) {
      if (other.parents != null) {
		return false;
	}
    } else if (!this.parents.equals(other.parents)) {
		return false;
	}
    if (this.children == null) {
      if (other.children != null) {
		return false;
	}
    } else if (!this.children.equals(other.children)) {
		return false;
	}
    if (this.data == null) {
      if (other.data != null) {
		return false;
	}
    } else if (!this.data.equals(other.data)) {
		return false;
	}
    return true;
  }

  @Override
  @Pure
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((this.name== null) ? 0 : this.name.hashCode());
    result = prime * result + ((this.detail== null) ? 0 : this.detail.hashCode());
    result = prime * result + ((this.kind== null) ? 0 : this.kind.hashCode());
    result = prime * result + ((this.deprecated== null) ? 0 : this.deprecated.hashCode());
    result = prime * result + ((this.uri== null) ? 0 : this.uri.hashCode());
    result = prime * result + ((this.range== null) ? 0 : this.range.hashCode());
    result = prime * result + ((this.selectionRange== null) ? 0 : this.selectionRange.hashCode());
    result = prime * result + ((this.parents== null) ? 0 : this.parents.hashCode());
    result = prime * result + ((this.children== null) ? 0 : this.children.hashCode());
    return prime * result + ((this.data== null) ? 0 : this.data.hashCode());
  }
}
