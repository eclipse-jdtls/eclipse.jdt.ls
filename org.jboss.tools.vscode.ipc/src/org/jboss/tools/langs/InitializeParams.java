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

public class InitializeParams {

	/**
	 * The process Id of the parent process that started
	 *
	 * the server.
	 * (Required)
	 *
	 */
	@SerializedName("processId")
	@Expose
	private Double processId;
	/**
	 * The rootPath of the workspace. Is null
	 *
	 * if no folder is open.
	 * (Required)
	 *
	 */
	@SerializedName("rootPath")
	@Expose
	private String rootPath;
	/**
	 * Defines the capabilities provided by the client.
	 * (Required)
	 *
	 */
	@SerializedName("capabilities")
	@Expose
	private Object capabilities;
	/**
	 * User provided initialization options.
	 *
	 */
	@SerializedName("initializationOptions")
	@Expose
	private Object initializationOptions;
	/**
	 * The initial trace setting. If omitted trace is disabled ('off').
	 *
	 */
	@SerializedName("trace")
	@Expose
	private String trace;

	/**
	 * The process Id of the parent process that started
	 *
	 * the server.
	 * (Required)
	 *
	 * @return
	 *     The processId
	 */
	public Double getProcessId() {
		return processId;
	}

	/**
	 * The process Id of the parent process that started
	 *
	 * the server.
	 * (Required)
	 *
	 * @param processId
	 *     The processId
	 */
	public void setProcessId(Double processId) {
		this.processId = processId;
	}

	public InitializeParams withProcessId(Double processId) {
		this.processId = processId;
		return this;
	}

	/**
	 * The rootPath of the workspace. Is null
	 *
	 * if no folder is open.
	 * (Required)
	 *
	 * @return
	 *     The rootPath
	 */
	public String getRootPath() {
		return rootPath;
	}

	/**
	 * The rootPath of the workspace. Is null
	 *
	 * if no folder is open.
	 * (Required)
	 *
	 * @param rootPath
	 *     The rootPath
	 */
	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}

	public InitializeParams withRootPath(String rootPath) {
		this.rootPath = rootPath;
		return this;
	}

	/**
	 * Defines the capabilities provided by the client.
	 * (Required)
	 *
	 * @return
	 *     The capabilities
	 */
	public Object getCapabilities() {
		return capabilities;
	}

	/**
	 * Defines the capabilities provided by the client.
	 * (Required)
	 *
	 * @param capabilities
	 *     The capabilities
	 */
	public void setCapabilities(Object capabilities) {
		this.capabilities = capabilities;
	}

	public InitializeParams withCapabilities(Object capabilities) {
		this.capabilities = capabilities;
		return this;
	}

	/**
	 * User provided initialization options.
	 *
	 * @return
	 *     The initializationOptions
	 */
	public Object getInitializationOptions() {
		return initializationOptions;
	}

	/**
	 * User provided initialization options.
	 *
	 * @param initializationOptions
	 *     The initializationOptions
	 */
	public void setInitializationOptions(Object initializationOptions) {
		this.initializationOptions = initializationOptions;
	}

	public InitializeParams withInitializationOptions(Object initializationOptions) {
		this.initializationOptions = initializationOptions;
		return this;
	}

	/**
	 * The initial trace setting. If omitted trace is disabled ('off').
	 *
	 * @return
	 *     The trace
	 */
	public String getTrace() {
		return trace;
	}

	/**
	 * The initial trace setting. If omitted trace is disabled ('off').
	 *
	 * @param trace
	 *     The trace
	 */
	public void setTrace(String trace) {
		this.trace = trace;
	}

	public InitializeParams withTrace(String trace) {
		this.trace = trace;
		return this;
	}

}
