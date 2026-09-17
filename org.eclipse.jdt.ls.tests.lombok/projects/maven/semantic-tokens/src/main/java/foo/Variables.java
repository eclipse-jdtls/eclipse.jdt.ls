package foo;

public class Variables {

	public static void foo(String string) {
		String bar1 = string;
		String bar2 = bar1;
		final String bar3 = "test";
	}

}
