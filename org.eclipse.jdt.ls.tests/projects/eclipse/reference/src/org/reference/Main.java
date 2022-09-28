package org.reference;

import org.sample.Foo;

public class Main {
	private static Foo foo = Foo.FOO2;

	public static void main(String[] args) {
		System.out.println(foo);
	}

	public void test() {
		String message1 = getMessage();
	}
	public String getMessage () {
		return "some message";
	}

}
