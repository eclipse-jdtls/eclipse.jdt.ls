/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.SerialVersionHashOperation
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.fix;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.util.IFieldInfo;
import org.eclipse.jdt.core.util.IInnerClassesAttribute;
import org.eclipse.jdt.core.util.IInnerClassesAttributeEntry;
import org.eclipse.jdt.core.util.IMethodInfo;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

/**
 * Proposal for a hashed serial version id.
 *
 * @since 3.1
 */
public final class SerialVersionHashOperation extends AbstractSerialVersionOperation {

	private static final String STATIC_CLASS_INITIALIZER = "<clinit>"; //$NON-NLS-1$

	public static Long calculateSerialVersionId(ITypeBinding typeBinding, final IProgressMonitor monitor) throws CoreException, IOException {
		try {
			IFile classfileResource = getClassfile(typeBinding);
			if (classfileResource == null) {
				return null;
			}

			InputStream contents = classfileResource.getContents();
			try {
				IClassFileReader cfReader = ToolFactory.createDefaultClassFileReader(contents, IClassFileReader.ALL);
				if (cfReader != null) {
					return calculateSerialVersionId(cfReader);
				}
			} finally {
				contents.close();
			}
			return null;
		} finally {
			if (monitor != null) {
				monitor.done();
			}
		}
	}

	private static String getClassName(char[] name) {
		return new String(name).replace('/', '.');
	}

	private static Long calculateSerialVersionId(IClassFileReader cfReader) throws IOException {
		// implementing algorithm specified on http://download.oracle.com/javase/6/docs/platform/serialization/spec/class.html#4100

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		DataOutputStream doos = new DataOutputStream(os);
		doos.writeUTF(getClassName(cfReader.getClassName())); // class name
		int mod = getClassModifiers(cfReader);
		//		System.out.println(Integer.toHexString(mod) + ' ' + Flags.toString(mod));

		int classModifiers = mod & (Flags.AccPublic | Flags.AccFinal | Flags.AccInterface | Flags.AccAbstract);

		doos.writeInt(classModifiers); // class modifiers
		char[][] interfaces = getSortedInterfacesNames(cfReader);
		for (int i = 0; i < interfaces.length; i++) {
			doos.writeUTF(getClassName(interfaces[i]));
		}
		IFieldInfo[] sortedFields = getSortedFields(cfReader);
		for (int i = 0; i < sortedFields.length; i++) {
			IFieldInfo curr = sortedFields[i];
			int flags = curr.getAccessFlags();
			if (!Flags.isPrivate(flags) || (!Flags.isStatic(flags) && !Flags.isTransient(flags))) {
				doos.writeUTF(new String(curr.getName()));
				doos.writeInt(flags & (Flags.AccPublic | Flags.AccPrivate | Flags.AccProtected | Flags.AccStatic | Flags.AccFinal | Flags.AccVolatile | Flags.AccTransient)); // field modifiers
				doos.writeUTF(new String(curr.getDescriptor()));
			}
		}
		if (hasStaticClassInitializer(cfReader)) {
			doos.writeUTF(STATIC_CLASS_INITIALIZER);
			doos.writeInt(Flags.AccStatic);
			doos.writeUTF("()V"); //$NON-NLS-1$
		}
		IMethodInfo[] sortedMethods = getSortedMethods(cfReader);
		for (int i = 0; i < sortedMethods.length; i++) {
			IMethodInfo curr = sortedMethods[i];
			int flags = curr.getAccessFlags();
			if (!Flags.isPrivate(flags) && !curr.isClinit()) {
				doos.writeUTF(new String(curr.getName()));
				doos.writeInt(flags & (Flags.AccPublic | Flags.AccPrivate | Flags.AccProtected | Flags.AccStatic | Flags.AccFinal | Flags.AccSynchronized | Flags.AccNative | Flags.AccAbstract | Flags.AccStrictfp)); // method modifiers
				doos.writeUTF(getClassName(curr.getDescriptor()));
			}
		}
		doos.flush();
		return computeHash(os.toByteArray());
	}

	private static int getClassModifiers(IClassFileReader cfReader) {
		IInnerClassesAttribute innerClassesAttribute = cfReader.getInnerClassesAttribute();
		if (innerClassesAttribute != null) {
			IInnerClassesAttributeEntry[] entries = innerClassesAttribute.getInnerClassAttributesEntries();
			for (int i = 0; i < entries.length; i++) {
				IInnerClassesAttributeEntry entry = entries[i];
				char[] innerClassName = entry.getInnerClassName();
				if (innerClassName != null) {
					if (CharOperation.equals(cfReader.getClassName(), innerClassName)) {
						return entry.getAccessFlags();
					}
				}
			}
		}
		return cfReader.getAccessFlags();
	}

	private static Long computeHash(byte[] bytes) {
		try {
			byte[] sha = MessageDigest.getInstance("SHA-1").digest(bytes); //$NON-NLS-1$
			if (sha.length >= 8) {
				long hash = 0;
				for (int i = 7; i >= 0; i--) {
					hash = (hash << 8) | (sha[i] & 0xFF);
				}
				return Long.valueOf(hash);
			}
		} catch (NoSuchAlgorithmException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return null;
	}

	private static char[][] getSortedInterfacesNames(IClassFileReader cfReader) {
		char[][] interfaceNames = cfReader.getInterfaceNames();
		Arrays.sort(interfaceNames, new Comparator<char[]>() {
			@Override
			public int compare(char[] o1, char[] o2) {
				return CharOperation.compareTo(o1, o2);
			}
		});
		return interfaceNames;
	}

	private static IFieldInfo[] getSortedFields(IClassFileReader cfReader) {
		IFieldInfo[] allFields = cfReader.getFieldInfos();
		Arrays.sort(allFields, new Comparator<IFieldInfo>() {
			@Override
			public int compare(IFieldInfo o1, IFieldInfo o2) {
				return CharOperation.compareTo(o1.getName(), o2.getName());
			}
		});
		return allFields;
	}

	private static boolean hasStaticClassInitializer(IClassFileReader cfReader) {
		IMethodInfo[] methodInfos = cfReader.getMethodInfos();
		for (int i = 0; i < methodInfos.length; i++) {
			if (methodInfos[i].isClinit()) {
				return true;
			}
		}
		return false;
	}

	private static IMethodInfo[] getSortedMethods(IClassFileReader cfReader) {
		IMethodInfo[] allMethods = cfReader.getMethodInfos();
		Arrays.sort(allMethods, new Comparator<IMethodInfo>() {
			@Override
			public int compare(IMethodInfo mi1, IMethodInfo mi2) {
				if (mi1.isConstructor() != mi2.isConstructor()) {
					return mi1.isConstructor() ? -1 : 1;
				} else if (mi1.isConstructor()) {
					return 0;
				}
				int res = CharOperation.compareTo(mi1.getName(), mi2.getName());
				if (res != 0) {
					return res;
				}
				return CharOperation.compareTo(mi1.getDescriptor(), mi2.getDescriptor());
			}
		});
		return allMethods;
	}

	private static IFile getClassfile(ITypeBinding typeBinding) throws CoreException {
		// bug 191943
		IType type = (IType) typeBinding.getJavaElement();
		if (type == null || type.getCompilationUnit() == null || type.getJavaProject() == null || ProjectsManager.DEFAULT_PROJECT_NAME.equals(type.getJavaProject().getProject().getName())) {
			return null;
		}

		IRegion region = JavaCore.newRegion();
		region.add(type.getCompilationUnit());

		String name = typeBinding.getBinaryName();
		if (name != null) {
			int packStart = name.lastIndexOf('.');
			if (packStart != -1) {
				name = name.substring(packStart + 1);
			}
		} else {
			throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, CorrectionMessages.SerialVersionHashOperation_error_classnotfound));
		}

		name += ".class"; //$NON-NLS-1$

		IResource[] classFiles = JavaCore.getGeneratedResources(region, false);
		for (int i = 0; i < classFiles.length; i++) {
			IResource resource = classFiles[i];
			if (resource.getType() == IResource.FILE && resource.getName().equals(name) && resource.exists()) {
				try (InputStream contents = ((IFile) resource).getContents()) {
				} catch (Exception e) {
					continue;
				}
				return (IFile) resource;
			}
		}
		return null;
		// throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, CorrectionMessages.SerialVersionHashOperation_error_classnotfound));
	}

	private final ICompilationUnit fCompilationUnit;

	public SerialVersionHashOperation(ICompilationUnit unit, ASTNode[] nodes) {
		super(unit, nodes);
		fCompilationUnit = unit;
	}

	@Override
	protected boolean addInitializer(final VariableDeclarationFragment fragment, final ASTNode declarationNode) {
		Assert.isNotNull(fragment);
		try {
			String id = computeId(declarationNode, new NullProgressMonitor());
			fragment.setInitializer(fragment.getAST().newNumberLiteral(id));

		} catch (InterruptedException exception) {
			// Do nothing
		}
		return true;
	}

	@Override
	protected void addLinkedPositions(ASTRewrite rewrite, VariableDeclarationFragment fragment, LinkedProposalModelCore positionGroups) {
		//Do nothing
	}

	private String computeId(final ASTNode declarationNode, final IProgressMonitor monitor) throws InterruptedException {
		Assert.isNotNull(monitor);
		long serialVersionID = SERIAL_VALUE;
		try {
			monitor.beginTask(CorrectionMessages.SerialVersionHashOperation_computing_id, 200);
			final IJavaProject project = fCompilationUnit.getJavaProject();
			final IPath path = fCompilationUnit.getResource().getFullPath();
			ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
			try {
				bufferManager.connect(path, LocationKind.IFILE, new SubProgressMonitor(monitor, 10));
				if (monitor.isCanceled()) {
					throw new InterruptedException();
				}

				//				final ITextFileBuffer buffer= bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
				//				if (buffer.isDirty() && buffer.isStateValidated() && buffer.isCommitable() && displayYesNoMessage(CorrectionMessages.SerialVersionHashOperation_save_caption, CorrectionMessages.SerialVersionHashOperation_save_message)) {
				//					buffer.commit(new SubProgressMonitor(monitor, 20), true);
				//				} else {
				//					monitor.worked(20);
				//				}

				if (monitor.isCanceled()) {
					throw new InterruptedException();
				}
			} finally {
				bufferManager.disconnect(path, LocationKind.IFILE, new SubProgressMonitor(monitor, 10));
			}
			project.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new SubProgressMonitor(monitor, 60));
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}

			ITypeBinding typeBinding = getTypeBinding(declarationNode);
			if (typeBinding != null) {
				Long id = calculateSerialVersionId(typeBinding, new SubProgressMonitor(monitor, 100));
				if (id != null) {
					serialVersionID = id.longValue();
				}
			}
		} catch (CoreException exception) {
			JavaLanguageServerPlugin.logException(exception.getMessage(), exception);
		} catch (IOException exception) {
			JavaLanguageServerPlugin.logException(exception.getMessage(), exception);
		} finally {
			monitor.done();
		}
		return serialVersionID + LONG_SUFFIX;
	}

	private static ITypeBinding getTypeBinding(final ASTNode parent) {
		if (parent instanceof AbstractTypeDeclaration) {
			final AbstractTypeDeclaration declaration = (AbstractTypeDeclaration) parent;
			return declaration.resolveBinding();
		} else if (parent instanceof AnonymousClassDeclaration) {
			final AnonymousClassDeclaration declaration = (AnonymousClassDeclaration) parent;
			return declaration.resolveBinding();
		} else if (parent instanceof ParameterizedType) {
			final ParameterizedType type = (ParameterizedType) parent;
			return type.resolveBinding();
		}
		return null;
	}
}
