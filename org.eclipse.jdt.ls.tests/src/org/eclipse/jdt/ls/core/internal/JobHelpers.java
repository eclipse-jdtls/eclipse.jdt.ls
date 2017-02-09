/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.internal.embedder.MavenExecutionContext;
import org.eclipse.m2e.core.internal.jobs.IBackgroundProcessingQueue;
import org.junit.Assert;

/**
 * Copied from m2e's org.eclipse.m2e.tests.common/src/org/eclipse/m2e/tests/common/JobHelpers.java
 *
 */
@SuppressWarnings("restriction")
public class JobHelpers {

	private JobHelpers() {
		//no instantiation
	}

	private static final int POLLING_DELAY = 10;

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

			Assert.assertFalse("Could not flush background processing queues: " + getProcessingQueues(jobManager), processed);
		} finally {
			jobManager.resume();
		}

		waitForBuildJobs();
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
					if(!status.isOK()) {
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
			if(job instanceof IBackgroundProcessingQueue) {
				queues.add((IBackgroundProcessingQueue) job);
			}
		}
		return queues;
	}

	private static void waitForBuildJobs() {
		waitForJobs(BuildJobMatcher.INSTANCE, 300000);
	}

	public static void waitForJobs(IJobMatcher matcher, int maxWaitMillis) {
		final long limit = System.currentTimeMillis() + maxWaitMillis;
		while(true) {
			Job job = getJob(matcher);
			if(job == null) {
				return;
			}
			boolean timeout = System.currentTimeMillis() > limit;
			Assert.assertFalse("Timeout while waiting for completion of job: " + job, timeout);
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

	public static interface IJobMatcher {

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

}