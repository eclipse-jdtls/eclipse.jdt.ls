/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.internal.gradle.checksums;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

/**
 *
 * @author snjeza
 *
 */
public class DownloadChecksumJob extends Job {

	public static final String WRAPPER_VALIDATOR_JOBS = "WrapperValidatorJobs";

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

	public DownloadChecksumJob() {
		super("Download Gradle Wrapper checksums");
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected IStatus run(IProgressMonitor monitor) {
		int totalWork = 2 * queue.size();
		SubMonitor subMonitor = SubMonitor.convert(monitor, totalWork);
		while (!queue.isEmpty() && !monitor.isCanceled()) {
			String urlStr = queue.poll();
			URL url;
			try {
				url = new URL(urlStr);
			} catch (MalformedURLException e1) {
				JavaLanguageServerPlugin.logInfo("Invalid wrapper URL " + urlStr);
				continue;
			}
			subMonitor.setTaskName(url.toString());
			final HttpURLConnection connection;
			try {
				connection = (HttpURLConnection) url.openConnection();
				connection.setConnectTimeout(30000);
				connection.setReadTimeout(30000);
			} catch (IOException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
				continue;
			}
			try (AutoCloseable closer = (() -> connection.disconnect()); InputStreamReader reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8);) {
				String sha256 = CharStreams.toString(reader);
				File sha256File = new File(WrapperValidator.getSha256CacheFile(), WrapperValidator.getFileName(urlStr));
				write(sha256File, sha256);
				WrapperValidator.allow(sha256);
				subMonitor.worked(2);
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException("Cannot download Gradle sha256 checksum: " + url.toString(), e);
				continue;
			}
		}
		subMonitor.done();
		return Status.OK_STATUS;
	}

	public void add(String urlStr) {
		queue.add(urlStr);
	}

	private void write(File sha256File, String sha256) {
		try {
			Files.write(Paths.get(sha256File.getAbsolutePath()), sha256.getBytes());
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
	 */
	@Override
	public boolean belongsTo(Object family) {
		return WRAPPER_VALIDATOR_JOBS.equals(family);
	}

	public boolean isEmpty() {
		return queue.isEmpty();
	}
}

