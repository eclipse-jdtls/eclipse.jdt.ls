package org.eclipse.jdt.ls.core.internal.filesystem;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
public class JDTLSFilesystemActivator implements BundleActivator {

	private static BundleContext context;

	static BundleContext getContext() {
		return context;
	}

	public void start(BundleContext bundleContext) throws Exception {
		JDTLSFilesystemActivator.context = bundleContext;
	}

	public void stop(BundleContext bundleContext) throws Exception {
		JDTLSFilesystemActivator.context = null;
	}

}
