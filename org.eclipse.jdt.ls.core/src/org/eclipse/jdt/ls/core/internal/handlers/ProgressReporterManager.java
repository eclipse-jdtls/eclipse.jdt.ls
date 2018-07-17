/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.internal.jobs.JobMessages;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.ProgressProvider;
import org.eclipse.jdt.ls.core.internal.CancellableProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.ProgressReport;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.StatusReport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * Manager for creating {@link IProgressMonitor}s reporting progress to clients
 *
 * @author Fred Bricon
 *
 */
public class ProgressReporterManager extends ProgressProvider {

	private JavaLanguageClient client;
	private long delay;
	private PreferenceManager preferenceManager;

	public ProgressReporterManager(JavaLanguageClient client, PreferenceManager preferenceManager) {
		this.client = client;
		this.preferenceManager = preferenceManager;
		delay = 200;
	}

	@Override
	public IProgressMonitor createMonitor(Job job) {
		if (job.belongsTo(InitHandler.JAVA_LS_INITIALIZATION_JOBS)) {
			return new ServerStatusMonitor();
		}
		IProgressMonitor monitor = createJobMonitor(job);
		return monitor;
	}

	private IProgressMonitor createJobMonitor(Job job) {
		return new ProgressReporter(job);
	}

	@Override
	public IProgressMonitor getDefaultMonitor() {
		return new ProgressReporter();
	}

	public IProgressMonitor getProgressReporter(CancelChecker checker) {
		return new ProgressReporter(checker);
	}

	@Override
	public IProgressMonitor createProgressGroup() {
		return getDefaultMonitor();
	}

	//For Unit tests purposes
	public void setReportThrottle(long delay) {
		this.delay = delay;
	}

	private class ProgressReporter extends CancellableProgressMonitor {

		protected Job job;
		protected int totalWork;
		protected String taskName;
		protected String subTaskName;
		protected int progress;
		protected long lastReport = 0;
		protected String progressId;

		public ProgressReporter() {
			super(null);
			progressId = UUID.randomUUID().toString();
		}

		public ProgressReporter(Job job) {
			this();
			this.job = job;
		}

		public ProgressReporter(CancelChecker checker) {
			super(checker);
		}


		@Override
		public void setTaskName(String name) {
			super.setTaskName(name);
			taskName = name;
		}

		@Override
		public void beginTask(String task, int totalWork) {
			taskName = task;
			this.totalWork = totalWork;
			sendProgress();
		}

		@Override
		public void subTask(String name) {
			this.subTaskName = name;
			sendProgress();
		}

		@Override
		public void worked(int work) {
			progress += work;
			sendProgress();
		}

		@Override
		public void done() {
			super.done();
			sendProgress();
		}

		@Override
		public boolean isDone() {
			return super.isDone() || (totalWork > 0 && progress >= totalWork);
		}

		private void sendProgress() {
			//Ignore system jobs or "The user operation is waiting for background work to complete." tasks
			if (job != null && job.isSystem() || JobMessages.jobs_blocked0.equals(taskName)) {
				return;
			}
			// throttle the sending of progress
			long currentTime = System.currentTimeMillis();
			if (lastReport == 0 || isDone() || (currentTime - lastReport >= delay)) {
				lastReport = currentTime;
				sendStatus();
			}
		}

		protected void sendStatus() {
			if (client == null || preferenceManager == null || preferenceManager.getClientPreferences() == null || !preferenceManager.getClientPreferences().isProgressReportSupported()) {
				return;
			}
			ProgressReport progressReport = new ProgressReport(progressId);
			String task = StringUtils.defaultIfBlank(taskName, (job == null || StringUtils.isBlank(job.getName())) ? "Background task" : job.getName());
			progressReport.setTask(task);
			progressReport.setSubTask(subTaskName);
			progressReport.setTotalWork(totalWork);
			progressReport.setWorkDone(progress);
			progressReport.setComplete(isDone());
			progressReport.setStatus(formatMessage(task));
			client.sendProgressReport(progressReport);
		}


		protected String formatMessage(String task) {
			return (totalWork > 0) ? String.format("%s - %.0f%%", task, ((double) progress / totalWork) * 100) : task;
		}
	}

	//XXX should we deprecate that class? doesn't seem to bring much value over the more generic ProgressReporter,
	//it's largely kept for legacy purposes.
	private class ServerStatusMonitor extends ProgressReporter {

		@Override
		protected String formatMessage(String task) {
			String message = this.taskName == null || this.taskName.length() == 0 ? "" : ("- " + this.taskName);
			return String.format("%.0f%% Starting Java Language Server %s", ((double) progress / totalWork) * 100, message);
		}

		@Override
		protected void sendStatus() {
			if (client == null) {
				return;
			}
			String message = formatMessage(subTaskName);
			client.sendStatusReport(new StatusReport().withType(ServiceStatus.Starting.name()).withMessage(message));
		}
	}
}
