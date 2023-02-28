/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.io.IOException;

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
	public void testSocketSelection(){
		System.setProperty("CLIENT_PORT", "10001");
		checkStreamProvider(SocketStreamProvider.class);
		System.clearProperty("CLIENT_PORT");
	}

	@Test
	public void testStdInOut() throws IOException {
		LanguageServerApplication languageServer = new LanguageServerApplication();
		ConnectionStreamFactory tested = new ConnectionStreamFactory(languageServer);
		Assert.assertSame(tested.getInputStream(), languageServer.getIn());
		Assert.assertSame(tested.getOutputStream(), languageServer.getOut());
		Assert.assertNotSame(tested.getInputStream(), System.in);
		Assert.assertNotSame(tested.getOutputStream(), System.out);
		System.out.println("test");
		Assert.assertTrue(tested.getInputStream().available() == 0);
	}

	private void checkStreamProvider(Class<? extends StreamProvider> providerClass){
		ConnectionStreamFactory tested = new ConnectionStreamFactory(null);
		StreamProvider provider = tested.getSelectedStream();
		Assert.assertSame(providerClass, provider.getClass());
	}


}
