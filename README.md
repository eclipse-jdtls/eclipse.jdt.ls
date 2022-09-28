[![Build Status](https://ci.eclipse.org/ls/buildStatus/icon?job=jdt-ls-master)](https://ci.eclipse.org/ls/job/jdt-ls-master)

Eclipse JDT Language Server
===========================

The Eclipse JDT Language Server is a Java language specific implementation of the [Language Server Protocol](https://github.com/Microsoft/language-server-protocol)
and can be used with any editor that supports the protocol, to offer good support for the Java Language. The server is based on:

* [Eclipse LSP4J](https://github.com/eclipse/lsp4j), the Java binding for the Language Server Protocol,
* [Eclipse JDT](http://www.eclipse.org/jdt/), which provides Java support (code completion, references, diagnostics...),
* [M2Eclipse](http://www.eclipse.org/m2e/), which provides Maven support,
* [Buildship](https://github.com/eclipse/buildship), which provides Gradle support.

Features
--------------
* Supports compiling projects from Java 1.5 through 19
* Maven pom.xml project support
* Gradle project support (with experimental Android project import support)
* Standalone Java files support
* As-you-type reporting of syntax and compilation errors
* Code completion
* Javadoc hovers
* Organize imports
* Type search
* Code actions (quick fixes, source actions & refactorings)
* Code outline
* Code folding
* Code navigation
* Code lens (references/implementations)
* Code formatting (on-type/selection/file)
* Code snippets
* Highlights (semantic highlighting)
* Semantic selection
* Diagnostic tags
* Call Hierarchy
* Type Hierarchy
* Annotation processing support (automatic for Maven projects)
* Automatic source resolution for classes in jars with maven coordinates
* Extensibility


Installation
------------

There are several options to install eclipse.jdt.ls:

- Download and extract a milestone build from [http://download.eclipse.org/jdtls/milestones/](http://download.eclipse.org/jdtls/milestones/?d)
- Download and extract a snapshot build from [http://download.eclipse.org/jdtls/snapshots/](http://download.eclipse.org/jdtls/snapshots/?d)
- Under some Linux distributions you can use the package manager. Search the package repositories for `jdtls` or `eclipse.jdt.ls`.
- Build it from source. Clone the repository via `git clone` and build the project via `JAVA_HOME=/path/to/java/11 ./mvnw clean verify`. Optionally append `-DskipTests=true` to by-pass the tests. This command builds the server into the `./org.eclipse.jdt.ls.product/target/repository` folder.

Some editors or editor extensions bundle eclipse.jdt.ls or contain logic to install it. If that is the case, you only need to install the editor extension. For example for Visual Studio Code you can install the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) and it will take care of the rest.


Running from the command line
------------------------------

If you built eclipse.jdt.ls from source, `cd` into `./org.eclipse.jdt.ls.product/target/repository`. If you downloaded a milestone or snapshot build, extract the contents.

To start the server in the active terminal, adjust the following command as described further below and run it:

```bash
java \
	-Declipse.application=org.eclipse.jdt.ls.core.id1 \
	-Dosgi.bundles.defaultStartLevel=4 \
	-Declipse.product=org.eclipse.jdt.ls.core.product \
	-Dlog.level=ALL \
	-noverify \
	-Xmx1G \
	--add-modules=ALL-SYSTEM \
	--add-opens java.base/java.util=ALL-UNNAMED \
	--add-opens java.base/java.lang=ALL-UNNAMED \
	-jar ./plugins/org.eclipse.equinox.launcher_1.5.200.v20180922-1751.jar \
	-configuration ./config_linux \
	-data /path/to/data
```

1. Choose a value for `-configuration`: this is the path to your platform's configuration directory. For Linux, use `./config_linux`. For windows, use `./config_win`. For mac/OS X, use `./config_mac`.
2. Change the filename of the jar in `-jar ./plugins/...` to match the version you built or downloaded.
3. Choose a value for `-data`: An absolute path to your data directory. eclipse.jdt.ls stores workspace specific information in it. This should be unique per workspace/project.

If you want to debug eclipse.jdt.ls itself, add `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044` right after `java` and ensure nothing else is running on port 1044. If you want to debug from the start of execution, change `suspend=n` to `suspend=y` so the JVM will wait for your debugger prior to starting the server.

Running from command line with wrapper script
---------------------------------------------

There is also a Python wrapper script available that makes the start up of eclipse.jdt.ls more convenient (no need to juggle with Java options etc.). A sample usage is described below. The script requires Python 3.9.

```bash
./org.eclipse.jdt.ls.product/target/repository/bin/jdtls \
	-configuration ~/.cache/jdtls \
	-data /path/to/data
```

All shown Java options will be set by the wrapper script. Please, note that the `-configuration` options points to a user's folder to ensure that the configuration folder in `org.eclipse.jdt.ls.product/target/repository/config_*` remains untouched.

Development Setup
-----------------

See [Contributing](CONTRIBUTING.md)


Managing connection types
-------------------------
The Java Language server supports sockets, named pipes, and standard streams of the server process
to communicate with the client. Client can communicate its preferred connection methods
by setting up environment variables or alternatively using system properties (e.g. `-DCLIENT_PORT=...`)

* To use a **plain socket**, set the following environment variables or system properties before starting the server:
   * `CLIENT_PORT`: the port of the socket to connect to
   * `CLIENT_HOST`: the host name to connect to. If not set, defaults to `localhost`.

   The connection will be used for in and output.

* To use standard streams(stdin, stdout) of the server process do not set any
of the above environment variables and the server will fall back to standard streams.

For socket and named pipes, the client is expected to create the connections
and wait for the server to connect.


Feedback
---------

* File a bug in [GitHub Issues](https://github.com/eclipse/eclipse.jdt.ls/issues).
* Join the discussion on our [Mattermost channel](https://mattermost.eclipse.org/eclipse/channels/eclipsejdtls)
* [Tweet](https://twitter.com/GorkemErcan) [us](https://twitter.com/fbricon) with other feedback.

Clients
-------
This repository only contains the server implementation. Here are some known clients consuming this server:

* [vscode-java](https://github.com/redhat-developer/vscode-java) : an extension for Visual Studio Code
* [ide-java](https://github.com/atom/ide-java) : an extension for Atom
* [ycmd](https://github.com/Valloric/ycmd) : a code-completion and code-comprehension server for multiple clients
* [Oni](https://github.com/onivim/oni/wiki/Language-Support#java) : modern modal editing - powered by Neovim.
* [LSP Java](https://github.com/emacs-lsp/lsp-java) : a Java LSP client for Emacs
* [Eclipse Theia](https://github.com/theia-ide/theia) : Theia is a cloud & desktop IDE framework implemented in TypeScript
* [coc-java](https://github.com/neoclide/coc-java) : an extension for [coc.nvim](https://github.com/neoclide/coc.nvim)
* [MS Paint IDE](https://github.com/MSPaintIDE/MSPaintIDE) : an IDE for programming in MS Paint
* [nvim-jdtls](https://github.com/mfussenegger/nvim-jdtls) : an extension for Neovim

Continuous Integration Builds
-----------------------------
Our [CI server](https://ci.eclipse.org/ls/) publishes the server binaries to [http://download.eclipse.org/jdtls/snapshots/](http://download.eclipse.org/jdtls/snapshots/?d).

P2 repositories are available under [http://download.eclipse.org/jdtls/snapshots/repository/](http://download.eclipse.org/jdtls/snapshots/repository?d).

Milestone builds are available under [http://download.eclipse.org/jdtls/milestones/](http://download.eclipse.org/jdtls/milestones/?d).

License
-------
EPL 2.0, See [LICENSE](LICENSE) file.
