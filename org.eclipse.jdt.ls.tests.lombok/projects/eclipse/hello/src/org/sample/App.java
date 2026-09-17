package org.sample;
import java.util.function.Function;
public class App {
    public String getGreeting() {
      return "foo";
    }
    public static void main(String[] args) {
        Function<Integer, Integer> foo = new Function<>() {

          @Override
          public Integer apply(Integer t) {
              // Go to definition on `getGreeting` doesn't work
              new App().getGreeting();
              return 10;
          }
        };
    }
}