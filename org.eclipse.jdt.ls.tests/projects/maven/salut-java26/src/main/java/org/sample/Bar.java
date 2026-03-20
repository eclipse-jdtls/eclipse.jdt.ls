package org.sample;

/**
 * This is Bar
 */
public class Bar {

    // JEP 530: Primitive Types in Patterns, instanceof, and switch (Fourth Preview)
    public static void main(String[] args) {
        Object obj = 42;
        if (obj instanceof int i) {
            System.out.println("int value: " + i);
        }
    }
}
