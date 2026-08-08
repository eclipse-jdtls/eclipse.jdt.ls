package org.sample;

import java.util.ResourceBundle;

public class ResourceBundleTest {
	private ResourceBundle bundle;

	public void testResourceBundle() {
		bundle = ResourceBundle.getBundle("resources.messages");
		String value = bundle.getString("");
	}
}


