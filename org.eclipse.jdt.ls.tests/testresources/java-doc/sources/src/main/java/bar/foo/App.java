package bar.foo;

/**
 * This App has some serious documentation
 * 
 * @author Fred
 * @since 1.0
 */
public class App {
	
	private App() {
		//no javadoc generated
	}

	/**
	 * Runs the <b>App</b>
	 */
	public static void main(String[] args) {
		new Foo().doSomething("Woot", 100);
	}
}
