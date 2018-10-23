# Change Log

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

