package foo.bar;

/**
 * It's a Foo class
 */
public sealed interface Foo
permits Bar { 
}
record Bar(String name) implements Foo { }
