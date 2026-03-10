
package org.sample;

import org.example.App;

public class Test {
    public static void main(String[] args) {
        App.main(new String[] {});
        String message = org.example.App$.MODULE$.greeting();

        System.out.println("Message: " + message);
    }

}
