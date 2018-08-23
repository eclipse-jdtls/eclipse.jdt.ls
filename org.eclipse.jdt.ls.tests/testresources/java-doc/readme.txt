# Generate the Javadoc and jar required by HoverHandlerTest.testHoverOnPackageWithNewJavadoc  
# Make sure Java 10 is used to build the project
cd sources
mvn clean package -DskipTests

move sources/target/apidocs to /org.eclipse.jdt.ls.tests/testresources/java-doc/apidocs
move sources/target/java-doc-0.0.1-SNAPSHOT.jar to /org.eclipse.jdt.ls.tests/projects/eclipse/remote-javadoc