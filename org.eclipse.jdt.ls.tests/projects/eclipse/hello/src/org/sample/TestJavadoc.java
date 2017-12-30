package org.sample;

public class TestJavadoc {

	private String foo() {
		Inner inner = new Inner();
		return inner.test;
	}

	public class Inner {
		/** Test */
		public String test;
	}
}