package org.sample;

public class FooService {
	private IFoo foo;

	void bar() {
		foo.someMethod();
	}

	void bar1() {
		Foo newFoo = new Foo();
		newFoo.someMethod();
	}

	void bar2(AbstractFoo newFoo) {
		newFoo.someMethod();
	}
}
