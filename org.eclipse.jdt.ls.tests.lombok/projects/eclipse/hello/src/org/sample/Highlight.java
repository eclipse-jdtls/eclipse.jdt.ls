package org.sample;

import java.io.IOException;

public class Highlight implements FooInterface {

	private String str = "string";

	public String getFoo() throws IOException {
		if (str.contains("!")) {
			throw new IOException();
		}
		str = "bar";
		if (str.length() == 0) {
			throw new RuntimeException();
		}
		loop: while (!str.isEmpty()) {
			for (;;) {
				if (str.contains("foo")) {
					break loop;
				}
				continue;
			}
		}
		str = String.format(str);
		return str + "foo";
	}

	public int getBar() {
		String str = "bar";
		return str.length();
	}

	@Override
	public void foo() {
		// TODO Auto-generated method stub
	}

	@Override
	public void bar() {
		// TODO Auto-generated method stub
	}

}

interface FooInterface {
	public void foo();
	public void bar();
}
