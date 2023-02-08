/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

 package org.eclipse.jdt.internal.corext.refactoring;

 import org.eclipse.jdt.core.ICompilationUnit;


 public class StubTypeContext {
	 private String fBeforeString;
	 private String fAfterString;
	 private final ICompilationUnit fCuHandle;

	 public StubTypeContext(ICompilationUnit cuHandle, String beforeString, String afterString) {
		 fCuHandle= cuHandle;
		 fBeforeString= beforeString;
		 fAfterString= afterString;
	 }

	 public ICompilationUnit getCuHandle() {
		 return fCuHandle;
	 }

	 public String getBeforeString() {
		 return fBeforeString;
	 }

	 public String getAfterString() {
		 return fAfterString;
	 }
 }
