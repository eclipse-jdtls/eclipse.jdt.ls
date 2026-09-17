package org.sample;

import java.util.function.Consumer;

public class HelloWorld {
    public static <T> void build(T element) {
        new Thread(() -> {
            new Consumer<T>() {

                @Override
                public void accept(T t) {

                }
            };
        });
    }
}
