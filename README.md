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

This command will build the server and put a copy under client/ folder:
    
    $ cd server
    $ mvn clean verify 

For building VS Code extension see the [guide](https://code.visualstudio.com/docs/extensions/overview).
The above command copies a server under client/server folder.

Feedback
---------

* File a bug in [GitHub Issues](https://github.com/gorkem/java-language-server/issues).
* [Tweet](https://twitter.com/GorkemErcan) us with other feedback.


License
-------
See [LICENSE](LICENSE) file.