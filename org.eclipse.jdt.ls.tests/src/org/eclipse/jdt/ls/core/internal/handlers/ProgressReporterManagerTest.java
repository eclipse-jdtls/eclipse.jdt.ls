/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.ProgressReport;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.StatusReport;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.ProgressParams;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ProgressReporterManagerTest {

	private ProgressReporterManager manager;

	@Mock
	private JavaLanguageClient client;

	@Mock
	private PreferenceManager preferenceManager;

	@Mock
	private ClientPreferences clientPreferences;

	@Before
	public void setup() {
		when(preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		when(clientPreferences.isProgressReportSupported()).thenReturn(true);
		manager = new ProgressReporterManager(client, preferenceManager);
	}

	@Test
	public void testReportThrottling() throws InterruptedException {
		manager.setReportThrottle(100);
		IProgressMonitor monitor = manager.getDefaultMonitor();
		monitor.beginTask("Some task", 10);
		for (int i = 0; i < 10; i++) {
			monitor.worked(1);
			Thread.sleep(40);
		}

		ArgumentCaptor<ProgressReport> captor = ArgumentCaptor.forClass(ProgressReport.class);
		verify(client, times(4)).sendProgressReport(captor.capture());

		List<ProgressReport> reports = captor.getAllValues();
		assertEquals(4, reports.size());
		assertEquals(0, reports.get(0).getWorkDone());
		assertEquals(4, reports.get(1).getWorkDone());
		assertEquals(7, reports.get(2).getWorkDone());
		assertEquals(10, reports.get(3).getWorkDone());
		monitor.done();
	}

	@Test
	public void testJobReporting() throws InterruptedException {
		manager.setReportThrottle(275);
		Job job = new Job("Test Job") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return null;
			}
		};
		IProgressMonitor monitor = manager.createMonitor(job);
		monitor.done();

		ArgumentCaptor<ProgressReport> captor = ArgumentCaptor.forClass(ProgressReport.class);
		verify(client, times(1)).sendProgressReport(captor.capture());

		List<ProgressReport> reports = captor.getAllValues();
		assertEquals(1, reports.size());
		ProgressReport report = reports.get(0);
		assertEquals("", report.getStatus());
		assertEquals(job.getName(), report.getTask());
		assertTrue(report.isComplete());
	}

	@Test
	public void testJobReporting_WithNotifyProgress() throws InterruptedException {
		when(clientPreferences.isProgressReportSupported()).thenReturn(false);
		manager.setReportThrottle(275);
		Job job = new Job("Test Job") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return null;
			}
		};
		IProgressMonitor monitor = manager.createMonitor(job);
		monitor.done();

		ArgumentCaptor<ProgressParams> captor = ArgumentCaptor.forClass(ProgressParams.class);
		verify(client, times(2)).notifyProgress(captor.capture());
	}

	@Test
	public void testMulticastJobReporting() throws InterruptedException {
		manager.setReportThrottle(275);
		Job job = new Job("Test Job") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return null;
			}
			@Override
			public boolean belongsTo(Object family) {
				return family == InitHandler.JAVA_LS_INITIALIZATION_JOBS;
			}
		};

		IProgressMonitor monitor = manager.createMonitor(job);
		monitor.done();

		ArgumentCaptor<StatusReport> captorStatus = ArgumentCaptor.forClass(StatusReport.class);
		verify(client, times(1)).sendStatusReport(captorStatus.capture());
		List<StatusReport> statusReports = captorStatus.getAllValues();
		assertEquals(1, statusReports.size());
		StatusReport statusReport = statusReports.get(0);
		assertEquals(ServiceStatus.Starting.name(), statusReport.getType());

		ArgumentCaptor<ProgressReport> captorProgress = ArgumentCaptor.forClass(ProgressReport.class);
		verify(client, times(1)).sendProgressReport(captorProgress.capture());
		List<ProgressReport> progressReports = captorProgress.getAllValues();
		assertEquals(1, progressReports.size());
		ProgressReport progressReport = progressReports.get(0);
		assertEquals("", progressReport.getStatus());
		assertEquals(job.getName(), progressReport.getTask());
		assertTrue(progressReport.isComplete());
	}

	@Test
	public void testStartupJobReporting() throws InterruptedException {
		manager.setReportThrottle(0);
		Job job = new Job("Startup job") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return null;
			}

			@Override
			public boolean belongsTo(Object family) {
				return InitHandler.JAVA_LS_INITIALIZATION_JOBS == family;
			}
		};

		IProgressMonitor monitor = manager.createMonitor(job);

		String taskName = "Do stuff";
		monitor.beginTask(taskName, 10);
		monitor.worked(5);

		ArgumentCaptor<StatusReport> captor = ArgumentCaptor.forClass(StatusReport.class);
		verify(client, times(2)).sendStatusReport(captor.capture());

		List<StatusReport> reports = captor.getAllValues();
		assertEquals(2, reports.size());

		StatusReport report1 = reports.get(0);
		assertEquals("0% Starting Java Language Server", report1.getMessage());
		assertEquals(ServiceStatus.Starting.name(), report1.getType());

		StatusReport report2 = reports.get(1);
		assertEquals("50% Starting Java Language Server", report2.getMessage());
		assertEquals(ServiceStatus.Starting.name(), report2.getType());
		monitor.done();
	}


}
