package org.sample;

public class CallHierarchyOther {

  static {
    new CallHierarchy.FooBuilder();
    new CallHierarchy.FooBuilder();
    new CallHierarchy.FooBuilder();
  }
  
  
  @Deprecated public static class X {
    
  }
  
}
