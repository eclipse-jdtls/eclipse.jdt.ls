package foo.bar;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * It's a Foo class
 */
public class TestJDT {

	@Nullable public String nullable() {
		return "";
	}

	// case 1: return null in Nonnull methods
	@NonNull public String nonnull() {
		return null;
	}

	// case 2: directly use the return value of Nullable methods
	public static void main(String[] args) {
		System.out.println(new TestJDT().nullable().length());
		System.out.println(new TestJDT().nonnull().length());
	}

	abstract class A {
		@NonNull abstract String nonnullMethod();
		@Nullable abstract String nullAbleMethod();
	}

	// case 3: implemented methods have no related Nonnull annotations
	class B extends A {
		String nonnullMethod() { return "empty string"; }
		String nullAbleMethod() { return "empty string"; }
	}

	// case 4: Assign an Nullable variable to an Nonnull variable
	void testList(@Nullable List<String> nullableList) {
		@NonNull List<String> list2 = nullableList;
	}
}
