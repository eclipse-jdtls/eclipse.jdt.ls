package org.sample;

public class StaticBlockFoldingRange {

	public void bar () {
	}

	static

	{
		String STATIC1 = "foo";
		int STATIC2 = 3;
	}

	public void foo () {
    }

	static {}
}