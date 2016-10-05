/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.langs;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class CompletionItem {

	/**
	 * The label of this completion item. By default
	 *
	 * also the text that is inserted when selecting
	 *
	 * this completion.
	 * (Required)
	 *
	 */
	@SerializedName("label")
	@Expose
	private String label;
	/**
	 * The kind of this completion item. Based of the kind
	 *
	 * an icon is chosen by the editor.
	 *
	 */
	@SerializedName("kind")
	@Expose
	private Integer kind;
	/**
	 * A human-readable string with additional information
	 *
	 * about this item, like type or symbol information.
	 *
	 */
	@SerializedName("detail")
	@Expose
	private String detail;
	/**
	 * A human-readable string that represents a doc-comment.
	 *
	 */
	@SerializedName("documentation")
	@Expose
	private String documentation;
	/**
	 * A string that shoud be used when comparing this item
	 *
	 * with other items. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 */
	@SerializedName("sortText")
	@Expose
	private String sortText;
	/**
	 * A string that should be used when filtering a set of
	 *
	 * completion items. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 */
	@SerializedName("filterText")
	@Expose
	private String filterText;
	/**
	 * A string that should be inserted a document when selecting
	 *
	 * this completion. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 */
	@SerializedName("insertText")
	@Expose
	private String insertText;
	/**
	 *
	 */
	@SerializedName("textEdit")
	@Expose
	private TextEdit textEdit;
	/**
	 * An data entry field that is preserved on a completion item between
	 *
	 * a [CompletionRequest](#CompletionRequest) and a [CompletionResolveRequest]
	 *
	 * (#CompletionResolveRequest)
	 *
	 */
	@SerializedName("data")
	@Expose
	private Object data;

	/**
	 * The label of this completion item. By default
	 *
	 * also the text that is inserted when selecting
	 *
	 * this completion.
	 * (Required)
	 *
	 * @return
	 *     The label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * The label of this completion item. By default
	 *
	 * also the text that is inserted when selecting
	 *
	 * this completion.
	 * (Required)
	 *
	 * @param label
	 *     The label
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	public CompletionItem withLabel(String label) {
		this.label = label;
		return this;
	}

	/**
	 * The kind of this completion item. Based of the kind
	 *
	 * an icon is chosen by the editor.
	 *
	 * @return
	 *     The kind
	 */
	public Integer getKind() {
		return kind;
	}

	/**
	 * The kind of this completion item. Based of the kind
	 *
	 * an icon is chosen by the editor.
	 *
	 * @param kind
	 *     The kind
	 */
	public void setKind(Integer kind) {
		this.kind = kind;
	}

	public CompletionItem withKind(Integer kind) {
		this.kind = kind;
		return this;
	}

	/**
	 * A human-readable string with additional information
	 *
	 * about this item, like type or symbol information.
	 *
	 * @return
	 *     The detail
	 */
	public String getDetail() {
		return detail;
	}

	/**
	 * A human-readable string with additional information
	 *
	 * about this item, like type or symbol information.
	 *
	 * @param detail
	 *     The detail
	 */
	public void setDetail(String detail) {
		this.detail = detail;
	}

	public CompletionItem withDetail(String detail) {
		this.detail = detail;
		return this;
	}

	/**
	 * A human-readable string that represents a doc-comment.
	 *
	 * @return
	 *     The documentation
	 */
	public String getDocumentation() {
		return documentation;
	}

	/**
	 * A human-readable string that represents a doc-comment.
	 *
	 * @param documentation
	 *     The documentation
	 */
	public void setDocumentation(String documentation) {
		this.documentation = documentation;
	}

	public CompletionItem withDocumentation(String documentation) {
		this.documentation = documentation;
		return this;
	}

	/**
	 * A string that shoud be used when comparing this item
	 *
	 * with other items. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 * @return
	 *     The sortText
	 */
	public String getSortText() {
		return sortText;
	}

	/**
	 * A string that shoud be used when comparing this item
	 *
	 * with other items. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 * @param sortText
	 *     The sortText
	 */
	public void setSortText(String sortText) {
		this.sortText = sortText;
	}

	public CompletionItem withSortText(String sortText) {
		this.sortText = sortText;
		return this;
	}

	/**
	 * A string that should be used when filtering a set of
	 *
	 * completion items. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 * @return
	 *     The filterText
	 */
	public String getFilterText() {
		return filterText;
	}

	/**
	 * A string that should be used when filtering a set of
	 *
	 * completion items. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 * @param filterText
	 *     The filterText
	 */
	public void setFilterText(String filterText) {
		this.filterText = filterText;
	}

	public CompletionItem withFilterText(String filterText) {
		this.filterText = filterText;
		return this;
	}

	/**
	 * A string that should be inserted a document when selecting
	 *
	 * this completion. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 * @return
	 *     The insertText
	 */
	public String getInsertText() {
		return insertText;
	}

	/**
	 * A string that should be inserted a document when selecting
	 *
	 * this completion. When `falsy` the [label](#CompletionItem.label)
	 *
	 * is used.
	 *
	 * @param insertText
	 *     The insertText
	 */
	public void setInsertText(String insertText) {
		this.insertText = insertText;
	}

	public CompletionItem withInsertText(String insertText) {
		this.insertText = insertText;
		return this;
	}

	/**
	 *
	 * @return
	 *     The textEdit
	 */
	public TextEdit getTextEdit() {
		return textEdit;
	}

	/**
	 *
	 * @param textEdit
	 *     The textEdit
	 */
	public void setTextEdit(TextEdit textEdit) {
		this.textEdit = textEdit;
	}

	public CompletionItem withTextEdit(TextEdit textEdit) {
		this.textEdit = textEdit;
		return this;
	}

	/**
	 * An data entry field that is preserved on a completion item between
	 *
	 * a [CompletionRequest](#CompletionRequest) and a [CompletionResolveRequest]
	 *
	 * (#CompletionResolveRequest)
	 *
	 * @return
	 *     The data
	 */
	public Object getData() {
		return data;
	}

	/**
	 * An data entry field that is preserved on a completion item between
	 *
	 * a [CompletionRequest](#CompletionRequest) and a [CompletionResolveRequest]
	 *
	 * (#CompletionResolveRequest)
	 *
	 * @param data
	 *     The data
	 */
	public void setData(Object data) {
		this.data = data;
	}

	public CompletionItem withData(Object data) {
		this.data = data;
		return this;
	}

}
