package org.sample;

public class CallHierarchy {

  @Deprecated
  public static void main(String[] args) {
    new Base();
    new Child().bar();
    System.out.println(Base.STATIC_FIELD);
  }

  public static class Base {

    static int STATIC_FIELD = 100;
    protected int protectedField = 200;

/*nothing*/

    public Base() {
/*should resolve to enclosing "method/constructor/initializer"*/
    }

    public void foo() {

    }

    public void bar() {
      new Child();
      new Child();
      Thread.currentThread();
      Thread.currentThread();
    }

    protected void method_1() {
      foo();
      bar();
    }

  }

  public static class Child extends Base {

    Child() {
      super();
      foo();
      bar();
      method_1();
      protectedField = 300;
      protectedField = 400;
    }

    protected void method_2() {
      method_1();
      method_1();
      method_1();
    }

  }

  public void recursive1() {
    recursive2();
  }

  public void recursive2() {
    recursive1();
  }
}
