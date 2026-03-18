package org.sample;

/**
 * This is Bar
 */
public class Bar {

    public static void main(String[] args) {
        Object foo = "x";
        if (foo instanceof String str) {
            System.out.println(str);
        }
    }
}
