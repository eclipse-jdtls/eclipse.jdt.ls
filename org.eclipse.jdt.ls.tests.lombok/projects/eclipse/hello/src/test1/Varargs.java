package test1;

public class Varargs {
    public static void main(String[] args) {
        run(Object.class, args);
    }

    public static void run(Class<?> clazz, String... args) {
        run(new Class<?>[] { clazz }, args);
    }

    public static void run(Class<?>[] classes, String[] args) {

    }

    public void run(String... args) {
    }
}