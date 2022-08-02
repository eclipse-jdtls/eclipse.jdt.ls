public class Bar {
	record Edge(int fromNodeId,
		int toNodeId,
		Object fromPoint,
		Object toPoint,
		double length,
		Object profile
	) {}
	void foo(String id) {
		new Edge(1, 2, 3, 4, 5, 6);
	}
}
