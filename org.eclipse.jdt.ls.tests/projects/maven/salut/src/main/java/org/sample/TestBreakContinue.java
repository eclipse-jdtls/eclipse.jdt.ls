package org.sample;

public class TestBreakContinue {

	public static void main(String[] args) {
		int i = 0;
		outer: while (true) {
			System.out.println(i);
			while (true) {
				i++;
				if (i == 1) {
					continue; // inner
				}
				if (i == 3) {
					continue outer;
				}
				if (i == 5) {
					break; //inner
				}
				if (i == 7) {
					break outer;
				}
			}
		}
	}
}