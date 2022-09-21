/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.managers;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.internal.core.LaunchConfigurationInfo;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * JavaLaunchConfigurationInfo
 */
public class JavaLaunchConfigurationInfo extends LaunchConfigurationInfo {

	private static final String JAVA_APPLICATION_LAUNCH = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
		+ "<launchConfiguration type=\"org.eclipse.jdt.launching.localJavaApplication\">\n"
		+ "<listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_PATHS\">\n"
		+ "</listAttribute>\n"
		+ "<listAttribute key=\"org.eclipse.debug.core.MAPPED_RESOURCE_TYPES\">\n"
		+ "</listAttribute>\n"
		+ "</launchConfiguration>";
			
	public JavaLaunchConfigurationInfo(String scope) {
		super();

		try {
			DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			parser.setErrorHandler(new DefaultHandler());
			StringReader reader = new StringReader(JAVA_APPLICATION_LAUNCH);
			InputSource source = new InputSource(reader);
			Element root = parser.parse(source).getDocumentElement();
			initializeFromXML(root);
		} catch (ParserConfigurationException | SAXException | IOException | CoreException e) {
			// do nothing
		}
	}
}