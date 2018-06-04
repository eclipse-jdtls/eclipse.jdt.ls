package org.sample;

/**
 * Test javadoc class
 */
public class TestJavadoc {
	
	/**
	 * Foo field
	 */
	public int fooField;

	/**
	 * Foo method
	 */
	private String foo() {
		Inner inner = new Inner();
		return inner.test;
	}

	public class Inner {
		/** Test */
		public String test;
	}

}