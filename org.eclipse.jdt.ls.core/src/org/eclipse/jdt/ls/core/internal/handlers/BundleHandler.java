/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.io.File;
import java.net.MalformedURLException;

import org.eclipse.jdt.ls.core.internal.BundleRequestParams;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class BundleHandler {

	private static final String REFERENCE_PREFIX = "reference:";

	public Object handleBundle(BundleRequestParams param) {

		Object result = -1;

		String[] params = param.getParams();
		if (params.length < 1) {
			return -1;
		}
		BundleContext context = JavaLanguageServerPlugin.getBundleContext();
		String bundleLocation = params[1];

		switch (params[0]) {
			case "install":
				try {
					Bundle bundle = context.getBundle(bundleLocation);
					if (bundle != null) {
						bundle.update();
						result = bundle.getBundleId();
					} else {
						File f = new File(bundleLocation);
						bundle = context.installBundle(REFERENCE_PREFIX + f.toURI().toURL().toString());
						startBundle(bundle);
						result = bundle.getBundleId();
					}
				} catch (BundleException e) {
					JavaLanguageServerPlugin.logException("Install bundle failure ", e);
				} catch (MalformedURLException ex) {
					JavaLanguageServerPlugin.logException("Install bundle failure ", ex);
				}
				break;
			case "uninstall":
				context = JavaLanguageServerPlugin.getBundleContext();
				bundleLocation = params[1];
				try {
					Bundle bundle = context.getBundle(bundleLocation);
					if (bundle != null) {
						bundle.uninstall();
						result = 0;
					} else {
						result = -1;
					}
				} catch (BundleException e) {
					result = -1;
					JavaLanguageServerPlugin.logException("Start bundle failure ", e);
				}
				break;
			default:
				break;
		}

		return result;
	}

	private void startBundle(Bundle bundle) {
		if (bundle.getState() == Bundle.UNINSTALLED || bundle.getState() == Bundle.STARTING || bundle.getBundleId() == 0) {
			return;
		}
		try {
			bundle.start();
		} catch (BundleException e) {
			JavaLanguageServerPlugin.logException("Start bundle failure ", e);
		}
	}
}
