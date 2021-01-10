package foo;

public class Annotations {

	@SomeAnnotation
	public void foo() {}

	@SuppressWarnings("all")
	public void bar() {}

	@SuppressWarnings(value = "all")
	public void baz() {}

}
