/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Based on org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatterOptions;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * load/store profiles from/to profilesKey
 */
public class FormatterManager {

	/** The default encoding to use */
	public static final String ENCODING= "UTF-8"; //$NON-NLS-1$

	protected static final String VERSION_KEY_SUFFIX= ".version"; //$NON-NLS-1$

	public static final String CODE_FORMATTER_PROFILE_KIND = "CodeFormatterProfile"; //$NON-NLS-1$

	private final static String FORMATTER_OPTION_PREFIX = JavaCore.PLUGIN_ID + ".formatter"; //$NON-NLS-1$

	/**
	 * A SAX event handler to parse the xml format for profiles.
	 */
	private final static class ProfileDefaultHandler extends DefaultHandler {

		private String profileName;
		private String fName;
		private Map<String, String> fSettings;
		private String fKind;
		private boolean reading = false;

		/**
		 * @param profileName
		 */
		private ProfileDefaultHandler(String profileName) {
			this.profileName = profileName;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

			if (qName.equals(XML_NODE_SETTING)) {
				if (reading) {
					final String key = attributes.getValue(XML_ATTRIBUTE_ID);
					final String value = attributes.getValue(XML_ATTRIBUTE_VALUE);
					fSettings.put(key, value);
				}

			} else if (qName.equals(XML_NODE_PROFILE)) {
				fName= attributes.getValue(XML_ATTRIBUTE_NAME);
				if (profileName == null || profileName.equals(fName)) {
					reading = true;
					fKind = attributes.getValue(XML_ATTRIBUTE_PROFILE_KIND);
					if (fKind == null) {
						fKind = CODE_FORMATTER_PROFILE_KIND;
					}
					fSettings = new HashMap<>(200);
				}
			}
			else if (qName.equals(XML_NODE_ROOT)) {
				// ignore
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			if (qName.equals(XML_NODE_PROFILE)) {
				if (reading) {
					reading = false;
				}
			}
		}

		private Map<String, String> getSettings() {
			return fSettings;
		}

	}

	/**
	 * Identifiers for the XML file.
	 */
	private final static String XML_NODE_ROOT= "profiles"; //$NON-NLS-1$
	private final static String XML_NODE_PROFILE= "profile"; //$NON-NLS-1$
	private final static String XML_NODE_SETTING= "setting"; //$NON-NLS-1$

	private final static String XML_ATTRIBUTE_ID= "id"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_NAME= "name"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_PROFILE_KIND= "kind"; //$NON-NLS-1$
	private final static String XML_ATTRIBUTE_VALUE= "value"; //$NON-NLS-1$

	public FormatterManager() {
	}

	/**
	 * Read the available profiles from the internal XML file and return them as
	 * collection or <code>null</code> if the file is not a profile file.
	 *
	 * @param file
	 *            The file to read from
	 * @return returns a map of formatter options or <code>null</code>
	 * @throws CoreException
	 */
	public Map<String, String> readSettingsFromFile(File file, String profileName) throws CoreException {
		try (FileInputStream reader = new FileInputStream(file)) {
			return readSettingsFromStream(new InputSource(reader), profileName);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.WARNING, IConstants.PLUGIN_ID, e.getMessage(), e));
		}
	}

	/**
	 * Load profiles from a XML stream and add them to a map or <code>null</code> if the source is not a profile store.
	 * @param inputSource The input stream
	 * @return returns a list of <code>CustomProfile</code> or <code>null</code>
	 * @throws CoreException
	 */
	public static Map<String, String> readSettingsFromStream(InputSource inputSource, String profileName) throws CoreException {

		final ProfileDefaultHandler handler = new ProfileDefaultHandler(profileName);
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			final SAXParser parser = factory.newSAXParser();
			parser.parse(inputSource, handler);
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.WARNING, IConstants.PLUGIN_ID, e.getMessage(), e));
		}
		return handler.getSettings();
	}

	public static void configureFormatter(Preferences preferences) {
		URI formatterUri = preferences.getFormatterAsURI();
		Map<String, String> options = null;
		if (formatterUri != null) {
			try (InputStream inputStream = formatterUri.toURL().openStream()) {
				InputSource inputSource = new InputSource(inputStream);
				String profileName = preferences.getFormatterProfileName();
				options = FormatterManager.readSettingsFromStream(inputSource, profileName);
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		}
		if (options != null && !options.isEmpty()) {
			setFormattingOptions(preferences, options);
		} else {
			Map<String, String> defaultOptions = DefaultCodeFormatterOptions.getEclipseDefaultSettings().getMap();
			PreferenceManager.initializeJavaCoreOptions();
			Hashtable<String, String> javaOptions = JavaCore.getOptions();
			defaultOptions.forEach((k, v) -> {
				javaOptions.put(k, v);
			});
			preferences.updateTabSizeInsertSpaces(javaOptions);
			JavaCore.setOptions(javaOptions);
		}
	}

	private static void setFormattingOptions(Preferences preferences, Map<String, String> options) {
		Map<String, String> defaultOptions = DefaultCodeFormatterOptions.getEclipseDefaultSettings().getMap();
		defaultOptions.putAll(options);
		Hashtable<String, String> javaOptions = JavaCore.getOptions();
		defaultOptions.entrySet().stream().filter(p -> p.getKey().startsWith(FORMATTER_OPTION_PREFIX)).forEach(p -> {
			javaOptions.put(p.getKey(), p.getValue());
		});
		preferences.updateTabSizeInsertSpaces(javaOptions);
		JavaCore.setOptions(javaOptions);
	}

}
