package org.sample;

public class Enums {

    public enum MyEnum {
        ENUM1 {

            @Override
            public String get() {
                return "ENUM1";
            }
        },
        ENUM2 {

            @Override
            public String get() {
                return "ENUM2";
            }
        };

        public abstract String get();

    }
    
}
