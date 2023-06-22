/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class LanguageServerApplication implements IApplication {

	private volatile boolean shutdown;
	private long parentProcessId;
	private final Object waitLock = new Object();

	private InputStream in;
	private PrintStream out;
	private PrintStream err;

	@Override
	public Object start(IApplicationContext context) throws Exception {
		prepareWorkspace();
		prepareStreams();
		JavaLanguageServerPlugin.startLanguageServer(this);
		synchronized (waitLock) {
			while (!shutdown) {
				try {
					context.applicationRunning();
					JavaLanguageServerPlugin.logInfo("Main thread is waiting");
					waitLock.wait();
				} catch (InterruptedException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
			}
		}
		return IApplication.EXIT_OK;
	}

	private static void prepareWorkspace() throws CoreException {
		try {
			Platform.getBundle(ResourcesPlugin.PI_RESOURCES).start(Bundle.START_TRANSIENT);
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IWorkspaceDescription description = workspace.getDescription();
			description.setAutoBuilding(false);
			workspace.setDescription(description);
		} catch (BundleException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
	}

	@Override
	public void stop() {
		synchronized (waitLock) {
			waitLock.notifyAll();
		}
	}

	public void exit() {
		shutdown = true;
		JavaLanguageServerPlugin.logInfo("Shutdown received... waking up main thread");
		synchronized (waitLock) {
			waitLock.notifyAll();
		}
	}

	public void setParentProcessId(long pid) {
		this.parentProcessId = pid;
	}

	/**
	 * @return the parentProcessId
	 */
	public long getParentProcessId() {
		return parentProcessId;
	}

	private void prepareStreams() {
		boolean isDebug = Boolean.getBoolean("jdt.ls.debug");
		in = System.in;
		out = System.out;
		err = System.err;
		System.setIn(new ByteArrayInputStream(new byte[0]));
		if (isDebug) {
			String id = "jdt.ls-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File workspaceFile = root.getRawLocation().makeAbsolute().toFile();
			File rootFile = new File(workspaceFile, ".metadata");
			rootFile.mkdirs();
			File outFile = new File(rootFile, ".out-" + id + ".log");
			try {
				FileOutputStream stdFileOut = new FileOutputStream(outFile);
				System.setOut(new PrintStream(stdFileOut));
				File errFile = new File(rootFile, ".error-" + id + ".log");
				FileOutputStream stdFileErr = new FileOutputStream(errFile);
				System.setErr(new PrintStream(stdFileErr));
			} catch (FileNotFoundException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		} else {
			System.setOut(new PrintStream(new ByteArrayOutputStream()));
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
		}
	}

	public InputStream getIn() {
		if (in == null) {
			prepareStreams();
		}
		return in;
	}

	public PrintStream getOut() {
		if (out == null) {
			prepareStreams();
		}
		return out;
	}

	public PrintStream getErr() {
		if (err == null) {
			prepareStreams();
		}
		return err;
	}

}
