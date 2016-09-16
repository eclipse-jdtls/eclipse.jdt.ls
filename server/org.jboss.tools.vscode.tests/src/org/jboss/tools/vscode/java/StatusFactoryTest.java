package org.jboss.tools.vscode.java;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.runtime.IStatus;
import org.junit.Test;

public class StatusFactoryTest {

	@Test
	public void testNewErrorStatusString() throws Exception {
		IStatus error = StatusFactory.newErrorStatus("foo");
		assertEquals("foo", error.getMessage());
		assertEquals(IStatus.ERROR, error.getSeverity());
		assertEquals(JavaLanguageServerPlugin.PLUGIN_ID, error.getPlugin());
	}

	@Test
	public void testNewErrorStatusStringThrowable() throws Exception {
		Exception e = new Exception();
		IStatus error = StatusFactory.newErrorStatus("foo", e);
		assertEquals("foo", error.getMessage());
		assertEquals(e, error.getException());
	}
	
}
