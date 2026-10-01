package org.sample;

public interface CallHierarchyGH2771 {
    Opt<String> name();

    public static class Opt<T> {
        public static <T> Opt<T> of(T t) {
            return new Opt();
        }
    }
}
