public class Bar {
	record Rectangle(double length, double width) {}
	void foo(String id) {
		new Rectangle(1.0, 2.0);
	}
}
