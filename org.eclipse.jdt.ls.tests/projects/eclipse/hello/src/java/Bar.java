package java;

/**
 * Bar
 */
public class Bar extends Foo {

    public Bar () {
    	somethingFromLombok();
    	somethingFromJPAModelGen();
    }
    
    @javax.annotation.Generated(value="lombok")
    public void somethingFromLombok(){}
    
    @javax.annotation.Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
    public void somethingFromJPAModelGen(){}
    
    @javax.annotation.Generated("lombok")
    public void somethingElseFromLombok(){}
   
}