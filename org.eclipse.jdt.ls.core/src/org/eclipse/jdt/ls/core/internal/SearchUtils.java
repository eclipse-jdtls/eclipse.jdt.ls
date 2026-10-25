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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;

import java.util.Arrays;

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
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.objectweb.asm.AnnotationVisitor;
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

	/*
	 * The class finds the source file and location of types, fields and methods from *.class file.
	 * Scala sometimes creates an additional class ending with $ that contains data about the source file and location.
	 * This class recognizes scala classes and searches for <ClassName>$ class if it exists.
	 */
	private static final class SearchClassVisitor extends ClassVisitor {
		private int elementLine;
		private String elementName;
		private boolean isType;
		private boolean isField;
		private String elementSignature;
		private boolean isScala;

		private SearchClassVisitor(int api, int elementLine, String elementName, boolean isType, boolean isField, String elementSignature, boolean isScala) {
			super(api);
			this.elementLine = elementLine;
			this.elementName = elementName;
			this.isType = isType;
			this.isField = isField;
			this.elementSignature = elementSignature;
			this.isScala = isScala;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (descriptor.contains("Lscala/reflect/ScalaSignature;") || descriptor.contains("Lscala/ScalaContext;")) {
				isScala = true;
			}
			return super.visitAnnotation(descriptor, visible);
		}

		/* (non-Javadoc)
		 * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
		 */
		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if (!isType && !isField && elementName.equals(name) && elementSignature.equals(descriptor)) {
				return new MethodVisitor(Opcodes.ASM9) {

					/* (non-Javadoc)
					 * @see org.objectweb.asm.MethodVisitor#visitLineNumber(int, org.objectweb.asm.Label)
					 */
					@Override
					public void visitLineNumber(int line, Label start) {
						elementLine = line;
					}

				};
			}
			if (!isType && isField && (name.equals("<init>") || name.equals("<clinit>"))) {
				return new MethodVisitor(Opcodes.ASM9) {

					/* (non-Javadoc)
					 * @see org.objectweb.asm.MethodVisitor#visitLineNumber(int, org.objectweb.asm.Label)
					 */
					@Override
					public void visitLineNumber(int line, Label start) {
						elementLine = line;
					}

				};
			}
			if (isType) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitLineNumber(int line, Label start) {
						if (elementLine == 0 || line < elementLine) {
							elementLine = line;
						}
					}
				};
			}
			if (!isType && !isField && name.equals("<init>")) {
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public void visitLineNumber(int line, Label start) {
						if (elementLine == 0) {
							elementLine = line;
						}
					}
				};
			}
			return null;
		}
	}

	private static final SearchParticipant[] EMPTY_PARTICIPANTS = new SearchParticipant[0];

	/**
	 * Returns contributed search participants, excluding the default Java
	 * participant. {@link SearchEngine#getSearchParticipants()} places the
	 * default at index 0; this method returns the remainder.
	 */
	public static SearchParticipant[] getContributedSearchParticipants() {
		SearchParticipant[] all = SearchEngine.getSearchParticipants();
		return all.length > 1
				? Arrays.copyOfRange(all, 1, all.length)
				: EMPTY_PARTICIPANTS;
	}

	public static Location searchOtherSources(IMember member) throws JavaModelException {
		PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (member == null || member.getClassFile() == null || preferenceManager == null || !(preferenceManager.getPreferences().isAspectjSupportEnabled() || preferenceManager.getPreferences().isKotlinSupportEnabled()
				|| preferenceManager.getPreferences().isGroovySupportEnabled() || preferenceManager.getPreferences().isScalaSupportEnabled())) {
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
							String elementSignature = null;
							String elementName = member.getElementName();
							int elementLine = 0;
							boolean isField = false;
							boolean isType = false;
							boolean isScala = false;
							if (member instanceof IField field) {
								elementSignature = field.getTypeSignature();
								isField = true;
							} else if (member instanceof IMethod method) {
								elementSignature = method.getSignature();
							} else if (member instanceof IType) {
								isType = true;
							}
							if (elementSignature != null || isType) {
								elementSignature = elementSignature == null ? null : elementSignature.replace('.', '/');
								SearchClassVisitor classVisitor = new SearchClassVisitor(Opcodes.ASM9, elementLine, elementName, isType, isField, elementSignature, isScala);
								classReader.accept(classVisitor, ClassReader.SKIP_FRAMES);
								if (classVisitor.isScala && classVisitor.elementLine == 0 && !classVisitor.elementName.endsWith("$")) {
									File classFile = member.getPath().toFile();
									File dollarFile = new File(classFile.getParent(), classFile.getName().replace(".class", "$.class"));
									if (dollarFile.exists()) {
										try {
											bytes = Files.readAllBytes(dollarFile.toPath());
											ClassReader reader = new ClassReader(bytes);
											reader.accept(classVisitor, ClassReader.SKIP_FRAMES);
										} catch (IOException e) {
											JavaLanguageServerPlugin.logException(e);
										}
									}
								}
								elementLine = classVisitor.elementLine;
							}
							int character = 0;
							if (elementLine > 0) {
								if (resource instanceof IFile file) {
									try (InputStream is = file.getContents(); BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
										String line;
										int currentLine = 1;
										int start = Math.max(1, elementLine - 2);
										int end = elementLine;
										while ((line = reader.readLine()) != null) {
											if (currentLine >= start && currentLine <= end) {
												int col = line.indexOf(elementName);
												if (col != -1) {
													character = col;
													elementLine = currentLine;
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
								elementLine--;
							}
							Position position = new Position(elementLine, character);
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
