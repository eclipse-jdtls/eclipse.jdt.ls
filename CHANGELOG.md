# Change Log
# [0.70.0 (March 5th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/88?closed=1)
 * feature - Code actions should return textedits with proper formatting. See [#1157](https://github.com/eclipse/eclipse.jdt.ls/issues/1157).
 * bug fix - Change location of .m2/ and .tooling/ from HOME. See [#1654](https://github.com/eclipse/eclipse.jdt.ls/issues/1654).
 * bug fix - Issue with 'Go To Definition'. See [#1634](https://github.com/eclipse/eclipse.jdt.ls/issues/1634).
 * other - Adopt the helpers from jdt.core.manipulation to deal with the CU's preferences. See [#1666](https://github.com/eclipse/eclipse.jdt.ls/pull/1666).
 * debt - Tests fail on Windows. See [#996](https://github.com/eclipse/eclipse.jdt.ls/issues/996).

# [0.69.0 (February 11th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/87?closed=1)
 * enhancement - Specify output path for invisible project. See [#1593](https://github.com/eclipse/eclipse.jdt.ls/issues/1593).
 * other - Remove legacy Semantic Highlighting implementation. See [#1649](https://github.com/eclipse/eclipse.jdt.ls/pull/1649).
 * other - Further semantic tokens improvements. See [#1641](https://github.com/eclipse/eclipse.jdt.ls/pull/1641).
 * other - Rename 'function' semantic token to 'method'. See [#1608](https://github.com/eclipse/eclipse.jdt.ls/pull/1608).

# [0.68.0 (January 20th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/86?closed=1)
 * enhancement - Download sources for the masses. See [#1628](https://github.com/eclipse/eclipse.jdt.ls/pull/1628).
 * bug fix - Some refactors are missing when the location has diagnostics. See [#1642](https://github.com/eclipse/eclipse.jdt.ls/issues/1642).
 * bug fix - Should not enable preview compiler options if the tooling doesn't support the early access JDK. See [#1644](https://github.com/eclipse/eclipse.jdt.ls/issues/1644).
 * bug fix - Enhance MavenProjectImporter to stop scanning the specified exclusion list. See [#1636](https://github.com/eclipse/eclipse.jdt.ls/pull/1636).
 * build - Update target platform to use Eclipse 2020-12 Release. See [#1639](https://github.com/eclipse/eclipse.jdt.ls/pull/1639).

# [0.67.0 (December 17th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/85?closed=1)
 * bug fix - Detect Gradle project by settings.gradle as well. See [#1617](https://github.com/eclipse/eclipse.jdt.ls/pull/1617).
 * bug fix - Should update Gradle project properly after the build file of a sub Gradle project is updated. See [#1617](https://github.com/eclipse/eclipse.jdt.ls/pull/1617).
 * bug fix - Long completionItem/resolve and TimeoutException. See [#1624](https://github.com/eclipse/eclipse.jdt.ls/issues/1624).
 * bug fix - Disable module results for autocomplete. See [#1613](https://github.com/eclipse/eclipse.jdt.ls/issues/1613).
 * bug fix - Get the project from the linked folder uri. See [#1630](https://github.com/eclipse/eclipse.jdt.ls/pull/1630).
 * bug fix - Add support to INFO log level. See [#1623](https://github.com/eclipse/eclipse.jdt.ls/pull/1623).

# [0.66.0 (December 2nd, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/84?closed=1)
 * enhancement - Support inferSelection when extract to field. See [#1619](https://github.com/eclipse/eclipse.jdt.ls/pull/1619).
 * enhancement - Support inferSelection when extract to variable. See [#1615](https://github.com/eclipse/eclipse.jdt.ls/pull/1615).
 * bug fix - jdt.ls distro is 10MB heavier because of com.ibm.icu_64.2.0.v20190507-1337.jar. See [#1351](https://github.com/eclipse/eclipse.jdt.ls/issues/1351).
 * bug fix - Java LS crashes on WSL Alpine. See [#1612](https://github.com/eclipse/eclipse.jdt.ls/pull/1612).
 * bug fix - End of File exception when opening completion in empty file. See [#1611](https://github.com/eclipse/eclipse.jdt.ls/issues/1611).
 * other - Update Target Platform to use Eclipse 2020-12 M3. See [#1616](https://github.com/eclipse/eclipse.jdt.ls/pull/1616).
 * other - Improve the performance of inferSelection. See [#1609](https://github.com/eclipse/eclipse.jdt.ls/pull/1609).

# [0.65.0 (November 19th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/83?closed=1)
 * bug fix - Update m2e to 1.17.0.20201112-0751. See [#1596](https://github.com/eclipse/eclipse.jdt.ls/pull/1596).
 * other - Improve tracing capability of m2e through m2e.logback.configuration. See [#1589](https://github.com/eclipse/eclipse.jdt.ls/pull/1589).
 * other - Infer expressions if there is no selection range when extracting method. See [#1585](https://github.com/eclipse/eclipse.jdt.ls/pull/1585).

# [0.64.0 (November 4th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/82?closed=1)
 * enhancement - Provide method for converting callstack entry to location. See [#1202](https://github.com/eclipse/eclipse.jdt.ls/issues/1202).
 * enhancement - Hide inline variable/constant commands when no reference found. See [#1573](https://github.com/eclipse/eclipse.jdt.ls/pull/1573) and [#1575](https://github.com/eclipse/eclipse.jdt.ls/pull/1575).
 * enhancement - Convert a lambda expression to method reference. See [#1571](https://github.com/eclipse/eclipse.jdt.ls/pull/1571).
 * bug fix - CompletionResultRequestor compares different ICompilationUnit types. See [#1582](https://github.com/eclipse/eclipse.jdt.ls/issues/1582).
 * bug fix - GTD is not working if referenced library is updated without file name change. See [#1577](https://github.com/eclipse/eclipse.jdt.ls/issues/1577).
 * bug fix - Fix method ref CompletionItemKind. See [#1574](https://github.com/eclipse/eclipse.jdt.ls/pull/1574).
 * other - Update Target Platform to Eclipse 2020-12 M1. See [#1567](https://github.com/eclipse/eclipse.jdt.ls/issues/1567).

# [0.63.0 (October 15th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/81?closed=1)
 * enhancement - Embed m2e 1.17. See [#1562](https://github.com/eclipse/eclipse.jdt.ls/pull/1562).
 * enhancement - Add code actions to add sealed/final/non-sealed modifier on a permitted type declaration. See [#1555](https://github.com/eclipse/eclipse.jdt.ls/issues/1555).
 * enhancement - Created type doesn't implement sealed interface. See [#1553](https://github.com/eclipse/eclipse.jdt.ls/issues/1553).
 * enhancement - Find references to fields via getters/setters. See [#1548](https://github.com/eclipse/eclipse.jdt.ls/issues/1548).
 * enhancement - Improve semantic token modifiers. See [#1539](https://github.com/eclipse/eclipse.jdt.ls/pull/1539).
 * bug fix - Update 4.17-P-builds to P20201001-0300. See [#1559](https://github.com/eclipse/eclipse.jdt.ls/pull/1559).

# [0.62.0 (September 30th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/80?closed=1)
 * enhancement - Add JavaFX support. See [#1536](https://github.com/eclipse/eclipse.jdt.ls/pull/1536).
 * enhancement - Add support for Java 15. See [#1543](https://github.com/eclipse/eclipse.jdt.ls/issues/1543).
 * enhancement - Report invalid runtime config back to the client. See [#1550](https://github.com/eclipse/eclipse.jdt.ls/pull/1550).
 * enhancement - Move the code action 'Change modifiers to final where possible' to a source action. See [#1547](https://github.com/eclipse/eclipse.jdt.ls/pull/1547).
 * bug fix - Fix semantic tokens offset due to document updates. See [#1552](https://github.com/eclipse/eclipse.jdt.ls/pull/1552).

# [0.61.0 (September 16th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/79?closed=1)
 * enhancement - Importing mixed (maven,gradle,eclipse) projects. See [#1532](https://github.com/eclipse/eclipse.jdt.ls/pull/1532).
 * enhancement - Allow to customize fileHeader and typeComment for the new Java file. See [#1540](https://github.com/eclipse/eclipse.jdt.ls/pull/1540).
 * enhancement - Better expose the "Anonymous to nested class" refactoring. See [#1541](https://github.com/eclipse/eclipse.jdt.ls/pull/1541).
 * enhancement - Can trigger 'convert var' and 'convert resolved type' on types. See [#1544](https://github.com/eclipse/eclipse.jdt.ls/pull/1544).
 * bug fix - Fixed override method proposal. See [#1537](https://github.com/eclipse/eclipse.jdt.ls/pull/1537).
 * build - Update TP to use Eclipse 2020-09 RC2. See [#1546](https://github.com/eclipse/eclipse.jdt.ls/pull/1546).

# [0.60.0 (September 1st, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/78?closed=1)
 * enhancement - Update to Eclipse 2020-09-M3. See [#1534](https://github.com/eclipse/eclipse.jdt.ls/pull/1534).
 * enhancement - Use `,` as signature trigger char. See [#1522](https://github.com/eclipse/eclipse.jdt.ls/pull/1522).
 * enhancement - Add `java.import.resourceFilter` preference. See [#1508](https://github.com/eclipse/eclipse.jdt.ls/issues/1508).
 * performance - Use ASTProvider to getAST for source action handlers. See [#1533](https://github.com/eclipse/eclipse.jdt.ls/pull/1533).
 * bug fix - Fixed wildcard import semantic tokens. See [#1518](https://github.com/eclipse/eclipse.jdt.ls/pull/1518).

# [0.59.0 (July 22nd, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/77?closed=1)
 * enhancement - Expose the 'java.import.gradle.java.home' preference. See [#1512](https://github.com/eclipse/eclipse.jdt.ls/pull/1512).
 * enhancement - Provide semantic tokens for class files. See [#1511](https://github.com/eclipse/eclipse.jdt.ls/pull/1511).
 * enhancement - Keep typing after the 1st tab when completing imports. See [#1510](https://github.com/eclipse/eclipse.jdt.ls/pull/1510).
 * enhancement - Semantic highlighting improvements. See [#1501](https://github.com/eclipse/eclipse.jdt.ls/pull/1501).
 * enhancement - Add "Introduce parameter..." code action. See [#1420](https://github.com/eclipse/eclipse.jdt.ls/issues/1420).
 * bug fix - NPE in CodeActionHandler.getProblemId L.221. See [#1502](https://github.com/eclipse/eclipse.jdt.ls/issues/1502).
 * build - Require Java 11 to build/run. See [#1509](https://github.com/eclipse/eclipse.jdt.ls/pull/1509).

# [0.58.0 (July 7th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/76?closed=1)
 * enhancement - jdt.ls should store gradle wrapper checksums at build time, so it can work offline. See [#1486](https://github.com/eclipse/eclipse.jdt.ls/issues/1486).
 * enhancement - fill additionalTextEdits during completionItem/resolve . See [#1487](https://github.com/eclipse/eclipse.jdt.ls/pull/1487).
 * enhancement - Update Buildship to 3.1.5. See [#1494](https://github.com/eclipse/eclipse.jdt.ls/pull/1494).
 * enhancement - Improve Java LS shutdown. See [#1495](https://github.com/eclipse/eclipse.jdt.ls/pull/1495).
 * bug fix - Prepare rename breaks if you have edited the symbol just before the call. See [#1483](https://github.com/eclipse/eclipse.jdt.ls/issues/1483).
 * build - Build from command line failure involving "org.codehaus.gmaven:groovy-maven-plugin:2.1.1". See [#1497](https://github.com/eclipse/eclipse.jdt.ls/issues/1497).
 * other - README: mention system props for configuring the connection mode (#1496). See [#1499](https://github.com/eclipse/eclipse.jdt.ls/pull/1499).

# [0.57.0 (June 18th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/75?closed=1)
 * enhancement - Avoid unnecessary Gradle re-synch on restart. See [#1485](https://github.com/eclipse/eclipse.jdt.ls/pull/1485).
 * enhancement - Optimize default VM management to avoid unnecessary project updates. See [#1484](https://github.com/eclipse/eclipse.jdt.ls/pull/1484).
 * enhancement - Update to Eclipse 4.16. See [#1478](https://github.com/eclipse/eclipse.jdt.ls/pull/1478).
 * enhancement - Support annotations in semantic highlighting. See [#1477](https://github.com/eclipse/eclipse.jdt.ls/pull/1477).
 * enhancement - Wait for jobs to complete when resolving the classpaths. See [#1476](https://github.com/eclipse/eclipse.jdt.ls/pull/1476).
 * enhancement - Java runtimes should be configured before projects are imported. See [#1474](https://github.com/eclipse/eclipse.jdt.ls/issues/1474).
 * enhancement - Send started status when light weight server is ready. See [#1472](https://github.com/eclipse/eclipse.jdt.ls/pull/1472).
 * enhancement - Enable code completion for syntax server. See [#1463](https://github.com/eclipse/eclipse.jdt.ls/pull/1463).
 * enhancement - IBuildSupport extension point. See [#1455](https://github.com/eclipse/eclipse.jdt.ls/pull/1455).
 * enhancement - Can get VM installation path through ProjectCommand.getProjectSettings(). See [#1454](https://github.com/eclipse/eclipse.jdt.ls/pull/1454).
 * bug fix - Fix NPE in BaseDocumentLifeCycleHandler.publishDiagnostics. See [#1473](https://github.com/eclipse/eclipse.jdt.ls/pull/1473).
 * bug fix - Correctly assign the queueLength in WrapperValidator. See [#1470](https://github.com/eclipse/eclipse.jdt.ls/pull/1470).
 * bug fix - Organize import on save should not select ambiguous static import. See [#1459](https://github.com/eclipse/eclipse.jdt.ls/pull/1459).

# [0.56.0 (May 21st, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/74?closed=1)
 * enhancement - Recognize new maven, gradle, eclipse project(s) after first init. See [#144](https://github.com/eclipse/eclipse.jdt.ls/issues/144).
 * enhancement - Add a new command to get all Java projects. See [#1447](https://github.com/eclipse/eclipse.jdt.ls/pull/1447).
 * enhancement - Expand the API usages. See [#1446](https://github.com/eclipse/eclipse.jdt.ls/pull/1446).
 * enhancement - Use default JVM when importing gradle project. See [#1430](https://github.com/eclipse/eclipse.jdt.ls/pull/1430).
 * enhancement - Check for suspicious gradle-wrapper.jar. See [#1434](https://github.com/eclipse/eclipse.jdt.ls/pull/1434).
 * bug fix - Discard the stale workingcopies that belonged to the deleted folder. See [#1439](https://github.com/eclipse/eclipse.jdt.ls/pull/1439).
 * bug fix - jdt.ls shouldn't modify disk file when handling newly created or renamed files. See [#1438](https://github.com/eclipse/eclipse.jdt.ls/pull/1438).
 * bug fix - Respect 'java.codeGeneration.generateComments' when generating accessors on completion. See [#1437](https://github.com/eclipse/eclipse.jdt.ls/pull/1437).
 * bug fix - highlight QualifiedName together in packages. See [#1435](https://github.com/eclipse/eclipse.jdt.ls/pull/1435).
 * bug fix - Favorite static imports are ignored if server launched without advancedOrganizeImportsSupport:true . See [#1422](https://github.com/eclipse/eclipse.jdt.ls/issues/1422).

# [0.55.0 (April 30th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/73?closed=1)
 * enhancement - refine semantic highlighting. See [#1416](https://github.com/eclipse/eclipse.jdt.ls/pull/1416).
 * enhancement - Organize Imports should resolve static imports as well. See [#1415](https://github.com/eclipse/eclipse.jdt.ls/pull/1415).
 * enhancement - Support refactoring package name. See [#1414](https://github.com/eclipse/eclipse.jdt.ls/pull/1414).
 * bug fix - Code folding is buggy. See [#1419](https://github.com/eclipse/eclipse.jdt.ls/issues/1419).
 * bug fix - clean out-of-date fAST before updating new fActiveJavaElement. See [#1418](https://github.com/eclipse/eclipse.jdt.ls/pull/1418).
 * bug fix - wait for lifecycle jobs before computing semantic tokens. See [#1412](https://github.com/eclipse/eclipse.jdt.ls/pull/1412).
 * build - CompletionHandlerTest.testStarImports fails randomly . See [#1409](https://github.com/eclipse/eclipse.jdt.ls/issues/1409).

# [0.54.0 (April 16th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/72?closed=1)
 * enhancement - support semantic tokens. See [#1408](https://github.com/eclipse/eclipse.jdt.ls/pull/1408).
 * enhancement - add threshold for organizing imports with the asterisk (*) wildcard character. See [#1407](https://github.com/eclipse/eclipse.jdt.ls/pull/1407).
 * enhancement - compute the rename updates after files renamed. See [#1406](https://github.com/eclipse/eclipse.jdt.ls/pull/1406).
 * enhancement - add 'Generate constructor' option to 'Show Fixes' options for fields. See [#1405](https://github.com/eclipse/eclipse.jdt.ls/pull/1405).
 * enhancement - make syntax server support hovering over a type. See [#1403](https://github.com/eclipse/eclipse.jdt.ls/pull/1403).

## [0.53.0 (April 1st, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/71?closed=1)
 * enhancement - add Java 14 support. See [#1391](https://github.com/eclipse/eclipse.jdt.ls/pull/1391).
 * enhancement - update Buildship for Java 14 support. See [#1397](https://github.com/eclipse/eclipse.jdt.ls/pull/1397).
 * enhancement - provide `record` snippet. See [#1393](https://github.com/eclipse/eclipse.jdt.ls/issues/1393).
 * enhancement - add Javadoc completion for `record`s. See [#1396](https://github.com/eclipse/eclipse.jdt.ls/issues/1396).
 * enhancement - j.i.gradle.arguments and j.i.gradle.jvmArguments aren't properly defined. See [#1387](https://github.com/eclipse/eclipse.jdt.ls/pull/1387).
 * enhancement - use stable lsp4j 0.9.0 release. See [#1382](https://github.com/eclipse/eclipse.jdt.ls/issues/1382).
 * enhancement - enable syntax mode when importing a partial folder of maven/gradle project. See [#1364](https://github.com/eclipse/eclipse.jdt.ls/pull/1364).
 * bug fix - root path in the preference manager won't update when workspace folder changes. See [#1388](https://github.com/eclipse/eclipse.jdt.ls/issues/1388).
 * bug fix - fixed BadLocationException and diagnostic with negative line number send to client. See [#1374](https://github.com/eclipse/eclipse.jdt.ls/issues/1374).

## [0.52.0 (March 5th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/70?closed=1)
* enhancement - provide diagnostics and quickfixes when opening non-project java files. See [#1366](https://github.com/eclipse/eclipse.jdt.ls/pull/1366).
* enhancement - parallel downloads of jars, for Maven projects. See [#1369](https://github.com/eclipse/eclipse.jdt.ls/pull/1369).
* enhancement - allow renaming of lambda parameters. See [#1375](https://github.com/eclipse/eclipse.jdt.ls/pull/1375).
* enhancement - build workspace action can report progress to client. See [#1368](https://github.com/eclipse/eclipse.jdt.ls/pull/1368).
* bug fix - fixed inconsistent filterText and textEdit returned, violating the LSP spec. See [#1348](https://github.com/eclipse/eclipse.jdt.ls/issues/1348).
* bug fix - fixed launch configs to use `com.ibm.icu`. See [#1365](https://github.com/eclipse/eclipse.jdt.ls/pull/1365).
* debt - remove direct dependency to ICU4J. See [#1362](https://github.com/eclipse/eclipse.jdt.ls/pull/1362).
* build - Use more stable update sites in TP. See [#1378](https://github.com/eclipse/eclipse.jdt.ls/pull/1378).

## [0.51.0 (February 19th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/69?closed=1)
* bug fix - update buildship to 3.1.4 to fix issues with projects not using the Gradle wrapper. See [#1354](https://github.com/eclipse/eclipse.jdt.ls/issues/1354).
* bug fix - fixed Java suggestion details missing in some circumstances. See [#1353](https://github.com/eclipse/eclipse.jdt.ls/pull/1353).

## [0.50.0 (February 17th, 2020)](https://github.com/eclipse/eclipse.jdt.ls/milestone/68?closed=1)
* enhancement - added code actions to remove the `final` modifier. See [#441](https://github.com/eclipse/eclipse.jdt.ls/issues/441).
* enhancement - added `java.configuration.runtimes` preference for mapping Java Execution Environments to local JDK runtimes. See [#1307](https://github.com/eclipse/eclipse.jdt.ls/issues/1307).
* enhancement - added code actions to assign statement to new variable/field. See [#1319](https://github.com/eclipse/eclipse.jdt.ls/issues/1319).
* enhancement - added code action to remove redundant interfaces. See [#438](https://github.com/eclipse/eclipse.jdt.ls/issues/438).
* enhancement - added `java.import.gradle.offline.enabled` preference. See [#1308](https://github.com/eclipse/eclipse.jdt.ls/pull/1308).
* enhancement - added code action to add missing case labels in switch statements. See [#1140](https://github.com/eclipse/eclipse.jdt.ls/issues/1140).
* enhancement - expose full completion proposals to 3rd party extensions. See [#1344](https://github.com/eclipse/eclipse.jdt.ls/pull/1344).
* bug fix - fixed Intellisense not working when attached javadoc can't be read. See [#1314](https://github.com/eclipse/eclipse.jdt.ls/pull/1314).
* bug fix - fixed duplicate labels in progress reports. See [#1321](https://github.com/eclipse/eclipse.jdt.ls/pull/1321).
* bug fix - added default value to `java.project.referencedLibraries`'s exclude and sources. See [#1315](https://github.com/eclipse/eclipse.jdt.ls/pull/1315).
* build - fixed failing FormatterHandlerTests du to upstream changes in formatting. See [#1317](https://github.com/eclipse/eclipse.jdt.ls/issues/1317).
* build - fixed build failure due to LSP4J API changes. See [#1340](https://github.com/eclipse/eclipse.jdt.ls/issues/1340).

## [0.49.0 (December 23rd, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/67?closed=1)
* enhancement - added support for Call Hierarchy. See [#508](https://github.com/eclipse/eclipse.jdt.ls/issues/508).
* enhancement - add jars to classpath via new `java.project.referencedLibraries` preference. See [#1305](https://github.com/eclipse/eclipse.jdt.ls/pull/1305).
* enhancement - include TextEdit to completion requests, as defined by the LSP. Completion results are now limited via `java.completion.maxResults` preference. See [#465](https://github.com/eclipse/eclipse.jdt.ls/issues/465) and [#1298](https://github.com/eclipse/eclipse.jdt.ls/pull/1298).
* enhancement - Remove duplicate call of getRawLocationURI(). See [#1299](https://github.com/eclipse/eclipse.jdt.ls/pull/1299).
* bug fixed - fixed incorrect signatures returned by signatureHelp. See [#1290](https://github.com/eclipse/eclipse.jdt.ls/issues/1290).
* bug fixed - fixed broken signatureHelp when previous string parameter has `(` or `{`. See [#1293](https://github.com/eclipse/eclipse.jdt.ls/issues/1293).
* debt - relicensed project to EPL-v2.0. See [#897](https://github.com/eclipse/eclipse.jdt.ls/issues/897).

## [0.48.0 (December 4rd, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/66?closed=1)
* enhancement - add quickfix to correct access to static elements. See [#439](https://github.com/eclipse/eclipse.jdt.ls/issues/439).
* enhancement - new `java.maven.updateSnapshots` preference to update snapshots/releases for Maven projects. See [#1217](https://github.com/eclipse/eclipse.jdt.ls/pull/1217).
* enhancement - sort code actions by relevance. See [#1250](https://github.com/eclipse/eclipse.jdt.ls/issues/1250).
* enhancement - enhanced referenced library support. See [#1257](https://github.com/eclipse/eclipse.jdt.ls/issues/1257).
* enhancement - use ProgressReporter to monitor initialization jobs. See [#1280](https://github.com/eclipse/eclipse.jdt.ls/pull/1280).
* enhancement - jump to definition on break/continue. See [#1281](https://github.com/eclipse/eclipse.jdt.ls/pull/1281).
* enhancement - no need to publish diagnostics in BuildWorkspaceHandler. See [#1282](https://github.com/eclipse/eclipse.jdt.ls/pull/1282).
* bug fixed - set correct kind for action "Add final modifier where possible". See [#1266](https://github.com/eclipse/eclipse.jdt.ls/issues/1266).
* bug fixed - call corresponding code action processor according to base kind. See [#1275](https://github.com/eclipse/eclipse.jdt.ls/pull/1275).
* bug fixed - should update the diagnostics for the deleted resource and it's children. See [#1283](https://github.com/eclipse/eclipse.jdt.ls/pull/1283).
* bug fixed - added the location check back when loading bundles. See [#1286](https://github.com/eclipse/eclipse.jdt.ls/issues/1286).
* debt - BasicFileDetectorTest.testInclusions* fail randomly. See [#1262](https://github.com/eclipse/eclipse.jdt.ls/issues/1262).
* debt - HoverHandlerTest.testHoverOnPackageWithJavadoc fails. See [#1284](https://github.com/eclipse/eclipse.jdt.ls/issues/1284).
* build/infra - CI jobs "Killed" randomly. See [#1275](https://github.com/eclipse/eclipse.jdt.ls/issues/1274).

## [0.47.0 (November 14th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/65?closed=1)

* enhancement - code action: remove unnecessary cast. See [#165](https://github.com/eclipse/eclipse.jdt.ls/issues/165).
* enhancement - provide better symbol details on hover. See [#1227](https://github.com/eclipse/eclipse.jdt.ls/issues/1227).
* enhancement - update m2e to 1.14 (embeds Maven 3.6.2). See [#1238](https://github.com/eclipse/eclipse.jdt.ls/pull/1238).
* enhancement - code action: improve "Invert Condition" refactoring trigger. See [#1230](https://github.com/eclipse/eclipse.jdt.ls/issues/1230).
* enhancement - code action: add final modifier where possible. See [#1234](https://github.com/eclipse/eclipse.jdt.ls/pull/1234).
* enhancement - refresh the bundles after uninstalling. See [#1253](https://github.com/eclipse/eclipse.jdt.ls/pull/1253).
* bug fixed  - add Java 13 support for Gradle projects. See [#1196](https://github.com/eclipse/eclipse.jdt.ls/issues/1196).
* bug fixed - fixed build job reporting errors from unrelated gradle projects outside the workspace. See [#1261](https://github.com/eclipse/eclipse.jdt.ls/issues/1261).
* bug fixed - fixed Maven import failure caused by m2e-apt unable to parse maven-compiler-plugin configuration. See [#1228](https://github.com/eclipse/eclipse.jdt.ls/issues/1228).

## [0.46.0 (October 23rd, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/64?closed=1)

* enhancement - tag IProblem.UnusedImport as DiagnosticTag.Unnecessary. See [#1219](https://github.com/eclipse/eclipse.jdt.ls/issues/1219).
* enhancement - support selection range in snippets. See [#1220](https://github.com/eclipse/eclipse.jdt.ls/issues/1220).

## [0.45.0 (October 16th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/63?closed=1)

* enhancement - completion should provide code snippets (sysout/syserr/systrace/conditions/loops). See [#977](https://github.com/eclipse/eclipse.jdt.ls/issues/977).
* enhancement - improve snippet documentation rendering. See [#1205](https://github.com/eclipse/eclipse.jdt.ls/issues/1205).
* enhancement - allow negative patterns in `java.import.exclusions` preference, to allow folder inclusions. See [#1200](https://github.com/eclipse/eclipse.jdt.ls/pull/1200).
* bug fix - don't return workspace symbols without a name. See [#1204](https://github.com/eclipse/eclipse.jdt.ls/issues/1204).
* bug fix - fixed package fragments not updated when adding a new folder. See [#1137](https://github.com/eclipse/eclipse.jdt.ls/issues/1137).
* bug fix - only enable preview features for the latest available JDK. See [#1197](https://github.com/eclipse/eclipse.jdt.ls/pull/1197).
* bug fix - don't filter methods from filtered types' code completion. See [#1212](https://github.com/eclipse/eclipse.jdt.ls/issues/1212).

## [0.44.0 (October 1st, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/62?closed=1)

* enhancement - added Java 13 support for Maven and Eclipse projects. See [#1179](https://github.com/eclipse/eclipse.jdt.ls/issues/1179).
* enhancement - code-action: fixed methods with reduced visibility. See [#442](https://github.com/eclipse/eclipse.jdt.ls/issues/442).
* enhancement - code-action: inline method/variable/field. See [#656](https://github.com/eclipse/eclipse.jdt.ls/issues/656) and [#771](https://github.com/eclipse/eclipse.jdt.ls/issues/771).
* enhancement - provide more granularity of progress during Maven import. See [#1121](https://github.com/eclipse/eclipse.jdt.ls/issues/1121).
* enhancement - added support for diagnostic tags. See [#1162](https://github.com/eclipse/eclipse.jdt.ls/issues/1162).
* enhancement - update Buildship to 3.1.2. See [#1195](https://github.com/eclipse/eclipse.jdt.ls/pulls/1195).
* bug fix - fixed wrong range for `Surround with try/multi-catch` code action. See [#1189](https://github.com/eclipse/eclipse.jdt.ls/issues/1189).
* debt -  use sequence rank to get the first position in the position group. See [#1180](https://github.com/eclipse/eclipse.jdt.ls/issues/1180).

## [0.43.0 (September 18th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/61?closed=1)

* enhancement - code action: fix non accessible references. See [#440](https://github.com/eclipse/eclipse.jdt.ls/issues/440).
* enhancement - code action: create non existing package when package declaration mismatch. See [#1163](https://github.com/eclipse/eclipse.jdt.ls/pull/1163).
* enhancement - code action: convert for-loop to for-each loop. See [#1166](https://github.com/eclipse/eclipse.jdt.ls/issues/1166).
* enhancement - code action: convert anonymous class to nested class. See [#1177](https://github.com/eclipse/eclipse.jdt.ls/issues/1177).
* enhancement - navigate to the super implementation. See [#1165](https://github.com/eclipse/eclipse.jdt.ls/pull/1165).
* enhancement - exclude certain packages from autocomplete/autoimport. See [#1176](https://github.com/eclipse/eclipse.jdt.ls/pull/1176).
* bug fix - extract embedded javadoc images. See [#1138](https://github.com/eclipse/eclipse.jdt.ls/pull/1138).
* bug fix - fixed "No delegateCommandHandler for 'xxx'" error. See [#1146](https://github.com/eclipse/eclipse.jdt.ls/issues/1146).
* bug fix - fixed Javadoc table conversion to Markdown. See [#1167](https://github.com/eclipse/eclipse.jdt.ls/pull/1167).
* bug fix - fixed wrong completion text for AnonymousDeclarationType. See [#1168](https://github.com/eclipse/eclipse.jdt.ls/issues/1168).
* bug fix - fixed client never receiving server Ready notification. See [#1170](https://github.com/eclipse/eclipse.jdt.ls/pull/1170).
* bug fix - load bundle only once if same bundle occurs multiple times in different locations. See [#1174](https://github.com/eclipse/eclipse.jdt.ls/pull/1174).
* bug fix - fixed incorrect `prepareRename` response when called over import. See [#1175](https://github.com/eclipse/eclipse.jdt.ls/issues/1175).
* build - add org.eclipse.ant.core into the launch config. See [#1173](https://github.com/eclipse/eclipse.jdt.ls/pull/1173).

## [0.42.0 (September 4th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/60?closed=1)
* enhancement - code action to create unresolved types. See [#853](https://github.com/eclipse/eclipse.jdt.ls/issues/853).
* enhancement - ignore "Unsupported SuppressWarning" warnings by default. See [#1062](https://github.com/eclipse/eclipse.jdt.ls/issues/1062).
* enhancement - properly render @ApiNote in javadoc. See [#1069](https://github.com/eclipse/eclipse.jdt.ls/issues/1069).
* enhancement - code action to move class to another package. See [#1089](https://github.com/eclipse/eclipse.jdt.ls/issues/1089).
* enhancement - code action to move member to another class. See [#1089](https://github.com/eclipse/eclipse.jdt.ls/issues/1089).
* enhancement - code action to move inner types to new class. See [#1089](https://github.com/eclipse/eclipse.jdt.ls/issues/1089).
* enhancement - code action to 'Invert local variable'. See [#1117](https://github.com/eclipse/eclipse.jdt.ls/issues/1117).
* enhancement - code action to convert lambda to anonymous class. See [#1119](https://github.com/eclipse/eclipse.jdt.ls/issues/1119).
* bug fix - fixed find implementation doesn't work on classes. See [#1098](https://github.com/eclipse/eclipse.jdt.ls/issues/1098).
* bug fix - fixed NavigateToDefinitionHandler should not return null. See [#1143](https://github.com/eclipse/eclipse.jdt.ls/pull/1143).
* bug fix - fixed secondary same-line error not reported. See [#1147](https://github.com/eclipse/eclipse.jdt.ls/issues/1147).
* bug fix - fixed go to implementation doesn't work for method invocation. See [#1149](https://github.com/eclipse/eclipse.jdt.ls/pull/1149).
* debt - migrate to BindingLabelProviderCore.See [#1155](https://github.com/eclipse/eclipse.jdt.ls/pull/1155).

## [0.41.0 (July 18th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/59?closed=1)
* enhancement - added code action to convert a local variable to a field. See [#772](https://github.com/eclipse/eclipse.jdt.ls/issues/772).
* enhancement - migrated to lsp4j 0.7.2. See [#1040](https://github.com/eclipse/eclipse.jdt.ls/issues/1040).
* enhancement - cancel init or update workspace job of removed rootPaths. See [#1064](https://github.com/eclipse/eclipse.jdt.ls/pull/1064).
* enhancement - trigger client autorename after 'extract to variable/constant/method'. See [#1077](https://github.com/eclipse/eclipse.jdt.ls/pull/1077).
* enhancement - prevented aggressive classpath updates when jars don't change. See [#1078](https://github.com/eclipse/eclipse.jdt.ls/pull/1078).
* enhancement - new extension point to register static commands during JDT LS initialization . See [#1084](https://github.com/eclipse/eclipse.jdt.ls/issues/1084).
* enhancement - added additional Gradle preferences. See [#1092](https://github.com/eclipse/eclipse.jdt.ls/pull/1092).
* enhancement - added support for "textDocument/selectionRange". See [#1100](https://github.com/eclipse/eclipse.jdt.ls/issues/1100).
* enhancement - support non-CUCorrectionProposal for CodeActions. See [#1103](https://github.com/eclipse/eclipse.jdt.ls/issues/1103).
* enhancement - updated m2e to 1.13 -> Use latest Execution Environment when source/target is not yet supported. See [m2e#549312](https://bugs.eclipse.org/bugs/show_bug.cgi?id=549312).
* bug fix - fixed signature help returning the wrong active parameter. See [#1039](https://github.com/eclipse/eclipse.jdt.ls/issues/1039).
* bug fix - use the default `GRADLE_USER_HOME` env var if possible, for Gradle wrappers and modules. See [#1072](https://github.com/eclipse/eclipse.jdt.ls/pull/1072).
* bug fix - fixed signature help stopped working after using a lambda. See [#1086](https://github.com/eclipse/eclipse.jdt.ls/issues/1086).
* bug fix - fixed ChangeUtil for non-MultiTextEdit conversion. See [#1095](https://github.com/eclipse/eclipse.jdt.ls/pull/1095).
* bug fix - fixed IllegalArgumentException thrown on completionItem/resolve if there's no javadoc. See [#1107](https://github.com/eclipse/eclipse.jdt.ls/issues/1107).
* bug fix - properly filter code actions according to context.only values. See [#1112](https://github.com/eclipse/eclipse.jdt.ls/pull/1112).
* build - added launch configurations for remote debugging. See [#1067](https://github.com/eclipse/eclipse.jdt.ls/pull/1067).
* debt - refactor ChangeUtil: universal API converting Change to WorkspaceEdit. See [1106](https://github.com/eclipse/eclipse.jdt.ls/pull/1106).

## [0.40.0 (June 5th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/58?closed=1)
* enhancement - added code action to generate constructors. See [#972](https://github.com/eclipse/eclipse.jdt.ls/issues/972).
* enhancement - added code action to generate delegate methods. See [#1042](https://github.com/eclipse/eclipse.jdt.ls/issues/1042).
* enhancement - updated buildship to 3.1.0. See [Buildship changelog](https://discuss.gradle.org/t/buildship-3-1-is-now-available/31600).
* enhancement - updated m2e to 1.12 (now embeds Maven 3.6.1). See [m2e changelog](https://projects.eclipse.org/projects/technology.m2e/releases/1.12/bugs).
* enhancement - provide more info on hover for constant fields. See [#1049](https://github.com/eclipse/eclipse.jdt.ls/issues/1049).
* bug fix - fixed Signature Help didn't match active parameter per type. See [#1037](https://github.com/eclipse/eclipse.jdt.ls/issues/1037).
* bug fix - fixed disabling Gradle wrapper in certain cases. See [#1044](https://github.com/eclipse/eclipse.jdt.ls/issues/1044).

## [0.39.0 (May 15th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/57?closed=1)
* enhancement - added `Assign parameters to new fields` source actions. See [#167](https://github.com/eclipse/eclipse.jdt.ls/issues/167).
* enhancement - added code action for adding non existing constructor from super class. See [#767](https://github.com/eclipse/eclipse.jdt.ls/issues/767).
* enhancement - use the `java.codeGeneration.generateComments` preference to generate comments for getter and setter. See [#1024](https://github.com/eclipse/eclipse.jdt.ls/pull/1024).
* enhancement - optionally disable loading gradle from gradle wrapper and use a specific Gradle version. See [#1026](https://github.com/eclipse/eclipse.jdt.ls/pull/1026).
* bug fix - fixed NPE when closing a renamed file. See [#993](https://github.com/eclipse/eclipse.jdt.ls/issues/993).
* bug fix - fixed potential NPE with a bad formatter URL. See [#1029](https://github.com/eclipse/eclipse.jdt.ls/pull/1029).
* bug fix - fixed Signature Help for constructors. See [#1030](https://github.com/eclipse/eclipse.jdt.ls/issues/1030).

## [0.38.0 (May 2nd, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/56?closed=1)
* enhancement - batch Maven project imports when available ram < 1.5GB and number of projects > 50, to reduce memory consumption. See [#982](https://github.com/eclipse/eclipse.jdt.ls/issues/982).
* enhancement - added advanced `Generate getters and setters...` source action. See [#992](https://github.com/eclipse/eclipse.jdt.ls/issues/992).
* enhancement - tentative workaround for poor resource refresh performance on Windows. See [#1001](https://github.com/eclipse/eclipse.jdt.ls/pull/1001).
* enhancement - show more progress details of workspace jobs. See [#1005](https://github.com/eclipse/eclipse.jdt.ls/pull/1005).
* enhancement - log resource path and line number of build errors. See [#1013](https://github.com/eclipse/eclipse.jdt.ls/issues/1013).
* bug fix - update classpath when jar files are modified. See [#1002](https://github.com/eclipse/eclipse.jdt.ls/pull/1002).
* bug fix - fixed NPE when peeking implementation on generic types. See [#1004](https://github.com/eclipse/eclipse.jdt.ls/issues/1004).
* bug fix - only return signature help on method invocation and javadoc reference. See [#1009](https://github.com/eclipse/eclipse.jdt.ls/issues/1009).
* bug fix - properly detect active signature in signature help. See [#1017](https://github.com/eclipse/eclipse.jdt.ls/issues/1017).
* bug fix - use proper kinds for interfaces, enums and constants, in completion and document symbols. See [#1012](https://github.com/eclipse/eclipse.jdt.ls/issues/1012).
* bug fix - remove ellipsis on `Create getter and setter for` label. See [#1019](https://github.com/eclipse/eclipse.jdt.ls/pull/1019).

## [0.37.0 (April 17th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/55?closed=1)
* enhancement - added `Generate toString()...` source action. See [#736](https://github.com/eclipse/eclipse.jdt.ls/issues/736).
* enhancement - dynamically add filewatchers. See [#926](https://github.com/eclipse/eclipse.jdt.ls/issues/926).
* enhancement - download Java sources lazily for Maven projects. See [#979](https://github.com/eclipse/eclipse.jdt.ls/issues/979).
* enhancement - optimize CompilationUnit computations. See [#980](https://github.com/eclipse/eclipse.jdt.ls/issues/980).
* enhancement - optimize server initialization. See [#981](https://github.com/eclipse/eclipse.jdt.ls/issues/981).
* enhancement - show more detailed progress report on startup. See [#997](https://github.com/eclipse/eclipse.jdt.ls/pull/997).
* bug fix - completion cache resets after file recompilation resulting in slow code completion. See [#847](https://github.com/eclipse/eclipse.jdt.ls/issues/847).
* bug fix - fix jar detection on windows, for invisible projects. See [#998](https://github.com/eclipse/eclipse.jdt.ls/pull/998).


## [0.36.1 (April 1st, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/54?closed=1)
* bug fix - Only enable the preview flag if the JVM supports it. See [#975](https://github.com/eclipse/eclipse.jdt.ls/pull/975).

## [0.36.0 (March 29th, 2019)](https://github.com/eclipse/eclipse.jdt.ls/milestone/53?closed=1)
* enhancement - added "imports" folding support. See [#555](https://github.com/redhat-developer/vscode-java/issues/555).
* enhancement - added UI to manage ambiguous imports. See [#673](https://github.com/redhat-developer/vscode-java/issues/673).
* enhancement - added `Convert to static import` code actions. See [#796](https://github.com/redhat-developer/vscode-java/issues/796).
* enhancement - eliminated CPU usage when idling on Windows. See [#843](https://github.com/redhat-developer/vscode-java/pull/843).
* enhancement - added Java 12 support. See [#671](https://github.com/redhat-developer/vscode-java/issues/671).
* bug fix - fixed occasional NPE when navigating to class, on Linux. See [#963](https://github.com/eclipse/eclipse.jdt.ls/issues/963).

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

