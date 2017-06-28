/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;

import org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.DualPipeStreamProvider;
import org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.DuplexPipeStreamProvider;
import org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.SocketStreamProvider;
import org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.StdIOStreamProvider;
import org.eclipse.jdt.ls.core.internal.ConnectionStreamFactory.StreamProvider;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ConnectionStreamFactoryTest {

	@Test
	public void testStdIOSelection(){
		checkStreamProvider(StdIOStreamProvider.class);
	}

	@Test
	public void testDualPipeSelection() {
		System.setProperty("STDIN_PIPE_NAME", "test_pipe_in");
		System.setProperty("STDOUT_PIPE_NAME", "test_pipe_out");
		checkStreamProvider(DualPipeStreamProvider.class);
		System.clearProperty("STDIN_PIPE_NAME");
		System.clearProperty("STDOUT_PIPE_NAME");
	}

	@Test
	public void testDuplexPipeSelection() {
		System.setProperty("INOUT_PIPE_NAME", "test_pipe");
		checkStreamProvider(DuplexPipeStreamProvider.class);
		System.clearProperty("INOUT_PIPE_NAME");
	}

	@Test
	public void testSocketSelection(){
		System.setProperty("STDIN_PORT", "10001");
		System.setProperty("STDOUT_PORT", "10002");
		checkStreamProvider(SocketStreamProvider.class);
		System.clearProperty("STDIN_PORT");
		System.clearProperty("STDOUT_PORT");
	}

	@Test
	public void testStdInOut() throws IOException {
		ConnectionStreamFactory tested = new ConnectionStreamFactory();
		Assert.assertSame(tested.getInputStream(), JavaLanguageServerPlugin.getIn());
		Assert.assertSame(tested.getOutputStream(), JavaLanguageServerPlugin.getOut());
		Assert.assertNotSame(tested.getInputStream(), System.in);
		Assert.assertNotSame(tested.getOutputStream(), System.out);
		System.out.println("test");
		Assert.assertTrue(tested.getInputStream().available() == 0);
	}

	private void checkStreamProvider(Class<? extends StreamProvider> providerClass){
		ConnectionStreamFactory tested = new ConnectionStreamFactory();
		StreamProvider provider = tested.getSelectedStream();
		Assert.assertSame(providerClass, provider.getClass());
	}


}
