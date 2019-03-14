# Change Log

## [0.35.0 (March 15th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/52?closed=1)
* enhancement - added `Generate hashcode() and equals()...` source action. See [168](https://github.com/eclipse/eclipse.jdt.ls/issues/168).
* enhancement - improve the mechanism to resolve the package name for empty java file. See [950](https://github.com/eclipse/eclipse.jdt.ls/pull/950).
* bug fix - fixed server stopping when idling, after failing to track client's PID. See [#946](https://github.com/eclipse/eclipse.jdt.ls/issues/946).
* bug fix - signature help should select the 1st parameter after the opening round bracket. See [#947](https://github.com/eclipse/eclipse.jdt.ls/issues/947).


## [0.34.0 (February 28th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/51?closed=1)
* enhancement - new source action: `Override/Implement Methods...`. See [900](https://github.com/eclipse/eclipse.jdt.ls/issues/900).
* enhancement - attaching sources now use a project relative path, when possible. See [#906](https://github.com/eclipse/eclipse.jdt.ls/issues/906).
* bug fix - definitely fixed the file handle/memory leak on Windows when idling (when using Java 9+), also reduced CPU usage. See [#936](https://github.com/eclipse/eclipse.jdt.ls/pull/936).

## [0.33.0 (February 21st, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/50?closed=1)
* enhancement - automatically detect jars in `lib/` folder of standalone folders (invisible projects). See [#927](https://github.com/eclipse/eclipse.jdt.ls/pull/927).
* bug fix - fixed file handle/memory leak on Windows when idling. See [#931](https://github.com/eclipse/eclipse.jdt.ls/pull/931).
* build - use Eclipse 2019-03 M2 bits. See [#934](https://github.com/eclipse/eclipse.jdt.ls/pull/934).
* debt - use FileWatcher API from lsp4j. See [#929](https://github.com/eclipse/eclipse.jdt.ls/pull/929).

## [0.32.0 (January 31st, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/49?closed=1)
* bug fix - updates to gradle properties should be picked up when doing a full build. See [#924](https://github.com/eclipse/eclipse.jdt.ls/pull/924).

## [0.31.0 (January 17th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/48?closed=1)
* bug fix - fixed regression with "Add parentheses around cast" code action. See [#907](https://github.com/eclipse/eclipse.jdt.ls/issues/907).
* bug fix - ignore circular links during project import. See [#911](https://github.com/eclipse/eclipse.jdt.ls/pull/911).
* build - fixed build failing to download the Maven wrapper on Windows. See [#789](https://github.com/eclipse/eclipse.jdt.ls/issues/789).

## [0.30.0 (December 18th, 2018)](https://github.com/eclipse/eclipse.jdt.ls/milestone/47?closed=1)
* enhancement - source action to generate Getters/Setters for all fields. See [#163](https://github.com/eclipse/eclipse.jdt.ls/issues/163) and [#902](https://github.com/eclipse/eclipse.jdt.ls/issues/902).
* enhancement - added `java.maxConcurrentBuilds` preference to allow concurrent builds. See [#825](https://github.com/eclipse/eclipse.jdt.ls/issues/825).
* enhancement - added commands to add/remove/list project source folders. See [#859](https://github.com/eclipse/eclipse.jdt.ls/pull/859).
* enhancement - reworked standalone files support. Now maps root folders to an invisible project under jdt.ls's workspace. See [#880](https://github.com/eclipse/eclipse.jdt.ls/pull/880).
* enhancement - mapped `extract` refactorings to new code action kinds (helps with key mapping). See [#909](https://github.com/eclipse/eclipse.jdt.ls/pull/909).
* bug fix - fixed project reference when navigating to JDK classes. See [#842](https://github.com/eclipse/eclipse.jdt.ls/issues/842).
* bug fix - fixed potential NPE on hover. See [#893](https://github.com/eclipse/eclipse.jdt.ls/pull/893).
* bug fix - don't return unnecessary code actions. See [#894](https://github.com/eclipse/eclipse.jdt.ls/issues/894).
* build - removed Guava 15 jar from the distribution. See [#484](https://github.com/eclipse/eclipse.jdt.ls/issues/484).
* build - migrated to buildship 3.0. See [#875](https://github.com/eclipse/eclipse.jdt.ls/issues/875).
* build - migrated to lsp4j 0.6.0. See [#882](https://github.com/eclipse/eclipse.jdt.ls/issues/882).
* debt - fixed random failures in DiagnosticHandlerTest.testMultipleLineRange. See [#877](https://github.com/eclipse/eclipse.jdt.ls/issues/877).
* debt - removed copy of ContextSensitiveImportRewriteContext. See [#887](https://github.com/eclipse/eclipse.jdt.ls/pull/887).

## [0.29.0 (November 30th, 2018)](https://github.com/eclipse/eclipse.jdt.ls/milestone/46?closed=1)
* enhancement - rename refactoring now supports file operations (rename/move file). See [#43](https://github.com/eclipse/eclipse.jdt.ls/issues/43).
* enhancement - `Organize imports` now added as Source Action. See [#845](https://github.com/eclipse/eclipse.jdt.ls/issues/845).
* bug fix - fixed broken import autocompletion. See [#591](https://github.com/eclipse/eclipse.jdt.ls/issues/591).
* bug fix - fixed diagnostics not being reset after closing a file. See [#867](https://github.com/eclipse/eclipse.jdt.ls/issues/867).
* build - update TP to include m2e, m2e-apt, buildship. See [#873](https://github.com/eclipse/eclipse.jdt.ls/issues/873).
* debt - deleted copied StubUtility2 class from corext.refactoring. See [#858](https://github.com/eclipse/eclipse.jdt.ls/pull/858).

## [0.28.0 (November 16th, 2018)](https://github.com/eclipse/eclipse.jdt.ls/milestone/45?closed=1)
* enhancement - adopt new CodeAction and CodeActionKind. See [#800](https://github.com/eclipse/eclipse.jdt.ls/pull/800).
* enhancement - added commands to manage dependency source attachment. See [#837](https://github.com/eclipse/eclipse.jdt.ls/pull/837).
* enhancement - resolve `~/` paths for `java.configuration.maven.userSettings`. See [#848](https://github.com/eclipse/eclipse.jdt.ls/issues/848).
* bug fix - fixed NPE in documentSymbols calls when no source is attached. See [#851](https://github.com/eclipse/eclipse.jdt.ls/pull/851).
* bug fix - fixed detection of projects under linked folders. See [#831](https://github.com/eclipse/eclipse.jdt.ls/pulls/836).
* bug fix - fixed NPE in MavenBuildSupport when parent project is missing. See [#839](https://github.com/eclipse/eclipse.jdt.ls/pull/839).
* build - update TP to include m2e-apt 1.5.1. See [#855](https://github.com/eclipse/eclipse.jdt.ls/issues/855).

## [0.27.0 (October 23rd, 2018)](https://github.com/eclipse/eclipse.jdt.ls/milestone/44?closed=1)
* bug fix - ignore multiple code lenses for byte code generated methods. See [#828](https://github.com/eclipse/eclipse.jdt.ls/pull/828).
* bug fix - fixed Maven diagnostics showing up and disappearing on save. See [#829](https://github.com/eclipse/eclipse.jdt.ls/pull/829).
* bug fix - fixed typo in willSaveWaitUntil log. See [#831](https://github.com/eclipse/eclipse.jdt.ls/pulls/831).
* debt - use CodeGeneration and GetterSetterUtil from o.e.jdt.core.manipulation. See [#821](https://github.com/eclipse/eclipse.jdt.ls/pull/821).
* debt - delete copied classes from corext.refactoring. See [#826](https://github.com/eclipse/eclipse.jdt.ls/pull/826).

## [0.26.0 (October 2nd, 2018)](https://github.com/eclipse/eclipse.jdt.ls/issues?q=is%3Aclosed+milestone%3A%22End+September+2018%22)
* enhancement - new Java 11 support for Maven, Gradle and Eclipse projects. See [#735](https://github.com/eclipse/eclipse.jdt.ls/issues/735).
* enhancement - bind `Project configuration is not up-to-date with pom.xml` diagnostics to pom.xml. See [#797](https://github.com/eclipse/eclipse.jdt.ls/issues/797).
* enhancement - cascade "Update project configuration" command to child Maven projects. See [#806](https://github.com/eclipse/eclipse.jdt.ls/pull/806).
* enhancement - ignore `Unknown referenced nature` warnings. See [#812](https://github.com/eclipse/eclipse.jdt.ls/issues/812).
* bug fix - fixed 'java/buildWorkspace' command failing due to `Project configuration is not up-to-date with pom.xml` errors. See [#813](https://github.com/eclipse/eclipse.jdt.ls/issues/813).
* debt - removed copy of StubUtility, use the one from o.e.jdt.core.manipulation. See [#793](https://github.com/eclipse/eclipse.jdt.ls/pull/793).

## [0.25.0 (September 16th, 2018)](https://github.com/eclipse/eclipse.jdt.ls/issues?q=is%3Aclosed+milestone%3A%22Mid+September+2018%22)
* enhancement - new code-action: Convert anonymous class to lambda expression. See [#658](https://github.com/eclipse/eclipse.jdt.ls/issues/658).
* enhancement - exposed new asynchronous `workspace/notify` command. See [#719](https://github.com/eclipse/eclipse.jdt.ls/issues/719).
* enhancement - adopted new DocumentSymbolProvider API. See [#780](https://github.com/eclipse/eclipse.jdt.ls/issues/780).
* enhancement - new preference to disable auto-completion. See [#786](https://github.com/eclipse/eclipse.jdt.ls/pull/786).
* enhancement - migrated to lsp4j 0.5.0.M1. See [#787](https://github.com/eclipse/eclipse.jdt.ls/issues/787).
* bug fix - fixed 'Updating Maven projects' showing progress above 100%. See [#785](https://github.com/eclipse/eclipse.jdt.ls/pull/785).
* bug fix - fixed BadLocationExceptions thrown during `textDocument/documentSymbol` invocations. See [#794](https://github.com/eclipse/eclipse.jdt.ls/issues/794).

## [0.24.0 (August 31rd, 2018)](https://github.com/eclipse/eclipse.jdt.ls/issues?q=is%3Aclosed+milestone%3A%22End+August+2018%22)
* enhancement - add `textDocument/implementation` support. See [#556](https://github.com/eclipse/eclipse.jdt.ls/issues/556).
* enhancement - automatically generate params in Javadoc. See [#744](https://github.com/eclipse/eclipse.jdt.ls/pull/744).
* enhancement - support folder URIs in `workspace/didChangeWatchedFiles`. See [#755](https://github.com/eclipse/eclipse.jdt.ls/pull/755).
* enhancement - prevent unnecessary build when reopening workspace. See [#756](https://github.com/eclipse/eclipse.jdt.ls/pull/756).
* enhancement - publish diagnostic information at the project level. See [#759](https://github.com/eclipse/eclipse.jdt.ls/pull/759).
* enhancement - update m2e to 1.9.1 See [#761](https://github.com/eclipse/eclipse.jdt.ls/issues/761).
* enhancement - lower severity of m2e's `Project configuration is not up-to-date...` diagnostics. See [#763](https://github.com/eclipse/eclipse.jdt.ls/issues/763).
* enhancement - add quickfix for removing unused local var and all assignments. See [#769](https://github.com/eclipse/eclipse.jdt.ls/issues/769).
* bug fix - fixed timestamps in logs. See [#742](https://github.com/eclipse/eclipse.jdt.ls/issues/742).
* bug fix - don't send notifications for gradle files modified under the build directory. See [#768](https://github.com/eclipse/eclipse.jdt.ls/issues/768).
* bug fix - fixed FormattingOptions.isInsertSpaces=false being ignored during formatting requests. See [#775](https://github.com/eclipse/eclipse.jdt.ls/issues/775).
* debt - remove copies of IProblemLocation and ProblemLocation. See [#749](https://github.com/eclipse/eclipse.jdt.ls/pull/749).
* debt - fixed random failures of HoverHandlerTest.testHoverOnPackageWithNewJavadoc. See [#764]( https://github.com/eclipse/eclipse.jdt.ls/issues/764).
* documentation - provide a changelog. See [#773](https://github.com/eclipse/eclipse.jdt.ls/issues/773).

