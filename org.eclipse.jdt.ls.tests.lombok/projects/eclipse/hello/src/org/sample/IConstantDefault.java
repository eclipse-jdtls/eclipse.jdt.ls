package org.sample;
@interface IConstantDefault {

	static final int ONE = 1;
	static final double TEST = 107.1921;
	String someMethod() default "test";

}