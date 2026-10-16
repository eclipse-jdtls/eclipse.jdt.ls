# On-Demand Project Import

## Problem: Full Import Mode and Large Codebases

By default, JDT-LS operates in **full import mode**: when a workspace is opened, every project is imported at startup. For small workspaces with a handful of modules, this works well -- import completes in seconds and all features are immediately available. However, many real-world codebases are far larger. On-demand import is designed specifically for these **large, multi-module workspaces** where full import becomes impractical.

For each project, import means:

1. **Classpath resolution** -- M2E resolves Maven dependencies (or Buildship resolves Gradle dependencies), downloading artifacts as needed and computing the full classpath.
2. **Project creation/opening** -- an Eclipse project is created in the workspace with the appropriate natures (Java, Maven, Gradle) and classpath containers.
3. **Building** -- JDT compiles the project, indexes all source and binary files, and reports diagnostics.

Consider [Quarkus](https://github.com/quarkusio/quarkus), which contains **1300+ Maven modules**. In full import mode:

- M2E attempts to resolve all 1300+ modules simultaneously. Classpath resolution alone takes **over 2 hours**.
- JDT indexes every source file and every JAR dependency across all projects, consuming massive amounts of heap.
- Auto-build triggers across all projects, compounding the resource pressure.
- The process systematically ends in **OutOfMemoryError** -- VS Code becomes unresponsive, and opening a Java file in the editor displays a blank white panel instead of code.
- Even when import partially succeeds, only a handful of projects are usable before memory exhaustion kills the language server.

The fundamental issue is that full import treats every project equally, regardless of whether the user intends to work on it.

## On-Demand Import Mode

On-demand import (`"java.import.mode": "ondemand"`) takes a different approach: **import nothing at startup, import each project only when the user opens a file in it.**

### User Perspective

- **Instant startup.** The workspace initializes in ~18 ms instead of minutes. The editor is immediately responsive.
- **Import on file open.** When the user opens a Java file, only the module containing that file is imported. Java features (hover, completion, diagnostics) become available within seconds.
- **Progressive view updates.** The Java Projects view starts empty and adds each project as it is imported, giving visual feedback of what has been loaded.
- **Minimal resource usage.** Only the projects the user actually works on consume CPU and memory. A developer editing two modules out of 1300 pays the cost of two imports, not 1300.
- **No build tool conflict prompt.** In full import mode, when a directory contains both `pom.xml` and `build.gradle`, the user is prompted to choose between Maven and Gradle before import can begin. In on-demand mode, the build tool is determined by the file being opened -- each importer tries its own build file lookup, and the first one that matches handles the import. No upfront choice is needed.

### Technical Implementation

#### Startup

`OnDemandImportManager` is initialized with the importers that support on-demand mode (Maven, Gradle). No project scanning or import happens. Projects left open by a previous session are **closed** (not deleted) to prevent the Eclipse framework from indexing or building them. Closing rather than deleting preserves the JDT index cache on disk so that reopening the project later is fast.

```
InitHandler.triggerInitialization()
  --> OnDemandImportManager.closeStaleProjects()   // close leftover projects
  --> OnDemandImportManager.initialize()            // register importers, no import
  --> Workspace initialized in ~18ms
```

#### Module Index

Each importer maintains a **module index** that maps source files to their containing module:

- **`MavenModuleIndex`** -- fully lazy. When a file is opened, the index walks up the directory tree from the file's location until it finds a `pom.xml`. The module is registered on discovery; subsequent files in the same module hit the cache.

- **`GradleModuleIndex`** -- fully lazy, like Maven. When a file is opened, the index walks up the directory tree until it finds a `build.gradle` or `build.gradle.kts`. Buildship does not expose an API to list subprojects from `settings.gradle` without triggering a full Gradle synchronization, so the walk-up approach is used instead.

Both indexes extend `AbstractModuleIndex`, which provides thread-safe module registration, import tracking (to avoid duplicate imports), and the walk-up algorithm.

#### Import Flow

```
User opens file
  --> textDocument/didOpen
  --> DocumentLifeCycleHandler.resolveCompilationUnit()
      --> JDTUtils.findFile(uri)
          --> resource is null OR resource belongs to a closed project
      --> OnDemandImportManager.tryOnDemandImport(uri)
          --> importer.importOnDemand(uri)
              --> index.findModulePath(uri)            // walk-up to find pom.xml / build.gradle
              --> tryReopenClosedProject()           // fast path: ~450ms, preserves JDT cache
              --> OR importModule()                  // slow path: ~6s, full M2E/Buildship import
          --> notifyProjectsImported()               // update Java Projects view
      --> JDTUtils.findFile(uri)                     // now resolves to the imported project
      --> JDTUtils.resolveCompilationUnit(resource)  // hover, completion, diagnostics work
```

**Reopen vs. full import.** On shutdown, projects are closed rather than deleted. When the user opens a file in the next session, `tryReopenClosedProject()` searches for a closed project at the module's location and reopens it. Reopening preserves the JDT index cache from the previous session, reducing import time from ~6 seconds (full M2E import) to ~450 ms.

#### Closed Project Handling

In on-demand mode, most projects in the workspace are closed. Code that iterates all projects must handle this:

- **`ProjectUtils.getAllProjects()`** filters out closed projects via `IProject.isAccessible()` when in on-demand mode. This prevents callers from crashing when calling methods like `IProject#findMaxProblemSeverity()` on a closed project. The filter is only active when `java.import.mode` is `"ondemand"`, so full mode is unaffected.

- **`WorkspaceDiagnosticsHandler`** skips closed projects via `!project.isAccessible()` when publishing diagnostics. Without this guard, calling `findMarkers()` on a closed project would throw an exception.

- **`DocumentLifeCycleHandler`** checks `resource.getProject().isOpen()` in addition to `resource == null`. `JDTUtils.findFile()` can return an `IFile` belonging to a closed project (Eclipse's `findFilesForLocationURI` includes closed projects in its results); without this check, on-demand import would never be triggered.

#### Shutdown

On shutdown, `OnDemandImportManager.closeProjectsOnShutdown()` closes open projects under the workspace roots (projects outside those roots are left untouched). This ensures the next session starts clean -- no projects are loaded until the user opens a file.

#### Notifications

After a successful on-demand import, `OnDemandImportManager` sends an `EventType.ProjectsImported` notification to the client with the URIs of the imported projects. The `vscode-java` extension forwards this to `vscode-java-dependency`, which adds the projects to the Java Projects tree view.

### Result

Instead of 1300+ open projects consuming resources, the workspace contains only the projects the user has actually opened files in -- typically 1 to 5. Startup is near-instant, and the first file opened in a module pays a one-time import cost of ~450 ms (reopen) or ~6 s (first import).

## Known Limitations (PR 1/4)

This is the first PR in a series of four. Current limitations:

- **Prerequisite: `mvn clean install` on the project root.** In full import mode, all modules are imported into the Eclipse workspace as projects, so M2E resolves inter-project dependencies directly (project A sees project B's source). In on-demand mode, only the modules the user opens are imported. All other modules -- including the ones that project A depends on -- are **not** in the workspace. M2E falls back to the local Maven repository (`~/.m2/repository`) and resolves them as JARs. This works transparently for compilation, hover, completion, and diagnostics, **but only if the JARs exist in the local repository**. Running `mvn clean install` (or `mvn install -DskipTests` for speed) on the project root before opening VS Code ensures that all modules are built and installed as JARs. Without this step, M2E cannot resolve the dependencies and the imported module will have classpath errors.

  ```
  # Example: Quarkus
  cd quarkus/
  mvn install -DskipTests -Dno-format    # ~30 min first time, populates ~/.m2/repository
  code .                                  # open VS Code with java.import.mode: "ondemand"
  ```

- **Runtime classpath fallback.** The runtime classpath resolution (`MavenRuntimeClasspathProvider`) does not handle missing workspace project references as gracefully as the compile classpath. When it fails, `ProjectCommand.getClasspathsFromJavaProject` falls back to the compile classpath, which correctly resolves dependencies as JARs.

- **No cross-project reference search.** "Find References" and "Call Hierarchy" only search within imported projects and their JAR dependencies. Other workspace projects that are not yet imported -- but that may reference the Java file where "Find References" is invoked -- will not be searched.

- **First import is slower than reopen.** The first time a module is imported in a fresh workspace (no previous session), the full M2E/Buildship import runs (~6s). Subsequent sessions benefit from the fast reopen path (~450ms) because the JDT index cache is preserved.

- **Mixed build systems.** On-demand mode supports Maven and Gradle, but mixed workspaces (some modules Maven, some Gradle) are handled by trying each importer in order. MBT (Multi-Build-Tool) support is planned for PR 2/4.

## Roadmap

- **PR 2/4** -- MBT (Multi-Build-Tool) import support.
- **PR 3/4** -- Module index extractors and `dependsOn` to know which projects reference a given project (reverse dependency graph).
- **PR 4/4** -- Cross-project reference search using the `dependsOn` graph to import referencing projects on demand.
