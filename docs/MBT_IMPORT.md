# MBT Import for JDT.LS

MBT (Metals Build Tool) import is an alternative to M2E for importing Maven/Gradle projects in eclipse.jdt.ls. It replaces the traditional Maven-based dependency resolution at startup with a pre-computed JSON descriptor (`mbt.json`), enabling fast import and on-demand module loading.

This support was developed and validated on the [Quarkus](https://github.com/quarkusio/quarkus) repository (1022 Maven modules, 1495 targets with existing sources).

Here is what the Quarkus `mbt.json` looks like (excerpt with 2 targets and 1 external JAR out of 1022 targets and 3729 JARs):

```json
{
  "namespaces": {
    "io.quarkus:quarkus-netty:999-SNAPSHOT": {
      "compilerOptions": ["-source", "17", "-target", "17", "-Xlint:unchecked"],
      "javaHome": "C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot",
      "sources": ["extensions/netty/runtime/src/main/java"],
      "classes": ["extensions/netty/runtime/target/classes"],
      "dependsOn": ["io.quarkus:quarkus-arc:999-SNAPSHOT"],
      "dependencyModules": [
        "io.netty:netty-codec:4.1.132.Final",
        "io.netty:netty-handler:4.1.132.Final",
        "jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0"
      ]
    },
    "io.quarkus:quarkus-core:999-SNAPSHOT": {
      "compilerOptions": ["-source", "17", "-target", "17"],
      "sources": ["core/runtime/src/main/java"],
      "classes": ["core/runtime/target/classes"],
      "dependsOn": [
        "io.quarkus:quarkus-ide-launcher:999-SNAPSHOT",
        "io.quarkus:quarkus-bootstrap-runner:999-SNAPSHOT"
      ],
      "dependencyModules": [
        "jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0",
        "io.smallrye.config:smallrye-config:3.17.2"
      ]
    }
  },
  "dependencyModules": [
    {
      "id": "io.netty:netty-handler:4.1.132.Final",
      "jar": "file:///~/.m2/repository/io/netty/netty-handler/4.1.132.Final/netty-handler-4.1.132.Final.jar",
      "sources": "file:///~/.m2/repository/io/netty/netty-handler/4.1.132.Final/netty-handler-4.1.132.Final-sources.jar"
    }
  ]
}
```

Key fields:
- **`compilerOptions`** — Java compiler flags (`-source`, `-target`, lint options) extracted from the Maven compiler plugin configuration.
- **`javaHome`** — path to the JDK used to compile this module.
- **`sources`** / **`classes`** — source folders and output directories, relative to the workspace root.
- **`dependsOn`** — references to other targets in the same workspace (inter-project dependencies). JDT.LS creates `IProjectEntry` classpath entries for these.
- **`dependencyModules`** — references to external JARs by ID. JDT.LS resolves each ID in the `dependencyModules` array at the root to get the JAR path and optional source attachment.
- **`dependencyModules` (root array)** — flat list of all external JARs with their location in `~/.m2/repository` and optional source JARs for F3/Go to Definition into library code.

---

## 1. The Problem with M2E on Large Repositories

When eclipse.jdt.ls opens a Maven workspace, M2E runs `readMavenProject()` on every `pom.xml`. On a repository like Quarkus with 1000+ modules, this means:

- **Import takes 1-2 hours** — each module requires full Maven dependency resolution (network I/O, reactor SNAPSHOT resolution, transitive dependency tree).
- **JDT build saturates resources** — once imported, 1000+ `IProject` instances compete for memory and CPU. The JDT incremental builder, diagnostic publishing, and encoding reports overwhelm VS Code.
- **UI freezes** — no file can be opened, no completion requested, no navigation used until the build finishes. Progress stalls at "48%" with no visible activity.
- **Every restart pays the full cost** — M2E re-resolves dependencies on each JDT.LS launch, even when nothing has changed.

**Bottom line:** M2E works well for small-to-medium projects, but it makes Quarkus-scale repositories unusable in VS Code.

---

## 2. How MBT Solves This

MBT decouples dependency resolution from IDE startup by pre-computing all classpath information into a single `mbt.json` file:

```
Traditional:   pom.xml  →  M2E  →  Maven resolution  →  classpath
MBT:           pom.xml  →  mbt.json (generated once)  →  classpath (read instantly)
```

The `mbt.json` file contains two sections: **targets** (one per module scope) and **dependency modules** (external JARs). Here is a real excerpt from the Quarkus `mbt.json`:

```json
{
  "namespaces": {
    "io.quarkus:quarkus-core:999-SNAPSHOT": {
      "compilerOptions": ["-source", "17", "-target", "17"],
      "javaHome": "C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot",
      "sources": ["core/runtime/src/main/java"],
      "classes": ["core/runtime/target/classes"],
      "dependsOn": [
        "io.quarkus:quarkus-ide-launcher:999-SNAPSHOT",
        "io.quarkus:quarkus-development-mode-spi:999-SNAPSHOT",
        "io.quarkus:quarkus-value-registry:999-SNAPSHOT",
        "io.quarkus:quarkus-bootstrap-runner:999-SNAPSHOT"
      ],
      "dependencyModules": [
        "jakarta.annotation:jakarta.annotation-api:3.0.0",
        "jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0",
        "io.smallrye.config:smallrye-config:3.17.2",
        "org.jboss.logging:jboss-logging:3.6.3.Final"
      ]
    }
  },
  "dependencyModules": [
    {
      "id": "jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0",
      "jar": "file:///C:/Users/AngeloZerr/.m2/repository/jakarta/enterprise/jakarta.enterprise.cdi-api/4.1.0/jakarta.enterprise.cdi-api-4.1.0.jar",
      "sources": "file:///C:/Users/AngeloZerr/.m2/repository/jakarta/enterprise/jakarta.enterprise.cdi-api/4.1.0/jakarta.enterprise.cdi-api-4.1.0-sources.jar"
    }
  ]
}
```

- **`namespaces`** — each key is a target ID (`groupId:artifactId:version`). Contains source folders, compiler options, `dependsOn` (inter-project references), and `dependencyModules` (external JAR references by ID).
- **`dependencyModules`** — flat list of external JARs with paths into `~/.m2/repository` and optional source attachments.

At import time, JDT.LS reads the JSON and calls `setRawClasspath()` directly — no Maven process, no network, no resolution.

---

## 3. VS Code Configuration

MBT import is enabled via two settings in VS Code's `settings.json`:

- `java.import.mbt.enabled` (boolean) enables the MBT importer
- `java.import.mode` controls the import strategy for all importers (MBT, Maven, Gradle)

Maven and Gradle importers must also be disabled when using MBT, otherwise vscode-java still triggers them even though MBT's importer has a lower order number:

```json
{
  "java.import.mbt.enabled": true,
  "java.import.mode": "ondemand",
  "java.import.maven.enabled": false,
  "java.import.gradle.enabled": false
}
```

Valid values for `java.import.mode`:
- `"full"` (default) — import all modules at startup
- `"ondemand"` — import modules only when files are opened

The `java.import.mode` setting applies to all importers. When Maven or Gradle importers are used with `"ondemand"`, they also support on-demand module loading with convention-based source folder indexing.

**Important:** After changing the import mode, you must **delete the JDT.LS workspace data** so the previous import state does not interfere. In VS Code, the workspace data is stored at:

```
%APPDATA%/Code/User/workspaceStorage/<workspace-id>/redhat.java/jdt_ws
```

You can delete it manually, or use the VS Code command **"Java: Clean Java Language Server Workspace"** which deletes it and restarts JDT.LS.

When MBT import is enabled, it runs **before** M2E (order 10 vs 400) and Gradle (300). If a `mbt.json` file is found, M2E and Gradle importers are blocked — MBT takes over entirely.

**Note:** The `mbt.json` file must be generated before opening the project. If it does not exist, JDT.LS will generate it automatically at first startup — this takes a few minutes (2-5 min on Quarkus) as it requires reading all `pom.xml` files, resolving the dependency tree, and locating JARs in `~/.m2/repository`. Once generated, subsequent startups read the existing `mbt.json` and are near-instant.

---

## 4. Import Modes

### 4.1 Full Mode

All targets are imported at startup.

```json
{
  "java.import.mbt.enabled": true,
  "java.import.mode": "full"
}
```

**How it works:**
1. Parse `mbt.json` — build the target graph.
2. Pass 1 — create all `IProject` instances with Java nature.
3. Pass 2 — configure classpaths in topological order (dependencies first).
4. JDT builds all projects.

**When to use:** Projects with fewer than ~200 modules. All IDE features work (cross-project references, rename, workspace search).

**Limitation on Quarkus:** Creating 1000+ Eclipse projects saturates JDT regardless of how fast the import is. VS Code freezes during the build phase — blank editors, unresponsive UI, diagnostic floods. MBT eliminates the import bottleneck, but JDT was not designed to hold 1000+ projects simultaneously.

### 4.2 On-Demand Mode

Projects are imported only when a file is opened.

```json
{
  "java.import.mbt.enabled": true,
  "java.import.mode": "ondemand"
}
```

**How it works:**
1. At startup — parse `mbt.json`, build a source-folder → target index. **No projects created.** Any projects left over from a previous session are closed so JDT does not re-index them. Startup completes in ~1 second.
2. On `textDocument/didOpen` — look up the file's target in the index, import it and its transitive `dependsOn` chain, configure classpaths.
3. Result — only the projects the developer actually touches are in the workspace.

**Session restart behavior:** Without the close-on-startup step, reopening VS Code would cause JDT to re-index every project created in the previous session — even before any file is opened. This would negate the benefit of on-demand mode. By closing leftover projects at startup, on-demand mode guarantees a fast restart regardless of how many projects were loaded previously.

**Example — opening `NettyProcessor.java`:**

The file is in `extensions/netty/deployment/src/main/java`. The index maps it to `io.quarkus:quarkus-netty-deployment:999-SNAPSHOT`. Its `dependsOn` chain is:

```json
"io.quarkus:quarkus-netty-deployment:999-SNAPSHOT": {
  "sources": ["extensions/netty/deployment/src/main/java"],
  "dependsOn": [
    "io.quarkus:quarkus-netty:999-SNAPSHOT",
    "io.quarkus:quarkus-arc-deployment:999-SNAPSHOT"
  ]
}
```

Each dependency is itself resolved transitively:

```
quarkus-netty-deployment
  ├── quarkus-netty
  │     dependsOn: ["quarkus-arc"]
  └── quarkus-arc-deployment
        dependsOn: ["quarkus-core-deployment", "quarkus-arc", "arc-processor", ...]
              └── quarkus-core-deployment
                    dependsOn: ["quarkus-core", ...]
                          └── quarkus-core
                                dependsOn: ["quarkus-ide-launcher", "quarkus-bootstrap-runner", ...]
```

Result: ~46 projects imported instead of 1000+.

**Typical workspace size on Quarkus:**

| Module opened | Transitive projects imported |
|---|---|
| `quarkus-core` | 12 |
| `quarkus-hibernate-orm` | 39 |
| `quarkus-netty-deployment` | ~46 |
| Worst case (`mongodb-rest-data-panache-deployment`) | 134 |

A developer working on a few modules accumulates 30-80 projects — well below JDT's saturation threshold.

---

## 5. Feature Comparison

| Feature | M2E | MBT Full | MBT On-Demand |
|---|---|---|---|
| Import speed | Minutes to hours | Seconds (JSON read) | ~1s startup |
| Projects in workspace | All | All | Only opened modules |
| Completion | Yes | Yes | Yes |
| Go to Definition | Yes | Yes | Yes |
| Find References (cross-project) | Yes | Yes | Yes (auto-imports reverse deps) |
| Rename (cross-project) | Yes | Yes | Yes (auto-imports reverse deps) |
| Usable with 1000+ modules | Slow but functional | No (UI freeze) | **Yes** |
| Dependency updates | Automatic | Regenerate `mbt.json` | Regenerate `mbt.json` |

---

## 6. Generating `mbt.json`

### 6.1 Generation Process

The `mbt.json` is generated by analyzing all `pom.xml` files in the workspace: reading the Maven model hierarchy, resolving dependency versions through parent POMs and BOMs, and collecting transitive dependencies from JARs in `~/.m2/repository`.

On Quarkus, generation takes **2-5 minutes** depending on the machine and Maven cache state.

### 6.2 Key Implementation Details

Generating accurate classpaths for a complex Maven reactor required solving several dependency resolution challenges:

- **Maven implicit properties** — POMs like `netty-handler` use `${project.groupId}` and `${project.version}` in their dependency declarations. For example, `netty-handler`'s POM declares:
  ```xml
  <dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>netty-resolver</artifactId>
    <version>${project.version}</version>
  </dependency>
  ```
  These implicit properties must be injected before resolving the model hierarchy, because BOMs may also reference them.

- **Nested BOM resolution** — the chain `vertx-core` → `vertx-dependencies` BOM → `netty-bom` must be followed recursively to resolve managed versions. Without this, dependencies like `netty-resolver-dns` are missing from the generated `mbt.json`:
  ```json
  "io.quarkus:quarkus-netty-deployment:999-SNAPSHOT": {
    "dependencyModules": [
      "io.netty:netty-handler:4.1.132.Final",
      "io.netty:netty-resolver:4.1.132.Final",
      "io.netty:netty-resolver-dns:4.1.132.Final"
    ]
  }
  ```

- **Systematic transitive resolution** — transitive dependencies from external JARs are always resolved (by reading their POMs in `~/.m2/repository`), not only when Maven signals missing artifacts.

### 6.3 When to Regenerate

The `mbt.json` must be regenerated when:
- A `pom.xml` or `build.gradle` is modified (dependency added/removed/changed).
- Dependency versions change (BOM update, parent version bump).

JDT.LS detects `pom.xml` modifications via file watchers and flags the `mbt.json` as potentially stale.

### 6.4 Why Generation Must Always Be Full

The `mbt.json` must always be generated for **all** modules, even when the import mode is `ondemand`. The `dependsOn` field requires knowledge of every module in the workspace to distinguish inter-project dependencies (→ `dependsOn`) from external JARs (→ `dependencyModules`).

A per-module incremental generation would lose the `dependsOn` graph, breaking project references — and by extension, cross-project navigation and rename even within imported projects.

---

## 7. Technical Challenges Solved

### 7.1 Topological Sort for Build Ordering

Each `setRawClasspath()` call triggers JDT's incremental builder. If project A's classpath references project B, but B has not been configured yet, JDT emits "prerequisite X is not built" errors. These errors disappear when B is later configured, but they cause diagnostic noise and confuse users.

**Solution:** Projects are configured in topological order (dependencies first). A depth-first traversal of the `dependsOn` graph ensures that every dependency's classpath is set before its dependents.

### 7.2 JPMS Module Visibility

Modular projects (those containing `module-info.java`) need the `MODULE=true` classpath attribute on their project entries. Without it, JDT places dependencies on the classpath instead of the module path, causing `"type X is not accessible"` errors for `requires` directives.

**Example:** `bootstrap-runner` is a modular project:

```json
"io.quarkus:quarkus-bootstrap-runner:999-SNAPSHOT": {
  "sources": ["independent-projects/bootstrap/runner/src/main/java"],
  "dependsOn": ["io.quarkus:quarkus-classloader-commons:999-SNAPSHOT"]
}
```

Its `module-info.java` declares `requires io.quarkus.commons.classloading`. Without the MODULE attribute on the `classloader-commons` project entry, JDT places it on the classpath instead of the module path and fails with `"type ClassLoaderHelper is not accessible"`.

### 7.3 Transitive Project Visibility

Project entries must be marked `isExported=true` so that transitive project dependencies are visible through the dependency chain.

**Example:** In the `mbt.json`, the chain looks like:

```json
"io.quarkus:quarkus-amazon-lambda-common-deployment:999-SNAPSHOT": {
  "dependsOn": ["io.quarkus:quarkus-core-deployment:999-SNAPSHOT", ...]
}
"io.quarkus:quarkus-core-deployment:999-SNAPSHOT": {
  "dependsOn": ["io.quarkus:quarkus-core:999-SNAPSHOT", ...]
}
```

`quarkus-amazon-lambda-common-deployment` needs `LaunchMode` from `quarkus-core`, but it only has a direct `dependsOn` to `quarkus-core-deployment`. Without `isExported=true` on project entries, transitive visibility is broken.

Library entries (JARs) remain `isExported=false` to avoid JPMS duplicate-module conflicts.

### 7.4 Missing Java Builder on Existing `.project` Files

When the project directory already contains a `.project` file from a previous session (M2E, manual, or earlier MBT run), Eclipse reads it as-is during `project.create()`. If the file has the Java nature but no `org.eclipse.jdt.core.javabuilder` in its `buildSpec`, JDT never compiles the project — no `.class` files, no errors, but the project's packages are invisible to dependent projects.

**Symptom:** `import io.quarkus.arc.deployment cannot be resolved` in `NettyProcessor`, but Go to Definition (F12) works on the same types. The import resolution uses the compiled type model (needs `.class` files), while F12 uses the source search index.

**Solution:** After opening a project, `ensureJavaBuilder()` checks the `buildSpec` and adds the Java builder if missing.

### 7.5 Session Restart in On-Demand Mode

Projects created during an on-demand session persist in the JDT workspace. On restart, JDT re-indexes and rebuilds all of them before any file is opened — negating the fast-startup benefit of on-demand mode.

**Solution:** At startup in on-demand mode, `closePreviousSessionProjects()` closes all MBT-created projects. They are re-opened on-demand when files are opened via `didOpen`.

### 7.6 Cross-Project References and Rename in On-Demand Mode

In on-demand mode, only the opened file's transitive `dependsOn` chain is imported. Find References and Rename need the opposite direction — all projects that *depend on* the current project. Without them, a rename of a public API type would miss callers in non-imported modules.

**Solution:** At startup, the `dependsOn` graph is inverted into a reverse dependency index. When Find References or Rename is triggered, `MbtProjectImporter.tryImportReverseDependencies(uri, monitor)` looks up the file's target in the source folder index, collects **direct** (non-transitive) reverse dependencies that have not yet been imported, and imports them on-the-fly. The hook is a single method call at the top of `ReferencesHandler.findReferences()` and `RenameHandler.rename()` — no other code is modified.

Only direct reverse dependencies are imported — a transitive traversal would pull in the entire repository for core modules (e.g., `quarkus-core` has 1000+ transitive reverse deps). Direct dependents are the projects most likely to reference the symbol directly.

**Example:** Renaming a method in `quarkus-arc`. The reverse `dependsOn` index finds that `quarkus-arc-deployment`, `quarkus-hibernate-orm`, and a few other modules directly depend on it. These are imported before the rename search executes.

---

## 8. Known Limitations

| Limitation | Impact |
|---|---|
| **Cross-project references in on-demand mode** | Find References and Rename auto-import reverse dependencies, but the initial request may be slower while projects are loaded |
| **Full mode unusable on large repos** | 1000+ projects overwhelm JDT and VS Code regardless of import speed |
| **No incremental `mbt.json` update** | Any `pom.xml` change requires full regeneration (2-5 min on Quarkus) |
| **Stale-error flicker** | In on-demand mode, errors may briefly appear and disappear as projects are configured (timing between build triggers and classpath setup) |

---

## 9. Future Improvements

### 9.1 ~~Reverse `dependsOn` for Cross-Project Operations~~ (Implemented)

The `mbt.json` `dependsOn` graph is inverted at startup into a reverse dependency index. When Find References or Rename is triggered, JDT.LS automatically imports the direct reverse dependencies of the current file's project — making cross-project operations work in on-demand mode without loading everything upfront. The hook is minimal: one call to `MbtProjectImporter.tryImportReverseDependencies()` at the top of `ReferencesHandler.findReferences()` and `RenameHandler.rename()`.

### 9.2 Background Progressive Import

After the initial on-demand import, remaining projects could be imported progressively in the background without blocking the UI. This would gradually enable full cross-project features while preserving the fast startup.

### 9.3 Incremental `mbt.json` Regeneration

Instead of regenerating the entire `mbt.json` on any `pom.xml` change, only the affected targets could be updated. The `dependsOn` graph would need to be recomputed globally, but dependency resolution could be scoped to the changed modules.

### 9.4 Configurable Module Subsets

Allow users to specify which parts of the repository to import (e.g., `extensions/netty/**`), combining on-demand mode with explicit project selection.

---

## Quarkus — Test Case Statistics

| Metric | Value |
|---|---|
| Maven modules | 1022 |
| Total targets (main + test) | 2044 |
| Targets with existing sources | 1495 |
| Typical on-demand workspace | 30-50 projects |
| Worst-case transitive chain | 134 projects |
| `mbt.json` generation time | 2-5 minutes |
| On-demand startup time | ~1 second |
