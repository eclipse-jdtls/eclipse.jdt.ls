/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.manipulation.Messages;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

import com.google.gson.JsonSyntaxException;

public class SourceAttachmentCommand {

	public static SourceAttachmentResult resolveSourceAttachment(List<Object> arguments, IProgressMonitor monitor) {
		if (arguments == null || arguments.isEmpty()) {
			return new SourceAttachmentResult("The parameter is missing.", null);
		}

		try {
			SourceAttachmentRequest request = JSONUtility.toModel(arguments.get(0), SourceAttachmentRequest.class);
			IPackageFragmentRoot root = getPackageFragmentRoot(request.classFileUri);
			return resolveSourceAttachment(root, monitor);
		} catch (JsonSyntaxException ex1) {
			JavaLanguageServerPlugin.logException("Converting the source attachment parameter ", ex1);
			return new SourceAttachmentResult("Invalid parameter to resolve source attachment.", null);
		} catch (CoreException ex2) {
			JavaLanguageServerPlugin.logException("Resolving the source attachment ", ex2);
			return new SourceAttachmentResult(ex2.getMessage(), null);
		}
	}

	public static SourceAttachmentResult updateSourceAttachment(List<Object> arguments, IProgressMonitor monitor) {
		if (arguments == null || arguments.isEmpty()) {
			return new SourceAttachmentResult("The parameter is missing.", null);
		}

		try {
			SourceAttachmentRequest request = JSONUtility.toModel(arguments.get(0), SourceAttachmentRequest.class);
			IPackageFragmentRoot root = getPackageFragmentRoot(request.classFileUri);
			return updateSourceAttachment(root, request.attributes, monitor);
		} catch (JsonSyntaxException ex1) {
			JavaLanguageServerPlugin.logException("Converting the source attachment parameter ", ex1);
			return new SourceAttachmentResult("Invalid parameter to update source attachment.", null);
		} catch (CoreException ex2) {
			JavaLanguageServerPlugin.logException("Updating the source attachment ", ex2);
			return new SourceAttachmentResult(ex2.getMessage(), null);
		}
	}

	public static SourceAttachmentResult resolveSourceAttachment(IPackageFragmentRoot root, IProgressMonitor monitor) {
		ClasspathEntryWrapper entryWrapper = null;
		try {
			entryWrapper = getClasspathEntry(root);
		} catch (CoreException e) {
			return new SourceAttachmentResult(e.getMessage(), null);
		}

		IResource jarResource = null;
		try {
			jarResource = root.getUnderlyingResource();
		} catch (JavaModelException e1) {
			// do nothing.
		}

		String jarPath = jarResource != null ? jarResource.getLocation().toOSString() : entryWrapper.original.getPath().toOSString();
		String sourceAttachmentPath = entryWrapper.original.getSourceAttachmentPath() != null ? entryWrapper.original.getSourceAttachmentPath().toOSString() : null;
		String sourceAttachmentEncoding = getSourceAttachmentEncoding(entryWrapper.original);
		return new SourceAttachmentResult(null, new SourceAttachmentAttribute(jarPath, sourceAttachmentPath, sourceAttachmentEncoding, entryWrapper.canEditEncoding));
	}

	public static SourceAttachmentResult updateSourceAttachment(IPackageFragmentRoot root, SourceAttachmentAttribute changedAttributes, IProgressMonitor monitor) {
		ClasspathEntryWrapper entryWrapper = null;
		try {
			entryWrapper = getClasspathEntry(root);
		} catch (Exception e) {
			return new SourceAttachmentResult(e.getMessage(), null);
		}

		IJavaProject javaProject = root.getJavaProject();
		IClasspathEntry newClasspathEntry = newClasspathEntry(entryWrapper.original, changedAttributes, javaProject.getProject());
		if (newClasspathEntry != null && !entryWrapper.original.equals(newClasspathEntry)) {
			try {
				if (entryWrapper.containerPath != null) {
					updateContainerClasspath(javaProject, entryWrapper.containerPath, newClasspathEntry);
				} else if (entryWrapper.original.getReferencingEntry() != null) {
					updateReferencedClasspathEntry(javaProject, newClasspathEntry, monitor);
				} else {
					updateProjectClasspath(javaProject, newClasspathEntry, monitor);
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Updating the project ClasspathEntry ", e);
				return new SourceAttachmentResult("Update the ClasspathEntry to the project failure. Reason: \"" + e.getMessage() + "\"", null);
			}
		}

		return new SourceAttachmentResult(null, null);
	}

	private static IPackageFragmentRoot getPackageFragmentRoot(String classFileUri) throws CoreException {
		IClassFile classFile = JDTUtils.resolveClassFile(classFileUri);
		if (classFile == null) {
			throw constructCoreException("Cannot find the class file " + classFileUri);
		}

		IPackageFragmentRoot root = getPackageFragmentRoot(classFile);
		if (root == null) {
			throw constructCoreException("Cannot find the JAR containing this class file " + classFileUri);
		}

		return root;
	}

	private static IPackageFragmentRoot getPackageFragmentRoot(IClassFile file) {
		IJavaElement element = file.getParent();
		while (element != null && element.getElementType() != IJavaElement.PACKAGE_FRAGMENT_ROOT) {
			element = element.getParent();
		}

		return (IPackageFragmentRoot) element;
	}

	private static String getSourceAttachmentEncoding(IClasspathEntry entry) {
		if (entry != null && entry.getExtraAttributes() != null) {
			for (IClasspathAttribute attribute : entry.getExtraAttributes()) {
				if (IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING.equals(attribute.getName())) {
					return attribute.getValue();
				}
			}
		}

		return null;
	}

	private static ClasspathEntryWrapper getClasspathEntry(IPackageFragmentRoot root) throws CoreException {
		IClasspathEntry entry = null;
		IPath containerPath = null;
		boolean canEditEncoding = true;
		try {
			entry = JavaModelUtil.getClasspathEntry(root);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("getting ClasspathEntry from the PackageFragmentRoot ", e);
			throw constructCoreException(Messages.format("Cannot find the ClasspathEntry for the JAR '{0}' of this class file", BasicElementLabels.getPathLabel(root.getPath(), true)), e);
		}

		try {
			if (root.getKind() != IPackageFragmentRoot.K_BINARY) {
				throw constructCoreException(Messages.format("The JAR '{0}' of this class file contains non binary files which does not support the attachment.", BasicElementLabels.getPathLabel(root.getPath(), true)));
			}

			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				IJavaProject javaProject = root.getJavaProject();
				containerPath = entry.getPath();
				ClasspathContainerInitializer initializer = JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
				IClasspathContainer container = JavaCore.getClasspathContainer(containerPath, javaProject);
				if (initializer == null || container == null) {
					throw constructCoreException(Messages.format("The JAR of this class file belongs to container '{0}' can not be configured.", BasicElementLabels.getPathLabel(containerPath, false)));
				}

				String containerName = container.getDescription();
				IStatus status = initializer.getSourceAttachmentStatus(containerPath, javaProject);
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED) {
					throw constructCoreException(Messages.format("The JAR of this class file belongs to container '{0}' which does not support the attachment of sources to its entries.", containerName));
				}

				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY) {
					throw constructCoreException(Messages.format("The JAR of this class file belongs to container '{0}' which does not allow modifications to source attachments on its entries.", containerName));
				}

				IStatus attributeStatus = initializer.getAttributeStatus(containerPath, javaProject, IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING);
				canEditEncoding = !(attributeStatus.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED || attributeStatus.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY);
				entry = JavaModelUtil.findEntryInContainer(container, root.getPath());
				if (entry == null) {
					throw constructCoreException(Messages.format("Cannot find the ClasspathEntry from container '{0}'.", containerName));
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting the ClasspathEntry for the packageFragmentRoot ", e);
			throw constructCoreException(Messages.format("Cannot find the ClasspathEntry for the JAR '{0}' of this class file", BasicElementLabels.getPathLabel(root.getPath(), true)), e);
		}

		return new ClasspathEntryWrapper(entry, containerPath, canEditEncoding);
	}

	private static IClasspathEntry newClasspathEntry(IClasspathEntry entry, SourceAttachmentAttribute changedAttributes, IProject project) {
		IPath sourceFilePath = StringUtils.isNotBlank(changedAttributes.sourceAttachmentPath) ? Path.fromOSString(changedAttributes.sourceAttachmentPath).makeAbsolute() : null;
		IPath projectLocation = project.getLocation();
		if (sourceFilePath != null && projectLocation.isPrefixOf(sourceFilePath)) {
			IPath relativeFilePath = sourceFilePath.makeRelativeTo(projectLocation);
			sourceFilePath = project.findMember(relativeFilePath).getFullPath();
		}

		IPath sourceAttachmentPath = (sourceFilePath == null || sourceFilePath.isEmpty()) ? null : sourceFilePath;
		IClasspathAttribute newSourceEncodingAttribute = StringUtils.isNotBlank(changedAttributes.sourceAttachmentEncoding)
				? JavaCore.newClasspathAttribute(IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING, changedAttributes.sourceAttachmentEncoding)
				: null;
		List<IClasspathAttribute> newAttributes = updateElements(entry.getExtraAttributes(), newSourceEncodingAttribute, (attribute) -> {
			return attribute.getName() == IClasspathAttribute.SOURCE_ATTACHMENT_ENCODING;
		});

		switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
				return JavaCore.newLibraryEntry(entry.getPath(), sourceAttachmentPath, null, entry.getAccessRules(), newAttributes.toArray(new IClasspathAttribute[0]), entry.isExported());
			case IClasspathEntry.CPE_CONTAINER:
				return JavaCore.newContainerEntry(entry.getPath(), entry.getAccessRules(), newAttributes.toArray(new IClasspathAttribute[0]), entry.isExported());
			case IClasspathEntry.CPE_VARIABLE:
				return JavaCore.newVariableEntry(entry.getPath(), sourceAttachmentPath, null, entry.getAccessRules(), newAttributes.toArray(new IClasspathAttribute[0]), entry.isExported());
			default:
				return null;
		}
	}

	private static void updateContainerClasspath(IJavaProject javaProject, IPath containerPath, IClasspathEntry newEntry) throws CoreException {
		IClasspathContainer container = JavaCore.getClasspathContainer(containerPath, javaProject);
		List<IClasspathEntry> newEntries = updateElements(container.getClasspathEntries(), newEntry, (entry) -> {
			return entry.getEntryKind() == newEntry.getEntryKind() && entry.getPath().equals(newEntry.getPath());
		});
		IClasspathContainer updatedContainer = new UpdatedClasspathContainer(container, newEntries.toArray(new IClasspathEntry[0]));
		ClasspathContainerInitializer initializer = JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
		if (initializer != null) {
			initializer.requestClasspathContainerUpdate(containerPath, javaProject, updatedContainer);
		}
	}

	private static void updateReferencedClasspathEntry(IJavaProject javaProject, IClasspathEntry newEntry, IProgressMonitor monitor) throws JavaModelException {
		List<IClasspathEntry> newEntries = updateElements(javaProject.getReferencedClasspathEntries(), newEntry, (entry) -> {
			return entry.getEntryKind() == newEntry.getEntryKind() && entry.getPath().equals(newEntry.getPath());
		});
		javaProject.setRawClasspath(javaProject.getRawClasspath(), newEntries.toArray(new IClasspathEntry[0]), javaProject.getOutputLocation(), monitor);
	}

	private static void updateProjectClasspath(IJavaProject javaProject, IClasspathEntry newEntry, IProgressMonitor monitor) throws JavaModelException {
		List<IClasspathEntry> newEntries = updateElements(javaProject.getRawClasspath(), newEntry, (entry) -> {
			return entry.getEntryKind() == newEntry.getEntryKind() && entry.getPath().equals(newEntry.getPath());
		});
		JavaLanguageServerPlugin.logInfo("Update source attachment " + (newEntry.getSourceAttachmentPath() == null ? null : newEntry.getSourceAttachmentPath().toOSString()) + " to the file " + newEntry.getPath().toOSString());
		javaProject.setRawClasspath(newEntries.toArray(new IClasspathEntry[0]), monitor);
	}

	private static <T> List<T> updateElements(T[] elements, T newElement, Function<T, Boolean> compare) {
		List<T> newElements = new ArrayList<>();
		boolean found = false;
		if (elements != null && elements.length > 0) {
			for (T element : elements) {
				if (compare.apply(element)) {
					if (newElement != null) {
						newElements.add(newElement);
					}
					found = true;
				} else {
					newElements.add(element);
				}
			}
		}

		if (!found && newElement != null) {
			newElements.add(newElement);
		}

		return newElements;
	}

	private static CoreException constructCoreException(String message) {
		return new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, message));
	}

	private static CoreException constructCoreException(String message, Exception original) {
		return new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, message, original));
	}

	private static class UpdatedClasspathContainer implements IClasspathContainer {
		private IClasspathEntry[] fNewEntries;
		private IClasspathContainer fOriginal;

		public UpdatedClasspathContainer(IClasspathContainer original, IClasspathEntry[] newEntries) {
			fNewEntries = newEntries;
			fOriginal = original;
		}

		@Override
		public IClasspathEntry[] getClasspathEntries() {
			return fNewEntries;
		}

		@Override
		public String getDescription() {
			return fOriginal.getDescription();
		}

		@Override
		public int getKind() {
			return fOriginal.getKind();
		}

		@Override
		public IPath getPath() {
			return fOriginal.getPath();
		}
	}

	private static class ClasspathEntryWrapper {
		IClasspathEntry original;
		IPath containerPath;
		boolean canEditEncoding;

		public ClasspathEntryWrapper(IClasspathEntry original, IPath containerPath, boolean canEditEncoding) {
			this.original = original;
			this.containerPath = containerPath;
			this.canEditEncoding = canEditEncoding;
		}
	}

	public static class SourceAttachmentRequest {
		public String classFileUri;
		public SourceAttachmentAttribute attributes;

		public SourceAttachmentRequest(String classFileUri, SourceAttachmentAttribute attributes) {
			this.classFileUri = classFileUri;
			this.attributes = attributes;
		}
	}

	public static class SourceAttachmentResult {
		public String errorMessage;
		public SourceAttachmentAttribute attributes;

		public SourceAttachmentResult(String errorMessage, SourceAttachmentAttribute attributes) {
			this.errorMessage = errorMessage;
			this.attributes = attributes;
		}
	}

	public static class SourceAttachmentAttribute {
		public String jarPath;
		public String sourceAttachmentPath;
		public String sourceAttachmentEncoding;
		public boolean canEditEncoding = true;

		public SourceAttachmentAttribute(String jarPath, String sourceAttachmentPath, String sourceAttachmentEncoding) {
			this.jarPath = jarPath;
			this.sourceAttachmentPath = sourceAttachmentPath;
			this.sourceAttachmentEncoding = sourceAttachmentEncoding;
		}

		public SourceAttachmentAttribute(String jarPath, String sourceAttachmentPath, String sourceAttachmentEncoding, boolean canEditEncoding) {
			this(jarPath, sourceAttachmentPath, sourceAttachmentEncoding);
			this.canEditEncoding = canEditEncoding;
		}
	}
}
