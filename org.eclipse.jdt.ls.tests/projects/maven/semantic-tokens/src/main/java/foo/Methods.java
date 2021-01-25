package foo;

public class Methods {

	public <T> void foo1() {}
	private void foo2() {}
	protected void foo3() {}
	static void foo4() {}
	native String foo5();
	@Deprecated
	void foo6(String s) {}
	public static void main(String args[]) {
		Methods m = new Methods();
		m.<String>foo1();
		m.foo2();
		m.foo3();
		foo4();
		m.<Integer>foo6(m.foo5());
	}

}
