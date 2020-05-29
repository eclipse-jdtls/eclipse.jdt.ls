package org.sample;

public class MyTest {
    @Test
    public void test() {
        assertEquals(1, 1, "message");
        assertThat("test", true);
        any();
    }
}
