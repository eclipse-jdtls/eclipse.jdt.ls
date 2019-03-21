package foo.bar;

/**
 * It's a Foo class
 */
public class Foo {

    public static String main(String[] args) {
        var qty = switch (args ==null?0:args.length) {
        	case 0 -> "Zero";
        	case 1 -> "One";
        	default -> "Many";
        };
        System.out.print(qty + " arguments");
        return qty;
    }
}