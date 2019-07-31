package foo;

import reactor.core.publisher.Mono;
/**
 * WARNING: DO NOT ADJUST FILE, IT WILL MESS UP OFFSETS
 * @author nkomonen
 *
 */
public class JavaDocJarTest {
	public static void main(String[] args) {
	    Mono.error(new Exception("Broke"));  
	}
}