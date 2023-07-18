/*******************************************************************************
* Copyright (c) 2023 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand.SourceAttachmentAttribute;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand.SourceAttachmentResult;
import org.eclipse.jdt.ls.core.internal.handlers.SourceAttachUpdateHandler.SourceInvalidatedEvent;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SourceAttachUpdateHandlerTest extends AbstractProjectsManagerBasedTest {
	@Mock
	private JavaClientConnection connection;

	@Test
	public void attachSource() throws Exception {
		importProjects("eclipse/source-attachment");
		IProject project = WorkspaceHelper.getProject("source-attachment");

		SourceAttachUpdateHandler attachListener = new SourceAttachUpdateHandler(connection);
		attachListener.addElementChangeListener();

		IResource source = project.findMember("foo-sources.jar");
		assertNotNull(source);
		IPath sourcePath = source.getLocation();
		String uri = ClassFileUtil.getURI(project, "foo.bar");
		IClassFile classfile = JDTUtils.resolveClassFile(uri);
		IPackageFragmentRoot packageRoot = (IPackageFragmentRoot) classfile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		assertNotNull(packageRoot);
		SourceAttachmentAttribute attributes = new SourceAttachmentAttribute(null, sourcePath.toOSString(), "UTF-8");

		reset(connection);
		SourceAttachmentResult updateResult = SourceAttachmentCommand.updateSourceAttachment(
			packageRoot, attributes, monitor);
		assertNotNull(updateResult);
		assertNull(updateResult.errorMessage);
		ArgumentCaptor<EventNotification> argument = ArgumentCaptor.forClass(EventNotification.class);
		verify(connection, times(1)).sendEventNotification(argument.capture());
		assertEquals(EventType.SourceInvalidated, argument.getValue().getType());
		assertEquals(true, ((SourceInvalidatedEvent) argument.getValue().getData()).contains(packageRoot, false));
	}
}
