package foo.bar;

/**
 * It's a Foo class
 */
public class Foo {

    public static void main(String[] args) {
        Object foo = "x";
        if (foo instanceof String str) {
            System.out.println(str);
        }
    }

}
