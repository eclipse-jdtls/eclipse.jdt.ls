/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.core.internal.resources.CharsetDeltaJob;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.ls.core.internal.handlers.BaseInitHandler;
import org.eclipse.m2e.core.internal.embedder.MavenExecutionContext;
import org.eclipse.m2e.core.internal.jobs.IBackgroundProcessingQueue;

/**
 * Copied from m2e's org.eclipse.m2e.tests.common/src/org/eclipse/m2e/tests/common/JobHelpers.java
 *
 */
@SuppressWarnings("restriction")
public final class JobHelpers {

	private JobHelpers() {
		//no instantiation
	}

	private static final int POLLING_DELAY = 10;
	public static final int MAX_TIME_MILLIS = 300000;

	public static void waitForJobsToComplete() {
		try {
			waitForJobsToComplete(new NullProgressMonitor());
		} catch(Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static void waitForJobsToComplete(IProgressMonitor monitor) throws InterruptedException, CoreException {
		waitForBuildJobs();

		/*
		 * First, make sure refresh job gets all resource change events
		 *
		 * Resource change events are delivered after WorkspaceJob#runInWorkspace returns
		 * and during IWorkspace#run. Each change notification is delivered by
		 * only one thread/job, so we make sure no other workspaceJob is running then
		 * call IWorkspace#run from this thread.
		 *
		 * Unfortunately, this does not catch other jobs and threads that call IWorkspace#run
		 * so we have to hard-code workarounds
		 *
		 * See http://www.eclipse.org/articles/Article-Resource-deltas/resource-deltas.html
		 */
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IJobManager jobManager = Job.getJobManager();
		jobManager.suspend();
		try {
			Job[] jobs = jobManager.find(null);
			for(int i = 0; i < jobs.length; i++ ) {
				if(jobs[i] instanceof WorkspaceJob || jobs[i].getClass().getName().endsWith("JREUpdateJob")) {
					jobs[i].join();
				}
			}
			workspace.run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) {
				}
			}, workspace.getRoot(), 0, monitor);

			// Now we flush all background processing queues
			boolean processed = flushProcessingQueues(jobManager, monitor);
			for(int i = 0; i < 10 && processed; i++ ) {
				processed = flushProcessingQueues(jobManager, monitor);
				try {
					Thread.sleep(10);
				} catch(InterruptedException e) {
				}
			}
			if (processed) {
				JavaLanguageServerPlugin.logInfo("Could not flush background processing queues: " + getProcessingQueues(jobManager));
			}
		} finally {
			jobManager.resume();
		}

		waitForBuildJobs();
		waitForJobs(CharsetDeltaJob.FAMILY_CHARSET_DELTA, null);
	}

	private static boolean flushProcessingQueues(IJobManager jobManager, IProgressMonitor monitor)
			throws InterruptedException, CoreException {
		boolean processed = false;
		for(IBackgroundProcessingQueue queue : getProcessingQueues(jobManager)) {
			queue.join();
			if(!queue.isEmpty()) {
				Deque<MavenExecutionContext> context = MavenExecutionContext.suspend();
				try {
					IStatus status = queue.run(monitor);
					if (!status.isOK() && status.getSeverity() != IStatus.CANCEL) {
						throw new CoreException(status);
					}
					processed = true;
				} finally {
					MavenExecutionContext.resume(context);
				}
			}
			if(queue.isEmpty()) {
				queue.cancel();
			}
		}
		return processed;
	}

	private static List<IBackgroundProcessingQueue> getProcessingQueues(IJobManager jobManager) {
		ArrayList<IBackgroundProcessingQueue> queues = new ArrayList<>();
		for(Job job : jobManager.find(null)) {
			if (job instanceof IBackgroundProcessingQueue backgroundProcessingQueue) {
				queues.add(backgroundProcessingQueue);
			}
		}
		return queues;
	}

	public static void waitForWorkspaceJobsToComplete(IProgressMonitor monitor) throws InterruptedException {
		IJobManager jobManager = Job.getJobManager();
		jobManager.suspend();
		try {
			Job[] jobs = jobManager.find(null);
			for(int i = 0; i < jobs.length; i++ ) {
				if(jobs[i] instanceof WorkspaceJob || jobs[i].getClass().getName().endsWith("JREUpdateJob")) {
					jobs[i].join();
				}
			}
		} finally {
			jobManager.resume();
		}
	}

	private static void waitForBuildJobs() {
		waitForBuildJobs(MAX_TIME_MILLIS);
	}

	public static void waitForBuildJobs(int maxTimeMilis) {
		waitForJobs(BuildJobMatcher.INSTANCE, maxTimeMilis);
	}

	public static void waitForInitializeJobs() {
		waitForInitializeJobs(MAX_TIME_MILLIS);
	}

	public static void waitForInitializeJobs(int maxTimeMillis) {
		waitForJobs(InitializeJobMatcher.INSTANCE, maxTimeMillis);
	}

	public static void waitForDownloadSourcesJobs(int maxTimeMillis) {
		waitForJobs(DownloadSourcesJobMatcher.INSTANCE, maxTimeMillis);
	}

	public static void waitForJobs(IJobMatcher matcher, int maxWaitMillis) {
		final long limit = System.currentTimeMillis() + maxWaitMillis;
		while(true) {
			Job job = getJob(matcher);
			if(job == null) {
				return;
			}
			boolean timeout = System.currentTimeMillis() > limit;
			if (timeout) {
				JavaLanguageServerPlugin.logInfo("Timeout while waiting for completion of job: " + job);
				break;
			}
			job.wakeUp();
			try {
				Thread.sleep(POLLING_DELAY);
			} catch(InterruptedException e) {
				// ignore and keep waiting
			}
		}
	}

	private static Job getJob(IJobMatcher matcher) {
		Job[] jobs = Job.getJobManager().find(null);
		for(Job job : jobs) {
			if(matcher.matches(job)) {
				return job;
			}
		}
		return null;
	}

	public static void waitForJobs(String jobFamily, IProgressMonitor monitor) {
		try {
			Job.getJobManager().join(jobFamily, monitor);
		} catch (OperationCanceledException ignorable) {
			// No need to pollute logs when query is cancelled
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	public static void waitForLoadingGradleVersionJob() {
		waitForJobs(LoadingGradleVersionJobMatcher.INSTANCE, MAX_TIME_MILLIS);
	}

	public static void waitForBuildOffJobs(int maxTimeMillis) {
		waitForJobs(BuildJobOffMatcher.INSTANCE, maxTimeMillis);
	}

	public static void waitForProjectRegistryRefreshJob() {
		waitForJobs(ProjectRegistryRefreshJobMatcher.INSTANCE, MAX_TIME_MILLIS);
	}

	public static void waitForRepositoryRegistryUpdateJob() {
		waitForJobs(RepositoryRegistryUpdateJobMatcher.INSTANCE, MAX_TIME_MILLIS);
	}

	// copied from ./org.eclipse.jdt.core.tests.performance/src/org/eclipse/jdt/core/tests/performance/FullSourceWorkspaceTests.java
	public static void waitUntilIndexesReady() {
		// dummy query for waiting until the indexes are ready
		SearchEngine engine = new SearchEngine();
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		JavaModelManager.getIndexManager().waitForIndex(true, null);
		try {
			engine.searchAllTypeNames(null, SearchPattern.R_EXACT_MATCH, "!@$#!@".toCharArray(), SearchPattern.R_PATTERN_MATCH | SearchPattern.R_CASE_SENSITIVE, IJavaSearchConstants.CLASS, scope, new TypeNameRequestor() {
				@Override
				public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames, String path) {
				}
			}, IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	interface IJobMatcher {

		boolean matches(Job job);

	}

	static class BuildJobMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new BuildJobMatcher();

		@Override
		public boolean matches(Job job) {
			return (job instanceof WorkspaceJob) || job.getClass().getName().matches("(.*\\.AutoBuild.*)")
					|| job.getClass().getName().endsWith("JREUpdateJob");
		}

	}

	static class BuildJobOffMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new BuildJobOffMatcher();

		@Override
		public boolean matches(Job job) {
			return job.getClass().getName().matches("(.*\\.AutoBuildOff.*)");
		}

	}

	static class ProjectRegistryRefreshJobMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new ProjectRegistryRefreshJobMatcher();

		@Override
		public boolean matches(Job job) {
			return job.getClass().getName().matches("org.eclipse.m2e.core.internal.project.registry.ProjectRegistryRefreshJob");
		}

	}

	static class RepositoryRegistryUpdateJobMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new RepositoryRegistryUpdateJobMatcher();

		@Override
		public boolean matches(Job job) {
			return job.getClass().getName().matches("org.eclipse.m2e.core.internal.repository.RepositoryRegistryUpdateJob");
		}

	}

	static class InitializeJobMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new InitializeJobMatcher();

		@Override
		public boolean matches(Job job) {
			return job.belongsTo(BaseInitHandler.JAVA_LS_INITIALIZATION_JOBS);
		}

	}

	static class LoadingGradleVersionJobMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new LoadingGradleVersionJobMatcher();

		@Override
		public boolean matches(Job job) {
			return job.getClass().getName().matches("org.eclipse.buildship.core.internal.util.gradle.PublishedGradleVersionsWrapper.LoadVersionsJob");
		}

	}

	static class DownloadSourcesJobMatcher implements IJobMatcher {

		public static final IJobMatcher INSTANCE = new DownloadSourcesJobMatcher();

		@Override
		public boolean matches(Job job) {
			return ("org.eclipse.m2e.jdt.internal.DownloadSourcesJob".equals(job.getClass().getName()));
		}

	}

}