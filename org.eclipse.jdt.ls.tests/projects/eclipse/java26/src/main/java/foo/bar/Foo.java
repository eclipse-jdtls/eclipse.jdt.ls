package foo.bar;

/**
 * It's a Foo class
 */
public class Foo {
	// JEP 530: Primitive Types in Patterns, instanceof, and switch (Fourth Preview)
	static String classify(int value) {
		return switch (value) {
			case 0 -> "zero";
			case int i when i > 0 -> "positive";
			case int i -> "negative";
		};
	}
}
