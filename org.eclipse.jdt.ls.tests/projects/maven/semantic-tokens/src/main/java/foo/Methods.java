package foo;

public class Methods {

	public <T> void foo1() {}
	private void foo2() {}
	protected void foo3() {}
	static void foo4() {}
	native String foo5();
	@Deprecated
	void foo6(String s) {}
	public static void main(String args[]) {
		Methods m = new Methods();
		m.<String>foo1();
		m.foo2();
		m.foo3();
		foo4();
		m.<Integer>foo6(m.foo5());
		Class<int[]> arr1 = int[].class;
		// https://github.com/eclipse/eclipse.jdt.ls/issues/1922
		int[] arr2 = new int[m[]];
		float[] arr3 = new float[int[]];
		// https://github.com/redhat-developer/vscode-java/issues/1921
		Class<int[]> arr4 = int[].cl;
	}

}
