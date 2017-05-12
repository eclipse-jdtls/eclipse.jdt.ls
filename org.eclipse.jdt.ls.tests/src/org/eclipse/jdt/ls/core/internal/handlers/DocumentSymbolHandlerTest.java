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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * @author snjeza
 */
public class DocumentSymbolHandlerTest extends AbstractProjectsManagerBasedTest {
	private IProject project;
	private DocumentSymbolHandler handler;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		handler = new DocumentSymbolHandler();
	}

	@Test
	public void testDocumentSymbolHandler() throws Exception {
		testClass("java.util.LinkedHashMap");
		testClass("java.util.HashMap");
		testClass("java.util.Set");
	}

	@Test
	public void testSyntheticMember() throws Exception {
		String className = "java.util.zip.ZipFile";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid name",
					symbol.getName().startsWith("access$"));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + "- invalid name",
					symbol.getName().equals("<clinit>"));
		}
	}

	private void testClass(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		List<? extends SymbolInformation> symbols = getSymbols(className);
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
		}
	}

	private List<? extends SymbolInformation> getSymbols(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		DocumentSymbolParams params = new DocumentSymbolParams();
		params.setTextDocument(identifier);
		CompletableFuture<List<? extends SymbolInformation>> future = handler.documentSymbol(params);
		List<? extends SymbolInformation> symbols = future.get();
		assertTrue(symbols.size() > 0);
		return symbols;
	}

	private boolean isValid(Range range) {
		return range != null && isValid(range.getStart()) && isValid(range.getEnd());
	}

	private boolean isValid(Position position) {
		return position != null && position.getLine() >= 0 && position.getCharacter() >= 0;
	}

	private static String getURI(IProject project, String className) throws JavaModelException, UnsupportedEncodingException {
		IJavaProject javaProject = JavaCore.create(project);
		javaProject.open(new NullProgressMonitor());
		String packageName = className.substring(0, className.lastIndexOf("."));
		String cName = className.substring(packageName.length() + 1, className.length()) + ".class";
		String classFileName = "/" + className.replaceAll("\\.", "/") + ".class";
		IPackageFragmentRoot[] packageFragmentRoots = javaProject.getAllPackageFragmentRoots();
		for (IPackageFragmentRoot packageFragmentRoot : packageFragmentRoots) {
			if (packageFragmentRoot.isArchive()) {
				IPackageFragment packageFragment = packageFragmentRoot.getPackageFragment(packageName);
				if (packageFragment != null && packageFragment.exists()) {
					IClassFile classFile;
					try {
						classFile = packageFragment.getClassFile(cName);
					} catch (Exception e) {
						continue;
					}
					if (classFile.exists()) {
						String ret1 = String.format("jdt://contents/%s%s?=", packageFragmentRoot.getElementName(),
								classFileName);
						String ret2 = String.format("%s/%s<%s(%s", project.getName(), packageFragmentRoot.getPath(),
								packageName, cName);
						return ret1 + URLEncoder.encode(ret2, "UTF-8");
					}
				}
			}
		}
		return null;
	}

}
