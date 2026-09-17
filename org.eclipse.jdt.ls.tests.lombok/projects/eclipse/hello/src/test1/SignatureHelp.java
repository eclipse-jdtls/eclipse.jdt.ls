package test1;

import java.util.function.Function;

public class SignatureHelp
{
  public SignatureHelp( String signature ) {
    this.test( (s) -> s );
    this.test(
  }
  public void test( Function<String,String> f ) {
  }
}