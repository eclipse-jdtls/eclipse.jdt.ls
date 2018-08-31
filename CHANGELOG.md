# Change Log

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

