package org.ref;

public class Test {
	public static void main(String[] args) {
		Apple apple = Apple.builder().name("Hello World!").build();
		System.out.println(apple.getName());
	}

}
