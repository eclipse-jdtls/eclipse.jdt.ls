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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;
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
import org.eclipse.jdt.ls.core.internal.managers.MavenProjectImporter;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

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
			// for backward compatibility
			List<IProgressMonitor> monitors = Arrays.asList(new ServerStatusMonitor(), createJobMonitor(job));
			return new MulticastProgressReporter(monitors);
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

	private class MulticastProgressReporter implements IProgressMonitor {
		protected List<IProgressMonitor> monitors;

		public MulticastProgressReporter(List<IProgressMonitor> monitors) {
			this.monitors = monitors;
		}

		@Override
		public void done() {
			for (IProgressMonitor monitor : monitors) {
				monitor.done();
			}
		}

		@Override
		public boolean isCanceled() {
			for (IProgressMonitor monitor : monitors) {
				if (!monitor.isCanceled()) {
					return false;
				}
			}

			return true;
		}

		@Override
		public void beginTask(String name, int totalWork) {
			for (IProgressMonitor monitor : monitors) {
				monitor.beginTask(name, totalWork);
			}
		}

		@Override
		public void internalWorked(double work) {
			for (IProgressMonitor monitor : monitors) {
				monitor.internalWorked(work);
			}
		}

		@Override
		public void setCanceled(boolean cancelled) {
			for (IProgressMonitor monitor : monitors) {
				monitor.setCanceled(cancelled);
			}
		}

		@Override
		public void setTaskName(String name) {
			for (IProgressMonitor monitor : monitors) {
				monitor.setTaskName(name);
			}
		}

		@Override
		public void subTask(String name) {
			for (IProgressMonitor monitor : monitors) {
				monitor.subTask(name);
			}
		}

		@Override
		public void worked(int work) {
			for (IProgressMonitor monitor : monitors) {
				monitor.worked(work);
			}
		}
	}

	private class ProgressReporter extends CancellableProgressMonitor {

		protected static final String SEPARATOR = " - ";
		protected static final String IMPORTING_MAVEN_PROJECTS = "Importing Maven project(s)";
		protected Job job;
		protected int totalWork;
		protected String taskName;
		protected String subTaskName;
		protected int progress;
		protected long lastReport = 0;
		protected String progressId;
		private boolean sentBegin = false;

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
			progressId = UUID.randomUUID().toString();
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
			if (IMPORTING_MAVEN_PROJECTS.equals(taskName) && (subTaskName == null || subTaskName.isEmpty())) {
				// completed or failed transfer
				return;
			}
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
			if (client == null || preferenceManager == null || preferenceManager.getClientPreferences() == null) {
				return;
			}
			String task = StringUtils.defaultIfBlank(taskName, (job == null || StringUtils.isBlank(job.getName())) ? "Background task" : job.getName());
			if (preferenceManager.getClientPreferences().isProgressReportSupported()) {
				ProgressReport progressReport = new ProgressReport(progressId);
				progressReport.setTask(task);
				progressReport.setSubTask(subTaskName);
				progressReport.setTotalWork(totalWork);
				progressReport.setWorkDone(progress);
				progressReport.setComplete(isDone());
				if (task != null && subTaskName != null && !subTaskName.isEmpty() && task.equals(MavenProjectImporter.IMPORTING_MAVEN_PROJECTS)) {
					progressReport.setStatus(task + SEPARATOR + subTaskName);
				} else {
					progressReport.setStatus(formatMessage(task));
				}

				client.sendProgressReport(progressReport);
			} else {
				Either<String, Integer> id = Either.forLeft(progressId);
				if (!sentBegin) {
					var workDoneProgressBegin = new WorkDoneProgressBegin();
					workDoneProgressBegin.setMessage(task);
					workDoneProgressBegin.setTitle(subTaskName == null ? task : subTaskName);
					client.notifyProgress(new ProgressParams(id, Either.forLeft(workDoneProgressBegin)));
					sentBegin = true;
				}
				WorkDoneProgressNotification notification;
				if (isDone()) {
					var endNotification = new WorkDoneProgressEnd();
					endNotification.setMessage(task);
					notification = endNotification;
					sentBegin = false;
				} else {
					var reportNotification = new WorkDoneProgressReport();
					reportNotification.setMessage(task);
					reportNotification.setPercentage((int)(((double) progress) / totalWork * 100.0));
					notification = reportNotification;
				}
				client.notifyProgress(new ProgressParams(id, Either.forLeft(notification)));
			}
		}


		protected String formatMessage(String task) {
			String message = getMessage(task);
			return (totalWork > 0) ? String.format("%.0f%% %s", ((double) progress / totalWork) * 100, message) : message;
		}

		protected String getMessage(String task) {
			String message = subTaskName == null || subTaskName.isEmpty() ? "" : subTaskName;
			return message;
		}
	}

	//XXX should we deprecate that class? doesn't seem to bring much value over the more generic ProgressReporter,
	//it's largely kept for legacy purposes.
	private class ServerStatusMonitor extends ProgressReporter {

		@Override
		protected String formatMessage(String task) {
			String message = getMessage(task);
			if (totalWork > 0 && !message.isEmpty()) {
				message = SEPARATOR + message;
			}
			return String.format("%.0f%% Starting Java Language Server%s", ((double) progress / totalWork) * 100, message);
		}

		@Override
		protected void sendStatus() {
			if (client == null) {
				return;
			}
			String task = StringUtils.defaultIfBlank(taskName, (job == null || StringUtils.isBlank(job.getName())) ? "Background task" : job.getName());
			String message = formatMessage(task);
			client.sendStatusReport(new StatusReport().withType(ServiceStatus.Starting.name()).withMessage(message));
		}

	}
}
