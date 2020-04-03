package java;

import org.apache.commons.lang3.StringUtils;

/**
 * This is foo
 */
public class Foo implements IFoo {

    public static void main(String[] args) {
        System.out.println(StringUtils.capitalize("Hello world! from " + Foo.class));
        Bar bar = new Bar();
        bar.print();
	}
}