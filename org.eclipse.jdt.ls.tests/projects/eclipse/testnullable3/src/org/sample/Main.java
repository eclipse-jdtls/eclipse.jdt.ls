package org.sample;
import javax.annotation.Nonnull;
public class Main {
  public static void main(String[] args) {
    Test.foo(null); 
  }
}
class Test {
  static void foo(@Nonnull Test a) { }
}