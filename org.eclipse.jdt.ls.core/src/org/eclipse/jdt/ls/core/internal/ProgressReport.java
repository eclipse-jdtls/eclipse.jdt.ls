/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Progress Report sent to clients.
 *
 * @author Fred Bricon
 */
public class ProgressReport {

	@SerializedName("id")
	@Expose
	private String id;

	@SerializedName("task")
	@Expose
	private String task;

	@SerializedName("subTask")
	@Expose
	private String subTask;

	@SerializedName("status")
	@Expose
	private String status;

	@SerializedName("totalWork")
	@Expose
	private int totalWork;

	@SerializedName("workDone")
	@Expose
	private int workDone;

	@SerializedName("complete")
	@Expose
	private boolean complete;

	public ProgressReport(String progressId) {
		this.id = progressId;
	}

	/**
	 * @return the task
	 */
	public String getTask() {
		return task;
	}

	/**
	 * @param task
	 *            the task to set
	 */
	public void setTask(String task) {
		this.task = task;
	}

	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * @param status
	 *            the status to set
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * @return the complete
	 */
	public boolean isComplete() {
		return complete;
	}

	/**
	 * @param complete
	 *            the complete to set
	 */
	public void setComplete(boolean complete) {
		this.complete = complete;
	}

	/**
	 * @return the totalWork
	 */
	public int getTotalWork() {
		return totalWork;
	}

	/**
	 * @param totalWork
	 *            the totalWork to set
	 */
	public void setTotalWork(int totalWork) {
		this.totalWork = totalWork;
	}

	/**
	 * @return the workDone
	 */
	public int getWorkDone() {
		return workDone;
	}

	/**
	 * @param workDone
	 *            the workDone to set
	 */
	public void setWorkDone(int workDone) {
		this.workDone = workDone;
	}

	/**
	 * @return the progress id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the subTask name
	 */
	public String getSubTask() {
		return subTask;
	}

	/**
	 * @param subTask
	 *                    the subTask to set
	 */
	public void setSubTask(String subTask) {
		this.subTask = subTask;
	}
}
