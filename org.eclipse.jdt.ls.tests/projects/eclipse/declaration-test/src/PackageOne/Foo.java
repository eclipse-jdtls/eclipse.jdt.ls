package PackageOne;

public class Foo implements IFoo{
    public void hello() {
        System.out.println("Hello World!");
    }
}

interface IFoo {
    void hello();
}