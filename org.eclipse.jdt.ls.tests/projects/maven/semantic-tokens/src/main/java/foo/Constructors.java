package foo;

public class Constructors {

	private Constructors() {
		Constructors c1 = new Constructors();
		Constructors c2 = new <String>Constructors();
		Constructors.InnerClass i1 = new Constructors.InnerClass();
		Constructors.InnerClass i2 = new @SomeAnnotation Constructors.InnerClass();
		Constructors.InnerClass<String> i3 = new Constructors.InnerClass<String>();
	}

	protected class InnerClass<T> {}

}
