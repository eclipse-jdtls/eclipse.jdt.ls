/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.logback.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.spi.ContextAwareBase;

/**
 * Configure default Java LS logback preferences
 *
 * @author snjeza
 *
 */
public class JavaLsConfigurator extends ContextAwareBase implements Configurator {

	public JavaLsConfigurator() {
	}

	@Override
	public void configure(LoggerContext lc) {
		addInfo("Setting up default configuration.");
		boolean isDebug = Boolean.getBoolean("jdt.ls.debug");
		if (isDebug) {
			EclipseLogAppender eca = new EclipseLogAppender();
			eca.setContext(lc);
			eca.setName("JavaLS");
			eca.start();

			Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
			rootLogger.setLevel(Level.DEBUG);
			rootLogger.addAppender(eca);

			Logger httpLogger = lc.getLogger("org.apache.hc.client5.http");
			httpLogger.setLevel(Level.INFO);
			httpLogger.setAdditive(false);
		}
	}
}
