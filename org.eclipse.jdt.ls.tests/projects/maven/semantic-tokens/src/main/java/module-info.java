// Foo
/**
 * Bar
 */

/**
 * Foo bar {@code baz}
 *
 * @uses java.sql.Driver
 * @moduleGraph
 */
module foo.bar.baz {
	// Baz
	requires java.base;
	requires java.desktop;
	requires java.net.http;
	requires java.sql;

	exports foo to java.base, java.desktop;
	opens foo to java.base, java.net.http;

	uses java.sql.Driver;
}
