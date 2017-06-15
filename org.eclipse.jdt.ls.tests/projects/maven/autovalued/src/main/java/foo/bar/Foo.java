package foo.bar;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Foo {
    abstract String bar();
    abstract int meh();

    public static void main(String[] args) {
        Foo foo = new AutoValue_Foo("bar", 10);
        System.err.println(foo);
    }
}