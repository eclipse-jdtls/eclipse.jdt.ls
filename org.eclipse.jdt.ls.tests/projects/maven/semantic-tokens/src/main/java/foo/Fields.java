package foo;

public class Fields {

	public String bar1;
	private int bar2 = 123;
	protected double bar3 = bar2;
	final String bar4 = "String";
	static int bar5;
	public static final int bar6 = 1;

	enum SomeEnum {
		FIRST,
		SECOND
	}

	record SomeRecord(Integer i, Float f) {
		void foo(Object foo) {
			foo(i);
			foo(f);
		}
	}

}
