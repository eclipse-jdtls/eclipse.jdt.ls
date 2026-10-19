package java.internal;

import org.springframework.core.SpringVersion;

public class InternalBar {

	public static void main(String[] args) {
		System.err.println(SpringVersion.getVersion());
	}
}
