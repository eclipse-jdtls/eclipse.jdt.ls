package foo;

public class Types {

	public String // comments
		s1 = "happy",
		s2, s3;

	protected final Class<?> clazz = java.lang.String.class;

	public SomeClass<String, SomeClass<String, Integer>> someClass;

	class SomeClass<T1, T2> {
		T1 t1;
		T2 t2;
	}
	interface SomeInterface {}
	enum SomeEnum {}
	@interface SomeAnnotation {}

}
