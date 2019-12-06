/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;

/**
 * @author Nikolas Komonen - nkomonen@redhat.com
 *
 */
public class JavaDocHTMLPathHandler {

	// absolute path to folder holding all extracted images
	public static final IPath EXTRACTED_JAR_IMAGES_FOLDER = JavaLanguageServerPlugin.getInstance().getStateLocation().append("extracted-jar-images/");

	public static final String[] tags = { "img" };

	public static final Pattern p = Pattern.compile("(src\\s*=\\s*['\"])");

	/**
	 * Returns true if the text is an HTML tag in the defined array of tag names in
	 * {@link JavaDocHTMLPathHandler#tags}
	 *
	 * @param text
	 * @return
	 */
	public static boolean containsHTMLTag(String text) {

		for (String tag : tags) {
			if (text.contains("<" + tag)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Given a {@link TextElement} that represents an HTML tag with a 'src'
	 * attribute, it will extract the image from the jar if necessary and copy it to
	 * the 'outputPath'.
	 *
	 * @param child
	 * @param fElement
	 * @return
	 */
	public static String getValidatedHTMLSrcAttribute(TextElement child, IJavaElement fElement) {

		//Check if current src attribute path needs to be validated
		String text = child.getText();
		int offsets[] = extractSourcePathFromHTMLTag(text);
		if (offsets == null) {
			return text;
		}
		String srcPath = text.substring(offsets[0], offsets[1]);

		if (isPathAbsolute(srcPath)) {
			return text; //Current path is good as is.
		}

		String fileName = Paths.get(srcPath).getFileName().toString();

		//Get the initial internal jar fragment, which is going to be the relative parent folder to the file
		IPackageFragment internalJarFragment = (IPackageFragment) fElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT);

		if (internalJarFragment == null) {
			return text;
		}

		//test if srcPath points to existing file relative to the fElement file.
		IResource fragmentResource = internalJarFragment.getResource();
		if (fragmentResource != null) {
			IPath absoluteFragmentPath = fragmentResource.getLocation();
			File absoluteFragmentFile = new File(absoluteFragmentPath.toPortableString() + "/" + srcPath);
			if (absoluteFragmentFile.exists()) {
				String uriString = ResourceUtils.fixURI(absoluteFragmentFile.toURI()).toString();
				return text.substring(0, offsets[0]) + uriString + text.substring(offsets[1]);
			}
		}

		//folder names are separated by '.'
		String fragmentName = internalJarFragment.getElementName();

		if (fragmentName == null || fragmentName.length() < 1) {
			return text;
		}

		String[] names = fragmentName.split("\\.");
		String fragmentPath = String.join("/", names) + "/";

		srcPath = srcPath.replace('\\', '/');

		String filePathRelativeToJar = fragmentPath + srcPath; // /relative/path/to/file.abc
		InputStream is = null; // file from jar (ZipEntry)
		JarFile jar = null;

		try {
			File currentJarPath = null;

			ZipEntry currentZipEntry = null;

			URL javadocJarBaseLocationURL = JavaDocLocations.getJavadocBaseLocation(internalJarFragment); //Absolute location of javadoc jar (not class or source jar)
			//Attempt to get file from javadoc jar
			if (javadocJarBaseLocationURL != null) {
				URI javadocJarBaseLocationURI = javadocJarBaseLocationURL.toURI();
				currentJarPath = getJarPathFromURI(javadocJarBaseLocationURI);
				if (currentJarPath != null) {
					jar = new JarFile(currentJarPath);
					currentZipEntry = jar.getEntry(filePathRelativeToJar);
				}
			}

			//No file was in the javadoc jar, try the source jar
			if (currentZipEntry == null) {
				currentJarPath = SourceJarLocations.getSourceJarPath(internalJarFragment); //Absolute location of source jar
				if (currentJarPath != null) {
					jar = new JarFile(currentJarPath);
					currentZipEntry = jar.getEntry(filePathRelativeToJar);
				}
			}

			if (jar == null || currentZipEntry == null) {
				return text; //File from source path could not be located in either jar
			}

			String jarRootName = internalJarFragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT).getElementName();
			if (jarRootName.endsWith(".jar")) {
				jarRootName = jarRootName.substring(0, jarRootName.length() - 4);
			}
			jarRootName += "/";

			IPath newOutputPath = EXTRACTED_JAR_IMAGES_FOLDER.append(jarRootName);
			newOutputPath = newOutputPath.append(fileName);
			File outputFile = newOutputPath.toFile();

			//Check if the file actually needs to be extracted
			if (!outputFile.exists()) {
				is = jar.getInputStream(currentZipEntry);
				extractFileTo(is, outputFile);
			} else {//Check if the file is outdated
				BasicFileAttributes existingOutputFileAttributes = Files.readAttributes(outputFile.toPath(), BasicFileAttributes.class);
				FileTime existingFileCreationTime = existingOutputFileAttributes.creationTime();
				BasicFileAttributes jarOutputFileAttributes = Files.readAttributes(currentJarPath.toPath(), BasicFileAttributes.class);

				FileTime jarFileCreationTime = jarOutputFileAttributes.creationTime();

				if (jarFileCreationTime.compareTo(existingFileCreationTime) > 0) {
					is = jar.getInputStream(currentZipEntry);
					extractFileTo(is, outputFile);
				}
			}

			//Insert new path into text
			return text.substring(0, offsets[0]) + ResourceUtils.fixURI(outputFile.toURI()).toString() + text.substring(offsets[1]);

		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to extract path from embedded jar with error message:", e);
			return text;
		} finally {
			//cleanup
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
			if (jar != null) {
				try {
					jar.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * Gets the position between the quotes of a src attribute. Will look for
	 * something similar to (src="...") and extract the path from inside.
	 *
	 * Start offset is at offsets[0], after the start quotation. End offset is at
	 * offsets[1], before the end quotation.
	 *
	 * Offsets are at '|':
	 *
	 * src="|nikolas/wrote/this|"
	 *
	 * If the src attribute cannot be found, null is returned.
	 *
	 * @param text
	 * @return int[] with start and end offset of src attribute value (excluding
	 *         quotations), else null.
	 */
	public static int[] extractSourcePathFromHTMLTag(String text) {
		Matcher m = p.matcher(text);
		if (m.find()) {
			int srcStartQuote = m.end();
			char quote = text.charAt(srcStartQuote - 1);
			int srcEndQuote = text.indexOf(quote, srcStartQuote);
			int[] offsets = { srcStartQuote, srcEndQuote };
			return offsets;
		}
		return null;
	}

	public static File getJarPathFromURI(URI uri) {
		String pathWithScheme = uri.getSchemeSpecificPart();
		String finalJarRootPath = pathWithScheme.substring(pathWithScheme.indexOf(':') + 1);

		//clean up/verify the jar path
		int actualJarIndex = finalJarRootPath.lastIndexOf(".jar");

		if (actualJarIndex == -1) {
			return null;
		}

		return new File(finalJarRootPath.substring(0, actualJarIndex + 4));
	}

	/**
	 * Checks if a given path is absolute. This path must be in the format of a URI
	 * (with scheme) or a Unix like path.
	 *
	 * eg:
	 *
	 * Linux: file:///home/nikolas/file.txt Windows:
	 * file:///C:/Users/Nikolas/file.txt
	 *
	 * If the path is not in the URI format, it will always return false since the
	 * scheme is missing.
	 *
	 * @param path
	 *            in format of URI
	 * @return true if path/URI is absolute
	 */
	public static boolean isPathAbsolute(String path) {
		try {

			URI uri = new URI(path);

			if (uri.isAbsolute()) {
				return true;
			}

			File f = new File(path);

			if (f.isAbsolute()) {
				return true;
			}

			//The case we are on Windows and we get a Unix-like path
			if (path.startsWith("/")) {
				return true;
			}

			return false;

		} catch (URISyntaxException e) {
			return false;
		}
	}

	/**
	 * Given an inputstream, outputs a file to the given path
	 *
	 * @param fileToExtract
	 * @param pathToExtractTo
	 * @return
	 */
	private static boolean extractFileTo(InputStream fileToExtract, File pathToExtractTo) {
		try {
			pathToExtractTo.getParentFile().mkdirs();
			Files.copy(fileToExtract, pathToExtractTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Failed to extract file with error message: ", e);
		}
		return false;
	}
}
