# Change Log

# [1.20.0 (February 16th, 2023)](https://github.com/eclipse/eclipse.jdt.ls/milestone/110?closed=1)
 * performance - Skip generated methods when calculating document symbols. See [#2446](https://github.com/eclipse/eclipse.jdt.ls/issues/2446).
 * performance - Make the debounce adaptive for the publish diagnostic job. See [#2443](https://github.com/eclipse/eclipse.jdt.ls/pull/2443).
 * performance - Only perform context sensitive import rewrite when resolving completion items. See [#2453](https://github.com/eclipse/eclipse.jdt.ls/pull/2453).
 * performance - Copy/paste within the same file should not trigger the paste handler for missing imports. See [#2441](https://github.com/eclipse/eclipse.jdt.ls/issues/2441).
 * enhancement - Support "extract interface" refactoring. See [#2373](https://github.com/eclipse/eclipse.jdt.ls/pull/2373), [#2459](https://github.com/eclipse/eclipse.jdt.ls/pull/2459).
 * enhancement - Add 'Convert String concatenation to Text Block' quick assist. See [#2456](https://github.com/eclipse/eclipse.jdt.ls/pull/2456).
 * enhancement - Add clean up for using try-with-resource. See [#2344](https://github.com/eclipse/eclipse.jdt.ls/issues/2344).
 * enhancement - Enable formatting support in syntax server. See [#2450](https://github.com/eclipse/eclipse.jdt.ls/pull/2450).
 * enhancement - Add option to configure behaviour when mojo execution metadata not available. See [#2426](https://github.com/eclipse/eclipse.jdt.ls/pull/2426).
 * enhancement - Add option to permit usage of test resources of a Maven project as dependencies within the compile scope of other projects. See [#2399](https://github.com/eclipse/eclipse.jdt.ls/pull/2399).
 * bug fix - Change default generated method stub to throw exception. See [#2366](https://github.com/eclipse/eclipse.jdt.ls/pull/2366).
 * bug fix - Prevent the paste handler for missing imports from generating overlapping text edits. See [#2442](https://github.com/eclipse/eclipse.jdt.ls/issues/2442).
 * bug fix - Reference search doesn't work for fields in JDK classes. See [#2405](https://github.com/eclipse/eclipse.jdt.ls/issues/2405).
 * bug fix - Completion results should include filtered (excluded) types if they are also present in the import declarations. See [#2467](https://github.com/eclipse/eclipse.jdt.ls/pull/2467).
 * bug fix - Re-publish diagnostics for null analysis configuration change when auto-build is disabled. See [#2447](https://github.com/eclipse/eclipse.jdt.ls/pull/2447).
 * bug fix - Only do full build for a configuration change when auto-build is enabled. See [#2437](https://github.com/eclipse/eclipse.jdt.ls/pull/2437).
 * bug fix - The command to upgrade gradle should check for cancellation prior to updating metadata files. See [#2444](https://github.com/eclipse/eclipse.jdt.ls/pull/2444).
 * bug fix - Fix the missing filter text for completion items. See [#2439](https://github.com/eclipse/eclipse.jdt.ls/pull/2439).
 * bug fix - Reduce the amount of logging from `org.apache.http` bundles. See [#2420](https://github.com/eclipse/eclipse.jdt.ls/pull/2420).
 * build - Do not require `org.eclipse.xtend.lib`. See [#2416](https://github.com/eclipse/eclipse.jdt.ls/pull/2416).
 * build - Reduce target platform size by making Xtext requirements explicit. See [#2412](https://github.com/eclipse/eclipse.jdt.ls/pull/2412).
 * build - Bump eclipse-jarsigner-plugin from 1.3.5 to 1.4.2. See [#2425](https://github.com/eclipse/eclipse.jdt.ls/pull/2425), [#2435](https://github.com/eclipse/eclipse.jdt.ls/pull/2435), [#2438](https://github.com/eclipse/eclipse.jdt.ls/pull/2438).
 * build - Bump tycho-version from 3.0.1 to 3.0.2. See [#2462](https://github.com/eclipse/eclipse.jdt.ls/pull/2462).
 * build - Update `.project` files from attempting project import with JDT-LS. See [#2432](https://github.com/eclipse/eclipse.jdt.ls/pull/2432).
 * build - Fix syntax server launch file. See [#2428](https://github.com/eclipse/eclipse.jdt.ls/pull/2428).
 * build - Update target platform to latest 4.27-I-builds. See [#2403](https://github.com/eclipse/eclipse.jdt.ls/pull/2403), [#2469](https://github.com/eclipse/eclipse.jdt.ls/pull/2469).
 * build - Use the Linux configuration on FreeBSD systems. See [#2408](https://github.com/eclipse/eclipse.jdt.ls/pull/2408).
 * documentation - Fix build status badge. See [#2418](https://github.com/eclipse/eclipse.jdt.ls/issues/2418).

# [1.19.0 (January 17th, 2023)](https://github.com/eclipse/eclipse.jdt.ls/milestone/109?closed=1)
 * enhancement - Support for shared indexes among workspaces. See [#2341](https://github.com/eclipse/eclipse.jdt.ls/pull/2341).
 * enhancement - Add new delegate command to handle paste events & escaping string literals. See [#2349](https://github.com/eclipse/eclipse.jdt.ls/pull/2349).
 * enhancement - Support for missing imports on paste events. See [#2320](https://github.com/eclipse/eclipse.jdt.ls/pull/2320).
 * enhancement - Support matching case for code completion. See [#2368](https://github.com/eclipse/eclipse.jdt.ls/pull/2368).
 * enhancement - Support code action for annotation with missing required attributes. See [#1860](https://github.com/eclipse/eclipse.jdt.ls/issues/1860).
 * enhancement - Create cleanup actions for adding `final` modifier where possible, converting `switch` statement to `switch` expression, using pattern matching for `instanceof` checks, and converting anonymous functions to lambda expressions. See [#2350](https://github.com/eclipse/eclipse.jdt.ls/pull/2350).
 * enhancement - Support quickfix for gradle jpms projects. See [#2304](https://github.com/eclipse/eclipse.jdt.ls/pull/2304).
 * bug fix - Fix incorrect type hierarchy on multi module Maven projects. See [#2404](https://github.com/eclipse/eclipse.jdt.ls/pull/2404).
 * bug fix - Permit output folder to be the same as a source folder. See [#2397](https://github.com/eclipse/eclipse.jdt.ls/pull/2397).
 * bug fix - Organize imports removes static imports under some conditions. See [#2396](https://github.com/eclipse/eclipse.jdt.ls/pull/2396).
 * bug fix - Fix completion issue occuring when invocation spans multiple lines. See [#2387](https://github.com/eclipse/eclipse.jdt.ls/issues/2387).
 * bug fix - Fix scope calculation for "Surround with try/catch" refactoring. See [#2380](https://github.com/eclipse/eclipse.jdt.ls/pull/2380).
 * bug fix - Fix NPE occuring when completion item is selected. See [#2376](https://github.com/eclipse/eclipse.jdt.ls/issues/2376).
 * bug fix - Log user friendly error if client does not support `_java.reloadBundles.command`. See [#2370](https://github.com/eclipse/eclipse.jdt.ls/pull/2370).
 * bug fix - Postfix completion should not be available when editing Javadoc. See [#2367](https://github.com/eclipse/eclipse.jdt.ls/issues/2367).
 * bug fix - Update m2e to latest version in order to ensure classpath resources persist. See [#2390](https://github.com/eclipse/eclipse.jdt.ls/pull/2390).
 * build - Update m2e to latest version in order to update Logback dependency. See [#2363](https://github.com/eclipse/eclipse.jdt.ls/pull/2363).
 * build - Fix failing test cases by disabling Gradle daemon. See [#2358](https://github.com/eclipse/eclipse.jdt.ls/issues/2358).
 * build - Use `instanceof` pattern matching in code base. See [#2357](https://github.com/eclipse/eclipse.jdt.ls/pull/2357).
 * build - Create a dedicated bundle for the custom `org.eclipse.core.filesystem.filesystems` extension point. See [#2309](https://github.com/eclipse/eclipse.jdt.ls/issues/2309).
 * build - Bump eclipse-jarsigner-plugin from 1.3.4 to 1.3.5. See [#2389](https://github.com/eclipse/eclipse.jdt.ls/pull/2389).

# [1.18.0 (December 1st, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/108?closed=1)
 * enhancement - Add setting for clean ups to be applied when document is saved. See [#2298](https://github.com/eclipse/eclipse.jdt.ls/pull/2298), [#2307](https://github.com/eclipse/eclipse.jdt.ls/issues/2307), [#2342](https://github.com/eclipse/eclipse.jdt.ls/pull/2342).
 * enhancement - Support "Add all missing imports". See [#2292](https://github.com/eclipse/eclipse.jdt.ls/pull/2292).
 * enhancement - Support Gradle annotation processing. See [#2319](https://github.com/eclipse/eclipse.jdt.ls/pull/2319).
 * enhancement - Add an option to configure null analysis, and set to `interactive` by default. See [#2279](https://github.com/eclipse/eclipse.jdt.ls/pull/2279), [#2314](https://github.com/eclipse/eclipse.jdt.ls/pull/2314).
 * enhancement - Add contribution points for completion customization. See [#2110](https://github.com/eclipse/eclipse.jdt.ls/pull/2110).
 * enhancement - Allow the language server to be run without using `IApplication`. See [#2311](https://github.com/eclipse/eclipse.jdt.ls/issues/2311).
 * enhancement - Improve Lombok support and renaming fields when an accessor is present. See [#2339](https://github.com/eclipse/eclipse.jdt.ls/pull/2339).
 * bug fix - Display the postfix completions at the bottom of the list. See [#2343](https://github.com/eclipse/eclipse.jdt.ls/pull/2343).
 * bug fix - Do not reset existing project options when setting null analysis options. See [#2299](https://github.com/eclipse/eclipse.jdt.ls/pull/2299).
 * bug fix - Code action response may contain `null` as one of the code actions. See [#2327](https://github.com/eclipse/eclipse.jdt.ls/issues/2327).
 * bug fix - Inlay hints should not show up next to Lombok annotations. See [#2323](https://github.com/eclipse/eclipse.jdt.ls/issues/2323).
 * bug fix - Ensure language server always terminates. See [#2302](https://github.com/eclipse/eclipse.jdt.ls/issues/2302).
 * bug fix - Prevent a deadlock during language server initialization. See [#2301](https://github.com/eclipse/eclipse.jdt.ls/pull/2301).
 * bug fix - Always send `begin` work done progress before sending `end`. See [#2258](https://github.com/eclipse/eclipse.jdt.ls/pull/2258).
 * bug fix - Use existing Gradle project `.settings/` location if available. See [#2289](https://github.com/eclipse/eclipse.jdt.ls/pull/2289).
 * bug fix - Avoid re-using the same job for the "Publish Diagnostics" job. See [#2356](https://github.com/eclipse/eclipse.jdt.ls/pull/2356).
 * build - Use Predicate for filter. See [#2355](https://github.com/eclipse/eclipse.jdt.ls/pull/2355).
 * build - WorkspaceDiagnosticsHandlerTest.testMissingNatures fails sometimes. See [#2331](https://github.com/eclipse/eclipse.jdt.ls/issues/2331).
 * build - ProjectsManagerTest.testCancelInitJob fails randomly. See [#2326](https://github.com/eclipse/eclipse.jdt.ls/issues/2326).
 * build - Fix CodeQL Java analysis error. See [#2318](https://github.com/eclipse/eclipse.jdt.ls/pull/2318).
 * build - Set proper ranges for dependencies on LSP4J. See [#2310](https://github.com/eclipse/eclipse.jdt.ls/issues/2310).
 * build - Bump Tycho from 2.7.5 to 3.0.0. See [#2260](https://github.com/eclipse/eclipse.jdt.ls/pull/2260).

# [1.17.0 (October 27th, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/107?closed=1)
 * performance - Improve project initialization. See [#2252](https://github.com/eclipse/eclipse.jdt.ls/pull/2252).
 * performance - Re-use ExecutorService to avoid creating extra threads and resource leak. See [#2041](https://github.com/eclipse/eclipse.jdt.ls/pull/2041).
 * enhancement - Add support for postfix completion. See [#863](https://github.com/eclipse/eclipse.jdt.ls/issues/863).
 * enhancement - Add quick fix for "remove all unused imports". See [#2280](https://github.com/eclipse/eclipse.jdt.ls/pull/2280).
 * enhancement - Add quick fixes for problems relating to sealed classes. See [#2265](https://github.com/eclipse/eclipse.jdt.ls/pull/2265).
 * bug fix - Signature help not working correctly for parameterized types. See [#2293](https://github.com/eclipse/eclipse.jdt.ls/pull/2293).
 * bug fix - Avoid NPE for null analysis when updating classpath. See [#2268](https://github.com/eclipse/eclipse.jdt.ls/issues/2268).
 * bug fix - Check the digest of the initializiation scripts for security and to prevent duplicates. See [#2254](https://github.com/eclipse/eclipse.jdt.ls/pull/2254).
 * bug fix - Support `includeDeclaration` in `textDocument/references`. See [#2148](https://github.com/eclipse/eclipse.jdt.ls/issues/2148).
 * bug fix - Provide folding for import regions in `.class` files. See [#2281](https://github.com/eclipse/eclipse.jdt.ls/pull/2281).
 * bug fix - Deadlock when using JDK 17 with Maven Java project. See [#2256](https://github.com/eclipse/eclipse.jdt.ls/pull/2256).
 * bug fix - Ignore unnamed module for split packages. See [#2273](https://github.com/eclipse/eclipse.jdt.ls/pull/2273).
 * bug fix - The project preference should only persist non default values. See [#2272](https://github.com/eclipse/eclipse.jdt.ls/issues/2272).
 * bug fix - Synchronize contributed bundles on demand. See [#2267](https://github.com/eclipse/eclipse.jdt.ls/pull/2267).
 * bug fix - Avoid unnecessary project updates when the default VM changes. See [#2266](https://github.com/eclipse/eclipse.jdt.ls/pull/2266).
 * bug fix - Exclude non-compile scope dependencies from consideration for enabling null analysis. See [#2264](https://github.com/eclipse/eclipse.jdt.ls/pull/2264).
 * bug fix - Add opportunistic support for Java/Kotlin polyglot Android projects. See [#2261](https://github.com/eclipse/eclipse.jdt.ls/pull/2261).
 * debt - Bump eclipse-jarsigner-plugin from 1.3.2 to 1.3.4. See [#2262](https://github.com/eclipse/eclipse.jdt.ls/pull/2262), [#2263](https://github.com/eclipse/eclipse.jdt.ls/pull/2263).
 * debt - `jdt.ls.socket-stream.launch` & `jdt.ls.remote.server.launch` are not running cleanly. See [#2277](https://github.com/eclipse/eclipse.jdt.ls/issues/2277).

# [1.16.0 (September 29th, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/106?closed=1)
 * enhancement - Provide Java 19 preview support. See [#2209](https://github.com/eclipse/eclipse.jdt.ls/pull/2209).
 * enhancement - Enable annotation-based `null` analysis. See [#2228](https://github.com/eclipse/eclipse.jdt.ls/pull/2228).
 * enhancement - Show generate `toString()`, `hashCode()` and `equals()` quick fixes on demand. See [#2213](https://github.com/eclipse/eclipse.jdt.ls/pull/2213).
 * enhancement - Support creating `module-info.java`. See [#2231](https://github.com/eclipse/eclipse.jdt.ls/pull/2231).
 * enhancement - Only add parentheses for lambda expression completions with multiple parameters. See [#2100](https://github.com/eclipse/eclipse.jdt.ls/issues/2100).
 * enhancement - Add buildship auto sync preference when build configuration update is set to `automatic`. See [#2224](https://github.com/eclipse/eclipse.jdt.ls/pull/2224).
 * bug fix - Show the field suggestions for the `toString()`, `hashCode()` and `equals()` generator dialogs in definition order. See [#2212](https://github.com/eclipse/eclipse.jdt.ls/pull/2212).
 * bug fix - Fix Gradle project synchorization errors when init script path contains spaces. See [#2245](https://github.com/eclipse/eclipse.jdt.ls/pull/2245), [#2222](https://github.com/eclipse/eclipse.jdt.ls/issues/2222), [#2249](https://github.com/eclipse/eclipse.jdt.ls/pull/2249).
 * bug fix - Fix NPE in the protobuf init script. See [#2246](https://github.com/eclipse/eclipse.jdt.ls/pull/2246).
 * bug fix - Fix type completion when type name conflicts. See [#2232](https://github.com/eclipse/eclipse.jdt.ls/pull/2232).
 * bug fix - Set up `-data` directory for the JDT-LS startup script. See [#2191](https://github.com/eclipse/eclipse.jdt.ls/issues/2191).
 * bug fix - Remove the deprecated `-noverify` option from JDT-LS startup script. See [#2221](https://github.com/eclipse/eclipse.jdt.ls/issues/2221).
 * bug fix - Fix gradle project classpath calculation. See [#2236](https://github.com/eclipse/eclipse.jdt.ls/pull/2236).
 * bug fix - Bad ".git" pattern in `.project` file's `filteredResources` element causes chaos. See [#2244](https://github.com/eclipse/eclipse.jdt.ls/issues/2244).
 * build - Make use of GitHub functions for library update and security analysis. See [#2239](https://github.com/eclipse/eclipse.jdt.ls/pull/2239).
 * build - Increase maximum heap size used by builds during testing. See [#2226](https://github.com/eclipse/eclipse.jdt.ls/issues/2226).
 * build - Update target platform to Eclipse 4.25 Release. See [#2225](https://github.com/eclipse/eclipse.jdt.ls/pull/2225).
 * build - Update Gradle wrapper for failing tests. See [#2215](https://github.com/eclipse/eclipse.jdt.ls/issues/2215).
 * build - Add tests for signature help on record class. See [#2206](https://github.com/eclipse/eclipse.jdt.ls/pull/2206).
 * debt - Update Maven plugin versions. See [#2238](https://github.com/eclipse/eclipse.jdt.ls/pull/2238), [#2242](https://github.com/eclipse/eclipse.jdt.ls/pull/2242), [#2241](https://github.com/eclipse/eclipse.jdt.ls/pull/2241), [#2240](https://github.com/eclipse/eclipse.jdt.ls/pull/2240).

# [1.15.0 (August 31st, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/105?closed=1)
 * enhancement - Search more folders to infer source roots for invisible projects. See [#2176](https://github.com/eclipse/eclipse.jdt.ls/pull/2176).
 * enhancement - Support for Android projects. See [#923](https://github.com/eclipse/eclipse.jdt.ls/issues/923).
 * enhancement - Automatically add Protobuf output source directories to classpath & generate tasks, if necessary. See [#2189](https://github.com/eclipse/eclipse.jdt.ls/pull/2189) & [#2195](https://github.com/eclipse/eclipse.jdt.ls/pull/2195).
 * enhancement - Support "Sort Members" code action. See [#2169](https://github.com/eclipse/eclipse.jdt.ls/pull/2169).
 * enhancement - Add support for Maven offline mode (`java.import.maven.offline.enabled`). See [#2187](https://github.com/eclipse/eclipse.jdt.ls/pull/2187).
 * enhancement - Support the `$/progress` notification method. See [#2030](https://github.com/eclipse/eclipse.jdt.ls/pull/2030).
 * enhancement - Always interpret the full workspace symbol query as a package name. See [#2174](https://github.com/eclipse/eclipse.jdt.ls/pull/2174).
 * enhancement - Add unmanaged folder nature ID. See [#2182](https://github.com/eclipse/eclipse.jdt.ls/pull/2182).
 * bug fix - Set default severity of "Circular classpath" to `warning`. See [#2170](https://github.com/eclipse/eclipse.jdt.ls/pull/2170).
 * bug fix - Fix inlay hints for `record` classes. See [#2181](https://github.com/eclipse/eclipse.jdt.ls/pull/2181).
 * bug fix - Infer the source root only when necessary. See [#2178](https://github.com/eclipse/eclipse.jdt.ls/pull/2178).
 * bug fix - Permit non-JDT errors to be reported in Java files. See [#2154](https://github.com/eclipse/eclipse.jdt.ls/issues/2154).
 * bug fix - Avoid naming conflicts between Gradle project modules. See [#2190](https://github.com/eclipse/eclipse.jdt.ls/pull/2190).
 * bug fix - Re-fetch the extension registry when delegate command lookup fails. See [#2184](https://github.com/eclipse/eclipse.jdt.ls/pull/2184).
 * debt - Remove deprecated Gradle attributes. See [#2198](https://github.com/eclipse/eclipse.jdt.ls/pull/2198).

# [1.14.0 (July 21st, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/104?closed=1)
 * enhancement - Refresh the unmanaged project's classpath on demand. See [#2160](https://github.com/eclipse/eclipse.jdt.ls/pull/2160).
 * enhancement - Provide reload project diagnostics on demand. See [#2164](https://github.com/eclipse/eclipse.jdt.ls/pull/2164).
 * bug fix - Missing completions for fully qualified constructor names. See [#2147](https://github.com/eclipse/eclipse.jdt.ls/issues/2147).
 * bug fix - Completion replacement for a type proposal is incorrect in some cases. See [#2146](https://github.com/eclipse/eclipse.jdt.ls/pull/2146).
 * bug fix - Correct typo in gradle checksum mismatch error message. See [#2161](https://github.com/eclipse/eclipse.jdt.ls/pull/2161).
 * build - Compile error in `MavenBuildSupport.update(IProject, boolean, IProgressMonitor)`. See [#2150](https://github.com/eclipse/eclipse.jdt.ls/issues/2150).
 * build - React to removal of o.e.m2e.lifecyclemapping.defaults in M2E. See [#2149](https://github.com/eclipse/eclipse.jdt.ls/pull/2149).
 * build - Automatically include the required plug-ins in the launch files. See [#2162](https://github.com/eclipse/eclipse.jdt.ls/pull/2162).
 * build - Update target platform to I20220713-1800. See [#2157](https://github.com/eclipse/eclipse.jdt.ls/issues/2157).

# [1.13.0 (June 30th, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/103?closed=1)
 * enhancement - Show `Add javadoc for ..` in quick assists. See [#2133](https://github.com/eclipse/eclipse.jdt.ls/pull/2133).
 * enhancement - Show `Change modifiers to final` in quick assists. See [#2134](https://github.com/eclipse/eclipse.jdt.ls/pull/2134).
 * enhancement - Allow to reload multiple projects at the same time. See [#2131](https://github.com/eclipse/eclipse.jdt.ls/pull/2131).
 * enhancement - Auto-select field when generating constructors. See [#2125](https://github.com/eclipse/eclipse.jdt.ls/pull/2125).
 * enhancement - Allow to build selected projects. See [#2138](https://github.com/eclipse/eclipse.jdt.ls/pull/2138).
 * enhancement - Support multiple selections for generate accessors. See [#2136](https://github.com/eclipse/eclipse.jdt.ls/pull/2136).
 * bug fix - Add logback tracing to JDT-LS. See [#2108](https://github.com/eclipse/eclipse.jdt.ls/issues/2108).
 * bug fix - Fix NPE when triggering signature help in class file. See [#2102](https://github.com/eclipse/eclipse.jdt.ls/issues/2102).
 * bug fix - Support for renaming record attributes. See [#2078](https://github.com/eclipse/eclipse.jdt.ls/pull/2078).
 * bug fix - Change the order for the configuration updating options. See [#2135](https://github.com/eclipse/eclipse.jdt.ls/pull/2135).
 * build - Move to Java 17. See [#2117](https://github.com/eclipse/eclipse.jdt.ls/issues/2117).
 * build - Update target platform to 4.25-I-builds. See [#2127](https://github.com/eclipse/eclipse.jdt.ls/issues/2127).
 * build - React to API changes in M2E. See [#2144](https://github.com/eclipse/eclipse.jdt.ls/pull/2144).
 * debt - JDT-LS doesn't require o.e.m2e.archetype.common anymore. See [#2119](https://github.com/eclipse/eclipse.jdt.ls/issues/2119).
 * other - Adjust the order of code actions. See [#2109](https://github.com/eclipse/eclipse.jdt.ls/pull/2109).
 * other - Update launch script, Java 17 is required now. See [#2141](https://github.com/eclipse/eclipse.jdt.ls/pull/2141).
 * other - Fix random failures for the test CompletionHandlerTest/WorkspaceDiagnosticsHandlerTest. See [#2121](https://github.com/eclipse/eclipse.jdt.ls/issues/2121).
 * other - Fix random failures for the test InlayHintHandlerTest.testVarargs2. See [#2104](https://github.com/eclipse/eclipse.jdt.ls/issues/2104).

# [1.12.0 (June 1st, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/102?closed=1)
 * enhancment - Support separate "Generate Getters" and "Generate Setters". See [#2086](https://github.com/eclipse/eclipse.jdt.ls/pull/2086).
 * enhancement - Show quick fixes for generating accessors in field declarations. See [#2092](https://github.com/eclipse/eclipse.jdt.ls/pull/2092).
 * enhancement - Add support for workspace symbols with qualified names. See [#2084](https://github.com/eclipse/eclipse.jdt.ls/issues/2084).
 * enhancement - Show field type when generating accessors. See [#2093](https://github.com/eclipse/eclipse.jdt.ls/pull/2093).
 * enhancement - Support Gradle invalid type code error check. See [#2082](https://github.com/eclipse/eclipse.jdt.ls/pull/2082).
 * enhancement - Support the exclusion list for inlay hints. See [#2098](https://github.com/eclipse/eclipse.jdt.ls/pull/2098).
 * bug fix - Add support to open decompiled symbols through the symbols list. See [#2087](https://github.com/eclipse/eclipse.jdt.ls/issues/2087).
 * bug fix - Handle illegal URL settings to avoid breaking language server. See [#2089](https://github.com/eclipse/eclipse.jdt.ls/pull/2089).
 * bug fix - Fix `java.completion.importOrder`. See [#2107](https://github.com/eclipse/eclipse.jdt.ls/pull/2107).
 * build - Update M2E to 2.0.0 snapshot release. See [#2085](https://github.com/eclipse/eclipse.jdt.ls/issues/2085).
 * build - Update to buildship 3.1.6 release. See [#2094](https://github.com/eclipse/eclipse.jdt.ls/issues/2094).

# [1.11.0 (May 5th, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/102?closed=1)
 * enhancement - Trigger signature help on completion item selected. See [#2065](https://github.com/eclipse/eclipse.jdt.ls/pull/2065).
 * enhancement - Support completion insert/replace capability. See [#2057](https://github.com/eclipse/eclipse.jdt.ls/pull/2057).
 * enhancement - Add a new preference to disable/enable signature description. See [#2051](https://github.com/eclipse/eclipse.jdt.ls/pull/2051).
 * bug fix - Improve the signature help feature by handling some special cases. See [#2025](https://github.com/eclipse/eclipse.jdt.ls/issues/2025).
 * bug fix - Do not show signature help at the end of an invocation. See [#2079](https://github.com/eclipse/eclipse.jdt.ls/pull/2079).
 * bug fix - Show error status when the project is not created. See [#2058](https://github.com/eclipse/eclipse.jdt.ls/issues/2058).
 * bug fix - Error during importing an Eclipse project whose sources are at root. See [#2072](https://github.com/eclipse/eclipse.jdt.ls/pull/2072).
 * bug fix - Fix NPE in isCompletionInsertReplaceSupport check. See [#2070](https://github.com/eclipse/eclipse.jdt.ls/pull/2070).
 * bug fix - Unexpected 'Project xxx has no explicit encoding set' warnings. See [#2061](https://github.com/eclipse/eclipse.jdt.ls/pull/2061).
 * bug fix - Fix issue where JDT-LS's logback configuration was being ignored. See [#2077](https://github.com/eclipse/eclipse.jdt.ls/pull/2077).
 * build - Enable lombok agent. See [#2068](https://github.com/eclipse/eclipse.jdt.ls/issues/2068).
 * build - Add the default FILECOMMENT template to the preference initialization. See [#2056](https://github.com/eclipse/eclipse.jdt.ls/pull/2056).
 * other - Improve the `jdtls.py` script. See [#2048](https://github.com/eclipse/eclipse.jdt.ls/pull/2048), [#2060](https://github.com/eclipse/eclipse.jdt.ls/issues/2060).

# [1.10.0 (April 13th, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/100?closed=1)
 * performance - Adopt new CompletionProposal API to ignore types before creating certain proposals. See [#2034](https://github.com/eclipse/eclipse.jdt.ls/pull/2034).
 * enhancement - Provide Java 18 support. See [#2026](https://github.com/eclipse/eclipse.jdt.ls/issues/2026).
 * enhancement - Support inlay hints for parameter names. See [#2019](https://github.com/eclipse/eclipse.jdt.ls/pull/2019).
 * enhancement - Add code action to extract lambda body to method. See [#2027](https://github.com/eclipse/eclipse.jdt.ls/issues/2027).
 * enhancement - Adds support for `workspaceSymbol/resolve`. See [#2008](https://github.com/eclipse/eclipse.jdt.ls/pull/2008).
 * bug fix - Provide file & type comments for newly created compilation units. See [#2047](https://github.com/eclipse/eclipse.jdt.ls/pull/2047).
 * bug fix - Postpone the capability registration until the service is ready. See [#1979](https://github.com/eclipse/eclipse.jdt.ls/pull/1979).
 * bug fix - Fix an occurrence of duplicate quick fixes at the line level. See [#2023](https://github.com/eclipse/eclipse.jdt.ls/pull/2023).
 * bug fix - Cannot refactor in static block. See [#2049](https://github.com/eclipse/eclipse.jdt.ls/pull/2049).
 * other - Stabilize some intermitently failing tests. See [#2035](https://github.com/eclipse/eclipse.jdt.ls/issues/2035).
 * other - Advertise "friendly" Python startup wrapper a bit more prominently. See [#2020](https://github.com/eclipse/eclipse.jdt.ls/pull/2020).
 * other - Add documentation for PDE project support status. See [#1953](https://github.com/eclipse/eclipse.jdt.ls/issues/1953).

# [1.9.0 (March 3rd, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/99?closed=1)
 * enhancement - Trigger completion after `new` keyword. See [#2010](https://github.com/eclipse/eclipse.jdt.ls/pull/2010).
 * enhancement - Improve occurrences highlighting. See [#1941](https://github.com/eclipse/eclipse.jdt.ls/pull/1941).
 * enhancement - Provide more common aliases for code snippets. See [#2006](https://github.com/eclipse/eclipse.jdt.ls/pull/2006).
 * bug fix - "Add serial version ID" should not generate empty comments. See [#1899](https://github.com/eclipse/eclipse.jdt.ls/issues/1899).
 * bug fix - convert to static import incorrectly removes import statements. See [#1203](https://github.com/eclipse/eclipse.jdt.ls/issues/1203).
 * bug fix - Type mismatch: cannot convert from `Object` to `Map<String,IndexType>`. See [#1971](https://github.com/eclipse/eclipse.jdt.ls/issues/1971).
 * bug fix - Signature help occasionally fails on constructors and qualified method invocations. See [#2014](https://github.com/eclipse/eclipse.jdt.ls/pull/2014).
 * build - Update m2e-apt to 1.5.4 (new repo). See [#1994](https://github.com/eclipse/eclipse.jdt.ls/pull/1994).
 * other - Provide a platform independent script to help launch language server. See [#1823](https://github.com/eclipse/eclipse.jdt.ls/issues/1823).

# [1.8.0 (January 24th, 2022)](https://github.com/eclipse/eclipse.jdt.ls/milestone/98?closed=1)
 * enhancement - Support completion for lambda expressions. See [#1985](https://github.com/eclipse/eclipse.jdt.ls/issues/1985).
 * enhancement - Add "Convert to Switch Expression" code assist proposal. See [#1935](https://github.com/eclipse/eclipse.jdt.ls/pull/1935).
 * enhancement - Check Gradle compatibility when importing fails. See [#1965](https://github.com/eclipse/eclipse.jdt.ls/pull/1965).
 * enhancement - Show the error status when imported projects having errors. See [#1962](https://github.com/eclipse/eclipse.jdt.ls/pull/1962).
 * bug fix - "Go to References" result contains inaccurate references. See [#1984](https://github.com/eclipse/eclipse.jdt.ls/pull/1984).
 * bug fix - Fix regression in signature help. See [#1980](https://github.com/eclipse/eclipse.jdt.ls/issues/1980).
 * bug fix - Ensure gradle wrappers are correctly processed on project import. See [#1989](https://github.com/eclipse/eclipse.jdt.ls/pull/1989).
 * bug fix - Avoid duplicate quick fixes when showing all quick fixes on a line. See [#1982](https://github.com/eclipse/eclipse.jdt.ls/pull/1982).
 * debt - Update Tycho to 2.5.0. See [#1977](https://github.com/eclipse/eclipse.jdt.ls/pull/1977).

# [1.7.0 (December 16th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/97?closed=1)
 * enhancement - Make the debounce adaptive for validation job. See [#1973](https://github.com/eclipse/eclipse.jdt.ls/pull/1973).
 * bug fix - Fix regression in code action for unresolved type. See [#1967](https://github.com/eclipse/eclipse.jdt.ls/pull/1967).
 * bug fix - Diagnostics from changes to build configuration not reflected in opened source files. See [#1963](https://github.com/eclipse/eclipse.jdt.ls/issues/1963).
 * bug fix - Fix the wrong additional text edit when completing an import statement. See [#1944](https://github.com/eclipse/eclipse.jdt.ls/pull/1944).
 * build - Update Target Platform to 4.22 (2021-12) release. See [#1959](https://github.com/eclipse/eclipse.jdt.ls/pull/1959).
 * debt - Remove unused log4j 1.2.15 from builds. See [#1972](https://github.com/eclipse/eclipse.jdt.ls/pull/1972).
 * debt - DocumentLifeCycleHandlerTest.testFixInDependencyScenario fails randomly. See [#1968](https://github.com/eclipse/eclipse.jdt.ls/issues/1968).
 * other - Boost loading performance of eclipse.jdt.ls project itself. See [#1955](https://github.com/eclipse/eclipse.jdt.ls/pull/1955).

# [1.6.0 (November 26th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/96?closed=1)
 * enhancement - Stop generating metadata files at project's root. See [#1900](https://github.com/eclipse/eclipse.jdt.ls/pull/1900).
 * enhancement - Quickfixes should be available at the line level. See [#1908](https://github.com/eclipse/eclipse.jdt.ls/pull/1908).
 * enhancement - Add `Generate Constructors` to Show Fixes for type declaration. See [#1937](https://github.com/eclipse/eclipse.jdt.ls/pull/1937).
 * enhancement - Add `Override/Implement methods` to Show Fixes for type declaration. See [#1932](https://github.com/eclipse/eclipse.jdt.ls/pull/1932).
 * enhancement - Formatter should indent `case` statements within a `switch` statement by default. See [#1927](https://github.com/eclipse/eclipse.jdt.ls/pull/1927).
 * enhancement - Formatter should not join wrapped lines by default. See [#1925](https://github.com/eclipse/eclipse.jdt.ls/pull/1925).
 * enhancement - Add "Surround With Try-With" code assist proposal. See [#1911](https://github.com/eclipse/eclipse.jdt.ls/pull/1911).
 * enhancement - Always show `Organize imports` in Quick Fixes for import declaration. See [#1936](https://github.com/eclipse/eclipse.jdt.ls/pull/1936).
 * bug fix - Java server refreshing the workspace (cleaning/building) for each restart. See [#1948](https://github.com/eclipse/eclipse.jdt.ls/pull/1948).
 * bug fix - Duplicate implement method quick fixes. See [#1942](https://github.com/eclipse/eclipse.jdt.ls/issues/1942).
 * bug fix - Malformed semantic tokens in some cases. See [#1922](https://github.com/eclipse/eclipse.jdt.ls/issues/1922).
 * bug fix - Several errors reported for anonymous Object classes. See [#1915](https://github.com/eclipse/eclipse.jdt.ls/issues/1915).
 * bug fix - `if` with `instanceof` pattern match and `&&` breaks completion in nested `if`. See [#1855](https://github.com/eclipse/eclipse.jdt.ls/issues/1855).
 * bug fix - Race condition between AutoBuildJob and the publish diagnostics job. See [#1920](https://github.com/eclipse/eclipse.jdt.ls/issues/1920).
 * debt - Clean up duplicate classes migrated into jdt.core.manipulation. See [#1923](https://github.com/eclipse/eclipse.jdt.ls/pull/1923).
 * debt - Don't use deprecated rangeLength property in handleChanged. See [#1928](https://github.com/eclipse/eclipse.jdt.ls/pull/1928).

# [1.5.0 (October 19th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/95?closed=1)
 * performance - completion: optimize the index engine for the scenario "complete on type name". See [#1846](https://github.com/eclipse/eclipse.jdt.ls/issues/1846).
 * enhancement - Support Java 17. See [#1845](https://github.com/eclipse/eclipse.jdt.ls/issues/1845).
 * enhancement - Add semantic tokens for records and constructors. See [#1897](https://github.com/eclipse/eclipse.jdt.ls/pull/1897).
 * enhancement - Add toString() to Show Fixes for type declaration. See [#1903](https://github.com/eclipse/eclipse.jdt.ls/pull/1903).
 * enhancement - Add a `codeAction` to generate the serialVersionUID field. See [#1892](https://github.com/eclipse/eclipse.jdt.ls/issues/1892).
 * enhancement - Add Getter and Setter to Show Fixes for type declaration. See [#1883](https://github.com/eclipse/eclipse.jdt.ls/pull/1883).
 * bug fix - Add space to anonymous type proposal. See [#1898](https://github.com/eclipse/eclipse.jdt.ls/pull/1898).
 * bug fix - "Project Configuration Update" is broken due to JDTUtils.isExcludedFile() not working. See [#1909](https://github.com/eclipse/eclipse.jdt.ls/issues/1909).
 * bug fix - NPE on ::new method refs (Cannot invoke "org.eclipse.jdt.core.dom.IMethodBinding.isSynthetic()" because "functionalMethod" is null). See [#1885](https://github.com/eclipse/eclipse.jdt.ls/issues/1885).
 * bug fix - Go to definition doesn't compute/find results on methods inside an anonymous class. See [#1813](https://github.com/eclipse/eclipse.jdt.ls/issues/1813).
 * bug fix - Packages are not filtered from completion despite the java.completion.filteredTypes configuration. See [#1904](https://github.com/eclipse/eclipse.jdt.ls/issues/1904).
 * bug fix - Exclude `jdk.*`, `org.graalvm.*` and `io.micrometer.shaded.*` from completion. See [#1905](https://github.com/eclipse/eclipse.jdt.ls/pull/1905).
 * bug fix - Assign all to fields generates wrong field names in some corner cases. See [#1031](https://github.com/eclipse/eclipse.jdt.ls/issues/1031).
 * bug fix - Report the diagnostic range correctly on save. See [#1886](https://github.com/eclipse/eclipse.jdt.ls/pull/1886).
 * bug fix - Fix adding preview features to a visible project. See [#1863](https://github.com/eclipse/eclipse.jdt.ls/pull/1863).
 * build - Add ability to skip Gradle checksum packaging. See [#1906](https://github.com/eclipse/eclipse.jdt.ls/pull/1906).
 * debt - Bump JUnit dependencies to 4.13 in some test projects. See [#1894](https://github.com/eclipse/eclipse.jdt.ls/pull/1894).
 * debt - Update org.jsoup 1.9.2 to 1.14.2. See [#1884](https://github.com/eclipse/eclipse.jdt.ls/pull/1884).
 * debt - Adjust m2e repository due to release of 1.18.2. See [#1912](https://github.com/eclipse/eclipse.jdt.ls/pull/1912).
 * other - Promote end-user usage instruction more in the README. See [#1887](https://github.com/eclipse/eclipse.jdt.ls/pull/1887).

# [1.4.0 (September 16th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/94?closed=1)
 * performance - completion: optimize the performance of SnippetCompletionProposal. See [#1838](https://github.com/eclipse/eclipse.jdt.ls/issues/1838).
 * performance - completion: listing constructors is slow. See [#1836](https://github.com/eclipse/eclipse.jdt.ls/issues/1836).
 * enhancement - Support Kotlin gradle files. See [#449](https://github.com/eclipse/eclipse.jdt.ls/issues/449).
 * enhancement - Add functionality to exclude files that will not be tracked for changes. See [#1847](https://github.com/eclipse/eclipse.jdt.ls/issues/1847).
 * enhancement - Add 'hashCode()' and 'equals()' to Show Fixes for type declaration. See [#1842](https://github.com/eclipse/eclipse.jdt.ls/pull/1842).
 * bug fix - Java LS sometimes hangs while loading a gradle project. See [#1874](https://github.com/eclipse/eclipse.jdt.ls/pull/1874).
 * bug fix - Fix regression in gradle startup performance. See [#1853](https://github.com/eclipse/eclipse.jdt.ls/pull/1853).
 * bug fix - Cannot exclude maven sub-module. See [#1850](https://github.com/eclipse/eclipse.jdt.ls/pull/1850).
 * bug fix - 'Open Call Hierarchy' does not jump to reference where it is invoked at. See [#1824](https://github.com/eclipse/eclipse.jdt.ls/pull/1824).
 * bug fix - Generate getters source action is broken from within a record. See [#1392](https://github.com/eclipse/eclipse.jdt.ls/issues/1392).
 * bug fix - Get correct Java project in multi-module case. See [#1865](https://github.com/eclipse/eclipse.jdt.ls/pull/1865).
 * bug fix - Add support for month, shortmonth, day, hour, and minute variables in typeComment. See [#1839](https://github.com/eclipse/eclipse.jdt.ls/pull/1839) & [#1851](https://github.com/eclipse/eclipse.jdt.ls/pull/1851).
 * bug fix - Cannot cast TypeBinding$LocalTypeBinding to IVariableBinding exception on `completionItem/resolve` . See [#1856](https://github.com/eclipse/eclipse.jdt.ls/issues/1856).
 * debt - Update target platform to 2021-09 (4.21) Release. See [#1880](https://github.com/eclipse/eclipse.jdt.ls/pull/1880).
 * debt - JDT LS build fails due to stale target platform. See [#1861](https://github.com/eclipse/eclipse.jdt.ls/issues/1861).
 * debt - Downgrade test Gradle version to avoid filesystem watching feature. See [#1869](https://github.com/eclipse/eclipse.jdt.ls/issues/1869).
 * debt - Update Maven wrapper to 3.8.2. See [#1843](https://github.com/eclipse/eclipse.jdt.ls/pull/1843).
 * debt - Support 3.16 semantic tokens. See [#1678](https://github.com/eclipse/eclipse.jdt.ls/issues/1678).
 * other - Support import from configuration files. See [#1840](https://github.com/eclipse/eclipse.jdt.ls/pull/1840).

# [1.3.0 (August 17th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/93?closed=1)
 * enhancement - 'Create method' code action for method reference. See [#1464](https://github.com/eclipse/eclipse.jdt.ls/issues/1464).
 * performance - Avoid displaying (expensive) constant values in completion items. See [#1835](https://github.com/eclipse/eclipse.jdt.ls/issues/1835).
 * performance - toURI is expensive on Windows for completions. See [#1831](https://github.com/eclipse/eclipse.jdt.ls/issues/1831).
 * bug fix - Go to definition doesn't compute/find results on methods inside an anonymous class. See [#1813](https://github.com/eclipse/eclipse.jdt.ls/issues/1813).
 * bug fix - quickfix not available where cursor lands by default on annotations. See [#1812](https://github.com/eclipse/eclipse.jdt.ls/pull/1812).
 * bug fix - Fix content assist for multiline strings. See [#1819](https://github.com/eclipse/eclipse.jdt.ls/pull/1819).
 * bug fix - Language server freezes when importing maven project. See [#1816](https://github.com/eclipse/eclipse.jdt.ls/pull/1816).
 * bug fix - Suggest correct import quick fix in anonymous classes. See [#1822](https://github.com/eclipse/eclipse.jdt.ls/pull/1822).
 * bug fix - Organize imports generates duplicate static import statement. See [#1814](https://github.com/eclipse/eclipse.jdt.ls/pull/1814).
 * build - Update eclipse-jarsigner-plugin to 1.3.2. See [#1829](https://github.com/eclipse/eclipse.jdt.ls/pull/1829).
 * build - Language server distro contains 2 Guava jars. See [#1706](https://github.com/eclipse/eclipse.jdt.ls/issues/1706).

# [1.2.0 (June 30th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/92?closed=1)
 * enhancement - Add a preference to control the severity of not covered maven plugin execution. See [#1770](https://github.com/eclipse/eclipse.jdt.ls/pull/1770).
 * enhancement - Allow folding `static` blocks. See [#1777](https://github.com/eclipse/eclipse.jdt.ls/issues/1777).
 * enhancement - Add deprecated property to CompletionItem and SymbolInformation. See [#695](https://github.com/eclipse/eclipse.jdt.ls/issues/695).
 * enhancement - Add option to ignore all proxies. See [#1799](https://github.com/eclipse/eclipse.jdt.ls/pull/1799).
 * bug fix - Cannot make a static reference to the non-static type T. See [#1781](https://github.com/eclipse/eclipse.jdt.ls/issues/1781).
 * bug fix - Standard language server should also be able to exit on shutdown. See [#1808](https://github.com/eclipse/eclipse.jdt.ls/pull/1808).
 * bug fix - Syntax language server should be able to exit on shutdown. See [#1790](https://github.com/eclipse/eclipse.jdt.ls/pull/1790).
 * bug fix - File contents would be strange when renaming java file name (with lombok). See [#1775](https://github.com/eclipse/eclipse.jdt.ls/issues/1775).
 * bug fix - java.project.sourcePaths doesn't refresh diagnostics. See [#1769](https://github.com/eclipse/eclipse.jdt.ls/issues/1769).
 * bug fix - wrong status in 'language/progressReport' notification when processing call hierarchy requests. See [#1722](https://github.com/eclipse/eclipse.jdt.ls/issues/1722).
 * bug fix - Update list of excluded errors with ParameterMismatch. See [#1792](https://github.com/eclipse/eclipse.jdt.ls/pull/1792).
 * bug fix - extract method does not seem to like var nor method references. See [#1780](https://github.com/eclipse/eclipse.jdt.ls/pull/1780).
 * bug fix - Fix backslashes (\\) in java.format.settings.url. See [#1774](https://github.com/eclipse/eclipse.jdt.ls/pull/1774).
 * build - Update target platform to 4.21 build I20210621-1800. See [#1804](https://github.com/eclipse/eclipse.jdt.ls/pull/1804).
 * other - Run test without setting lombok agent. See [#1782](https://github.com/eclipse/eclipse.jdt.ls/issues/1782).

# [1.1.2 (May 19th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/91?closed=1)
 * bug fix - Package name not recognized when opening standalone java files. See [#1764](https://github.com/eclipse/eclipse.jdt.ls/issues/1764).
 * bug fix - Fix parameter compatibility issue in buildWorkspace request. See [#1763](https://github.com/eclipse/eclipse.jdt.ls/pull/1763).
 * bug fix - Invalid formatter profile name setting causes errors. See [#1761](https://github.com/eclipse/eclipse.jdt.ls/issues/1761).
 * bug fix - Formatter doesn't load format config after update. See [#1757](https://github.com/eclipse/eclipse.jdt.ls/pull/1757).
 * bug fix - Improve handling of exported settings files. See [#1768](https://github.com/eclipse/eclipse.jdt.ls/pull/1768).
 * bug fix - Fix class literal keyword semantic token. See [#1753](https://github.com/eclipse/eclipse.jdt.ls/pull/1753).
 * debt - InvisibleProjectImporterTest.automaticJarDetectionLibUnderSource fails randomly. See [#1756](https://github.com/eclipse/eclipse.jdt.ls/issues/1756).
 * debt - Build failure on arch with openjdk 11.0.11. See [#1748](https://github.com/eclipse/eclipse.jdt.ls/issues/1748).
 * debt - Cannot launch JDT.LS using the 'jdt.ls.remote.server.launch' file. See [#1754](https://github.com/eclipse/eclipse.jdt.ls/issues/1754).
 * debt - Add a timeout to waitForInitializeJobs(). See [#1773](https://github.com/eclipse/eclipse.jdt.ls/pull/1773).

# [1.1.1 (May 3rd, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/91?closed=1)
* bug fix - Formatter doesn't load format config after update. See [#1757](https://github.com/eclipse/eclipse.jdt.ls/pull/1757).

# [1.1.0 (April 29th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/90?closed=1)
 * enhancement - Add Java 16 Support. See [#1733](https://github.com/eclipse/eclipse.jdt.ls/pull/1733).
 * enhancement - java.project.referencedLibraries should resolve paths leading with ~. See [#1735](https://github.com/eclipse/eclipse.jdt.ls/issues/1735).
 * enhancement - Add source method declaration lookups to the workspace symbol search. See [#1688](https://github.com/eclipse/eclipse.jdt.ls/pull/1688).
 * enhancement - Adopt resolveCodeAction capability. See [#1606](https://github.com/eclipse/eclipse.jdt.ls/issues/1606).
 * bug fix - Changes to Formatter profiles don‘t take effect in real time. See [#1736](https://github.com/eclipse/eclipse.jdt.ls/issues/1736).
 * bug fix - NPE in NewCUProposal.createChange(NewCUProposal.java:277). See [#1723](https://github.com/eclipse/eclipse.jdt.ls/issues/1723).
 * bug fix - Javadoc overriding methods not inheriting param descriptions. See [#1732](https://github.com/eclipse/eclipse.jdt.ls/issues/1732).
 * bug fix - workspaceEdit textDocument version is always 0. See [#1695](https://github.com/eclipse/eclipse.jdt.ls/issues/1695).
 * bug fix - java.settings.url does not override default java settings. See [#1741](https://github.com/eclipse/eclipse.jdt.ls/issues/1741).
 * build - Update Maven wrapper to use 3.8.1. See [#1734](https://github.com/eclipse/eclipse.jdt.ls/pull/1734).
 * other - Engineering: Tune the project settings. See [#1737](https://github.com/eclipse/eclipse.jdt.ls/pull/1737).

# [1.0.0 (April 15th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/89?closed=1)
 * enhancement - Support refactoring when renaming or moving files. See [#1445](https://github.com/eclipse/eclipse.jdt.ls/pull/1445).
 * enhancement - Support Type Hierarchy. See [#1656](https://github.com/eclipse/eclipse.jdt.ls/pull/1656).
 * enhancement - Support to specify source paths for unmanaged folder. See [#1658](https://github.com/eclipse/eclipse.jdt.ls/pull/1658).
 * enhancement - Allow to customize the insertion location for the code generated by source actions. See [#1713](https://github.com/eclipse/eclipse.jdt.ls/pull/1713).
 * enhancement - Support String formatting via delegate command. See [#1702](https://github.com/eclipse/eclipse.jdt.ls/pull/1702).
 * enhancement - Add more options to query project settings. See [#1682](https://github.com/eclipse/eclipse.jdt.ls/pull/1682).
 * enhancement - Link .settings directory to 'invisible project'. See [#1579](https://github.com/eclipse/eclipse.jdt.ls/pull/1579).
 * enhancement - Enhanced IBuildSupport usage to support other build tools such as bazel. See [#1694](https://github.com/eclipse/eclipse.jdt.ls/pull/1694).
 * bug fix - External tool file modifications not registered. See [#1650](https://github.com/eclipse/eclipse.jdt.ls/issues/1650).
 * bug fix - Enhance the condition of inline constant. See [#1672](https://github.com/eclipse/eclipse.jdt.ls/pull/1672).
 * bug fix - Update formatter profile options before applying. See [#1675](https://github.com/eclipse/eclipse.jdt.ls/pull/1675).
 * bug fix - v0.70 broke workspace/DidChangeConfiguration. See [#1685](https://github.com/eclipse/eclipse.jdt.ls/issues/1685).
 * bug fix - Correct minor typo in exception message. See [#1687](https://github.com/eclipse/eclipse.jdt.ls/pull/1687).
 * bug fix - Fall back to default value when import threashold is non-positive. See [#1715](https://github.com/eclipse/eclipse.jdt.ls/pull/1715).
 * bug fix - Invisible project forgets source paths on classpath update. See [#1647](https://github.com/eclipse/eclipse.jdt.ls/issues/1647).
 * bug fix - Fix javadoc in 'java.configuration.runtimes' settings. See [#1683](https://github.com/eclipse/eclipse.jdt.ls/pull/1683).
 * build - Update Target Platform to 2021-03 Release. See [#1691](https://github.com/eclipse/eclipse.jdt.ls/pull/1691).
 * build - Update lsp4j to 0.11.0. See [#1700](https://github.com/eclipse/eclipse.jdt.ls/pull/1700).
 * debt - Build Fails Due to Tests. See [#1646](https://github.com/eclipse/eclipse.jdt.ls/issues/1646).
 * debt - Stop generating .gz artifacts during builds. See [#1707](https://github.com/eclipse/eclipse.jdt.ls/issues/1707).
 * debt - Java LS Tests fail randomly. See [#1684](https://github.com/eclipse/eclipse.jdt.ls/issues/1684).
 * other - Set org.eclipse.jdt.core.compiler.problem.missingSerialVersion to ignore by default. See [#1714](https://github.com/eclipse/eclipse.jdt.ls/issues/1714).

# [0.70.0 (March 5th, 2021)](https://github.com/eclipse/eclipse.jdt.ls/milestone/88?closed=1)
 * enhancement - Code actions should return textedits with proper formatting. See [#1157](https://github.com/eclipse/eclipse.jdt.ls/issues/1157).
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

