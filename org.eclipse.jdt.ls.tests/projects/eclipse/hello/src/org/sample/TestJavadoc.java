package org.sample;

/**
 * Test javadoc class
 * 
 * @param <K> the type of keys 
 * @param <V> the type of values
 * @author Some dude
 * @author Another one
 * @see some.pkg.SomeClass
 * @see some.pkg.SomeClass#someMethod()
 */
public class TestJavadoc<K, V> {
	
	/**
	 * Foo field
	 */
	public int fooField;

	/**
	 * Foo method
	 * 
	 * @param input some input
	 * @param count some count
	 * @return some string
	 */
	private String foo(String input, int count) {
		Inner inner = new Inner();
		return inner.test;
	}

	public class Inner {
		/** Test */
		public String test;
	}

	/**
	 * <pre><code>
	 *  interface Service {
	 *     {@literal @LookupIfProperty(name = "service.foo.enabled", stringValue = "true")}
	 *     String name();
	 *  }
	 *  </code></pre>
	 */
	public void anotherMethod() {
		// TODO Auto-generated method stub
		
	}
}