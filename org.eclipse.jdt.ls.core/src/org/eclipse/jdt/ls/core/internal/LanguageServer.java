/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class LanguageServer implements IApplication {

  private volatile boolean shutdown;
  private long parentProcessId;
  private final Object waitLock = new Object();

	@Override
	public Object start(IApplicationContext context) throws Exception {

    JavaLanguageServerPlugin.startLanguageServer(this);
    synchronized(waitLock){
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

	@Override
	public void stop() {
    synchronized(waitLock){
      waitLock.notifyAll();
    }
	}

	public void exit() {
    shutdown = true;
    JavaLanguageServerPlugin.logInfo("Shutdown received... waking up main thread");
    synchronized(waitLock){
      waitLock.notifyAll();
    }
  }

	public void setParentProcessId(long pid) {
		this.parentProcessId = pid;
	}

	/**
	 * @return the parentProcessId
	 */
	long getParentProcessId() {
		return parentProcessId;
	}
}
