package foo;

/**
 * @implNote Lorem ipsum {@link java.lang.String}.
 */
public class Javadoc {

	/**
	 * Foo bar {@code baz}.
	 * @param arg1 An {@link Integer}.
	 * @param arg2 A {@link Double}.
	 * @return A {@link String}.
	 */
	public String getString(Integer arg1, Double arg2) {
		return "String";
	}

	/**
	 * Lorem ipsum {@link Javadoc#getString(Integer, Double)}
	 * @see #getString(Integer, Double)
	 * @return An {@link Integer}
	 */
	private int getInt() {
		return 0;
	}

}
