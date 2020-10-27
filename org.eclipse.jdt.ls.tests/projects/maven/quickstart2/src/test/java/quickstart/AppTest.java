package quickstart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppTest {
    @Test
    public void shouldAnswerWithTrue() {
        assertEquals("app", new App().getName());
    }

    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.baeldung.reflection.Goat");
        // assertEquals("app", new App().getName());
    }
}
