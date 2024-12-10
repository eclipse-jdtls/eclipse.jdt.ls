package java;

public abstract class Test implements ITest {

	@Override
	public void testMethod() {}
	
	public abstract void testAbstractMethod();
	
	public void noReferences() {}
}