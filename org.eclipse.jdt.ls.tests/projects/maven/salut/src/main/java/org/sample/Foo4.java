package org.sample;

/**
 * This is the test data for SelectionRangeHandlerTest. To ask for a selection range, we need to specify a position.
 * To make those positions visible, >< pairs are used if possible, and a position is what's between > and <.
 * All other positions are specified in the test code dierectly.
 */
class Foo4 {
	/**
	 * Class constructor to test >< Javadoc
	 */
	Foo4() {
		System.out.println("string >< literal"); // test line >< comment

		/* test block >< comment */
		memberFunc(1, 0.0);
	}

	void memberFunc(int paramA/* test block >< comment in param list */, double paramB) {
		try {
			switch(paramA) {
				case 0:
					System.out.println(paramB);
					break;
				default:
					System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
