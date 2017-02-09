Eclipse JDT Language Server
===========================

The Eclipse JDT Language Server is a Java language specific implementation of the [Language Server Protocol](https://github.com/Microsoft/language-server-protocol)
and can be used with any editor that supports the protocol, to offer good support for the Java Language. The server is based on:

* [Eclipse LSP4J](https://github.com/eclipse/lsp4j), the Java binding for the Language Server Protocol,
* [Eclipse JDT](http://www.eclipse.org/jdt/), which provides Java support (code completion, references, diagnostics...), 
* [M2Eclipse](http://www.eclipse.org/m2e/) which provides Maven support,
* [Buildship](https://github.com/eclipse/buildship) which provides Gradle support.

Features
--------------
* As you type reporting of parsing and compilation errors
* Code completion
* Javadoc hovers
* Code outline
* Code navigation
* Code lens (references)
* Highlights
* Code formatting
* Maven pom.xml project support


First Time Setup
--------------
0. Fork and clone the repository
1. Install Eclipse [Neon Java EE](http://www.eclipse.org/downloads/packages/eclipse-ide-java-ee-developers/neonr)
that will have most needed already installed. Alternately,
you can get the [Eclipse IDE for Java developers](http://www.eclipse.org/downloads/packages/eclipse-ide-java-developers/neonr)
and just instal Eclipse PDE from marketplace.

2. Once installed use `File > Open Projects from File System...` and
point it `java-language-server` and Eclipse should automatically
detect the projects and import it properly.

3. If, after import, you see an error on `pom.xml` about Tycho, you can use Quick Fix
(Ctrl+1) to install the Tycho maven integration.


Building from command line
----------------------------

1. Install [Apache Maven](https://maven.apache.org/)

2. This command will build the server into `/org.eclipse.jdt.ls.product/target/repository` folder:
```bash    
    $ mvn clean verify
````

Managing connection types
-------------------------
Java Language server supports socket and named pipes to communicate with the client.
Client can communicate its preferred connection methods by setting up environment
variables
* For using named pipes set the following environment variables before starting
the server.
```
STDIN_PIPE_NAME --> where client reads from
STDOUT_PIPE_NAME --> where client writes to
```
* For using plain sockets set the following environment variables before starting the
server.
```
STDIN_PORT --> client reads
STDOUT_PORT --> client writes to
```
optionally you can set host values for socket connections
```
STDIN_HOST
STDOUT_HOST
```
For both connection types the client is expected to create the connections
and wait for server the connect.


Feedback
---------

* File a bug in [GitHub Issues](https://github.com/eclipse/eclipse.jdt.ls/issues).
* [Tweet](https://twitter.com/GorkemErcan) [us](https://twitter.com/fbricon) with other feedback.

Clients
-------
This repository contains only the server implementation. Here are some known clients consuming this server:

* [vscode-java](https://github.com/redhat-developer/vscode-java) : an extension for Visual Studio Code



License
-------
EPL 1.0, See [LICENSE](LICENSE) file.
