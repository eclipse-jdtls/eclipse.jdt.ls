package org.sample;

public class Call {
    public static void main(String[] args) {
        System.out.println("skip");
        System.out.println("skip");
        System.out.println("skip");
        new Foo().someMethod();
        System.out.println("skip");
        System.out.println("skip");
        new Foo().someMethod();
    }
}
