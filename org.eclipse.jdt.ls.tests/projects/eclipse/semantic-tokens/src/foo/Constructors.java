package foo;

public class Constructors {

	private Constructors() {
		Constructors c = new Constructors();
		Constructors.InnerClass i1 = new Constructors.InnerClass();
		Constructors.InnerClass i2 = new @SomeAnnotation Constructors.InnerClass();
		Constructors.InnerClass<String> i3 = new Constructors.InnerClass<String>();
	}

	protected class InnerClass<T> {}

}
