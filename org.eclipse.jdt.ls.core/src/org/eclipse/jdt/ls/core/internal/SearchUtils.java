/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author snjeza
 *
 */
public class SearchUtils {

	public static Location searchOtherSources(IMember member) throws JavaModelException {
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (member == null || member.getClassFile() == null || preferenceManager == null
				|| !(preferenceManager.getPreferences().isAspectjSupportEnabled() || preferenceManager.getPreferences().isKotlinSupportEnabled() || preferenceManager.getPreferences().isGroovySupportEnabled())) {
			return null;
		}
		byte[] bytes;
		try {
			bytes = member.getClassFile().getBytes();
		} catch (JavaModelException e) {
			// ignore
			return null;
		}
		if (bytes != null) {
			final String[] sourceFile = new String[2];
			ClassReader classReader = new ClassReader(bytes);
			classReader.accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public void visitSource(String source, String debug) {
					sourceFile[1] = source;
				}

				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					sourceFile[0] = name;
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
			if (sourceFile[0] != null && sourceFile[1] != null) {
				IJavaProject javaProject = member.getJavaProject();
				for (IClasspathEntry entry : javaProject.getRawClasspath()) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						IPath packageName = new Path(sourceFile[0]).removeLastSegments(1);
						IPath sourcePath = entry.getPath().append(packageName).append(sourceFile[1]);
						IResource resource = javaProject.getProject().findMember(sourcePath.removeFirstSegments(1));
						if (resource instanceof IFile) {
							String uri = JDTUtils.getFileURI(resource);
							String elementSignature[] = { null };
							String elementName = member.getElementName();
							int[] elementLine = { 0 };
							boolean[] isField = { false };
							boolean[] isType = { false };
							if (member instanceof IField field) {
								elementSignature[0] = field.getTypeSignature();
								isField[0] = true;
							} else if (member instanceof IMethod method) {
								elementSignature[0] = method.getSignature();
							} else if (member instanceof IType) {
								isType[0] = true;
							}
							if (elementSignature[0] != null || isType[0]) {
								elementSignature[0] = elementSignature[0] == null ? null : elementSignature[0].replace('.', '/');
								classReader.accept(new ClassVisitor(Opcodes.ASM9) {

									/* (non-Javadoc)
									 * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
									 */
									@Override
									public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
										if (!isType[0] && !isField[0] && elementName.equals(name) && elementSignature[0].equals(descriptor)) {
											return new MethodVisitor(Opcodes.ASM9) {

												/* (non-Javadoc)
												 * @see org.objectweb.asm.MethodVisitor#visitLineNumber(int, org.objectweb.asm.Label)
												 */
												@Override
												public void visitLineNumber(int line, Label start) {
													elementLine[0] = line;
												}

											};
										}
										if (!isType[0] && isField[0] && (name.equals("<init>") || name.equals("<clinit>"))) {
											return new MethodVisitor(Opcodes.ASM9) {

												/* (non-Javadoc)
												 * @see org.objectweb.asm.MethodVisitor#visitLineNumber(int, org.objectweb.asm.Label)
												 */
												@Override
												public void visitLineNumber(int line, Label start) {
													elementLine[0] = line;
												}

											};
										}
										if (isType[0]) {
											return new MethodVisitor(Opcodes.ASM9) {
												@Override
												public void visitLineNumber(int line, Label start) {
													if (elementLine[0] == 0 || line < elementLine[0]) {
														elementLine[0] = line;
													}
												}
											};
										}
										if (!isType[0] && !isField[0] && name.equals("<init>")) {
											return new MethodVisitor(Opcodes.ASM9) {
												@Override
												public void visitLineNumber(int line, Label start) {
													if (elementLine[0] == 0) {
														elementLine[0] = line;
													}
												}
											};
										}
										return null;
									}
								}, ClassReader.SKIP_FRAMES);
							}
							int character = 0;
							if (elementLine[0] > 0) {
								if (resource instanceof IFile file) {
									try (InputStream is = file.getContents(); BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
										String line;
										int currentLine = 1;
										int start = Math.max(1, elementLine[0] - 2);
										int end = elementLine[0];
										while ((line = reader.readLine()) != null) {
											if (currentLine >= start && currentLine <= end) {
												int col = line.indexOf(elementName);
												if (col != -1) {
													character = col;
													break;
												}
											}
											if (currentLine > end) {
												break;
											}
											currentLine++;
										}
									} catch (Exception e) {
										// ignore
									}
								}
								elementLine[0]--;
							}
							Position position = new Position(elementLine[0], character);
							Range range = new Range(position, position);
							return new Location(ResourceUtils.toClientUri(uri), range);
						}
					}
				}
			}
		}
		return null;
	}

}
