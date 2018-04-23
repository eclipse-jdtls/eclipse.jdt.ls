package foo.bar;

import java.util.Arrays;

public class Foo {

	public static void main(String[] args) {
		for(var name : Arrays.asList("a")) {
			System.err.println(name.toUpperCase());
		}
	}
}
