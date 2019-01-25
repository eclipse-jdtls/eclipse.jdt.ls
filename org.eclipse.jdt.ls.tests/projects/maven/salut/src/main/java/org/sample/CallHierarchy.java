package org.sample;

import org.apache.commons.lang3.builder.Builder;
import org.apache.commons.lang3.text.WordUtils;

public class CallHierarchy {

  public static class FooBuilder implements Builder<Object> {

    public FooBuilder() {
      new CallHierarchyOther.X();
    }

    @Override
    public Object build() {
      WordUtils.capitalize("a");
      WordUtils.capitalize("b");
      return null;
    }

  }

}
