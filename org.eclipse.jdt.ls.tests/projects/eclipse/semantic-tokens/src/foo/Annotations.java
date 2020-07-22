package foo;

public class Annotations {

	@SuppressWarnings
	public void foo() {}

	@SuppressWarnings("all")
	public void bar() {}

	@SuppressWarnings(value = "all")
	public void baz() {}

}
