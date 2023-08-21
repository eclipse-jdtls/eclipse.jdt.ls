package foo;

public class Constructors {

	private Constructors() {
		Constructors c1 = new Constructors();
		Constructors c2 = new <String>Constructors();
		Constructors.InnerClass i1 = new Constructors.InnerClass();
		Constructors.InnerClass i2 = new @SomeAnnotation Constructors.InnerClass();
		Constructors.InnerClass<String> i3 = new Constructors.InnerClass<String>();
		InnerClass<Integer> i4 = new InnerClass<>();
		GenericConstructor g1 = new <String>GenericConstructor();
		Constructors.InnerRecord r1 = new Constructors.InnerRecord("foo", 0);
		new InnerRecord();
	}

	protected class InnerClass<T> {}
	private class GenericConstructor {
		protected <T> GenericConstructor() {}
	}

	public enum InnerEnum {
		FOO("bar");

		InnerEnum(String string) {}
	}

	private record InnerRecord(String string, int integer) {
		protected InnerRecord() {
			this("bar", 0);
		}
	}
	
	public interface TestInterface{}

}
