package org.sample;

import java.util.List;
import java.util.ArrayList;
public class App2 {
	public void test() {
		List<String> arr = new ArrayList<>() {
			String s2 = "abc";
			private void test() {
				int len = s2.length();
				System.out.println(s2);
				System.out.println(len);
			}
		};
	}
}