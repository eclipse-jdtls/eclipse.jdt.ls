java-language-server 
=====================

java-language-server is a Java language extension for Visual Studio Code!
The server adheres to the [language server protocol](https://github.com/Microsoft/language-server-protocol) 
and can be used with any editor that supports the protocol.  

This preview provides the following features:
* IntelliSense, Go to Definition, Outline, Open Type
* Support for Maven pom.xml to resolve dependencies


Developing and Contributing
----------------------------

*server* folder contains the java-server implemementation that is independent from VS Code

*client* folder contains the VS Code extension. 

This command will build the server and put a copy under `client/server` folder:
    
    $ cd server
    $ mvn clean verify 


For building VS Code extension see the [guide](https://code.visualstudio.com/docs/extensions/overview).
The above command copies a server under client/server folder.

Setup vscode
-----------

Install latest Visual Code - Insider edition from
https://code.visualstudio.com/insiders

Install latest Node (v6.3+) https://nodejs.org/en/download/current/

   cd client
   npm install
   vscode-insiders .
 
 
Setup Eclipse
-----------

Install Eclipse Neon Java EE from
http://www.eclipse.org/downloads/packages/eclipse-ide-java-ee-developers/neonr
that will have most needed already installed.

You can also get the
http://www.eclipse.org/downloads/packages/eclipse-ide-java-developers/neonr
and just instal Eclipse PDE from marketplace.

Once installed use `File > Open Projects from File System...` and
point it `java-language-server` and Eclipse should automatically
detect the projects and import it properly.

If you after import sees an error on `pom.xml` about Tycho, you can use Quick Fix
(Ctrl+1) to install the Tycho maven integration.

Generate simple java app for testing
-------------------------------

    mvn archetype:generate -DgroupId=com.mycompany.app
    -DartifactId=my-app
    -DarchetypeArtifactId=maven-archetype-quickstart
    -DinteractiveMode=false


Feedback
---------

* File a bug in [GitHub Issues](https://github.com/gorkem/java-language-server/issues).
* [Tweet](https://twitter.com/GorkemErcan) us with other feedback.


License
-------
See [LICENSE](LICENSE) file.
