# IClassFile → Workspace project: resolution strategies

## Context

In on-demand mode, "Go to Definition" may resolve to a `.class` file (JAR dependency).
If the class exists as source in a workspace module, we import the module on-demand
and redirect navigation to the `.java` file.

This document lists all strategies to locate the workspace source from an `IClassFile`,
sorted by performance. The implementation iterates from fastest to slowest until one succeeds.

## Input

```java
IClassFile classFile;

// From the class file, we can derive:
String packagePath;    // "com/example"           from classFile.getParent().getElementName()
String sourceName;     // "Foo.java"              from classFile.getElementName() (.class → .java, handle $)
String relativePath;   // "com/example/Foo.java"  packagePath + "/" + sourceName

// From the JAR root:
IPackageFragmentRoot root = classFile.getAncestor(PACKAGE_FRAGMENT_ROOT);
IPath jarPath;              // ~/.m2/repository/io/quarkus/quarkus-core/3.0.0/quarkus-core-3.0.0.jar
IPath sourceAttachment;     // may be null, a -sources.jar, or a workspace source directory
```

## Strategies

### 1. Module index (already discovered modules)

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 5/5  | 3/5         | All          |

Checks `knownModules` from `IModuleIndex` (populated by previous `didOpen` calls).
For each known module directory, checks `src/main/java/<relativePath>`.

```
knownModules = [/workspace/core, /workspace/client]
→ check /workspace/core/src/main/java/com/example/Foo.java    → exists? ✓
```

- **Fast:** in-memory set + `Files.isRegularFile()` per module
- **Limitation:** only contains modules discovered via previous `didOpen`; empty on first navigation

---

### 2. Source attachment path

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 5/5  | 2/5         | All          |

Uses `root.getSourceAttachmentPath()` to resolve the source file directly.

```
sourceAttachment = /workspace/core/src/main/java
→ resolve /workspace/core/src/main/java/com/example/Foo.java  → exists? ✓
```

- **Fast:** single path resolution + `Files.isRegularFile()`
- **Limitation:** source attachment is usually `null` or points to a `-sources.jar` (not a workspace directory), especially in on-demand mode where the target project is not yet imported

---

### 3. JAR path convention

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 4/5  | 2/5         | Maven        |

Parses the JAR path to extract Maven coordinates, then matches `artifactId` against workspace directory names.

```
~/.m2/repository/io/quarkus/quarkus-core/3.0.0/quarkus-core-3.0.0.jar
→ artifactId = "quarkus-core"
→ search /workspace/quarkus-core/src/main/java/com/example/Foo.java
```

- **Fast:** string parsing + targeted file check
- **Limitation:** fragile (custom repos, artifactId ≠ directory name, shadow JARs, Gradle cache uses different layout)

---

### 4. m2e classpath entry attributes

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 3/5  | 4/5         | Maven        |

m2e stores Maven coordinates in `IClasspathEntry` extra attributes:
`org.eclipse.m2e.maven.groupId`, `org.eclipse.m2e.maven.artifactId`.
Match against workspace `pom.xml` GAV.

```
entry attributes → groupId=io.quarkus, artifactId=quarkus-core
→ find pom.xml with matching GAV → /workspace/core/pom.xml
→ module = /workspace/core
```

- **Reliable for Maven:** attributes are set by m2e classpath container
- **Limitation:** Maven only; requires parsing workspace `pom.xml` files to match; m2e must be active

---

### 5. JAR META-INF/pom.properties

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 3/5  | 4/5         | Maven        |

Reads `META-INF/maven/<groupId>/<artifactId>/pom.properties` from inside the JAR
to get exact GAV. Matches against workspace `pom.xml` files.

```
jar:quarkus-core-3.0.0.jar!/META-INF/maven/io.quarkus/quarkus-core/pom.properties
→ groupId=io.quarkus, artifactId=quarkus-core
→ find pom.xml with matching GAV → /workspace/core/pom.xml
```

- **Reliable:** `pom.properties` contains exact coordinates as declared by the build
- **Limitation:** requires JAR I/O; Maven-built JARs only; Gradle JARs may not have this metadata

---

### 6. Pre-scanned module directories

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 4/5  | 5/5         | All          |

At initialization, scan the workspace for all directories containing `pom.xml` / `build.gradle` / `build.gradle.kts` (quick filesystem walk, no file parsing).
On lookup, check standard source folders for each module.

```
init scan → modules = [/workspace/core, /workspace/client, /workspace/api, ...]

lookup "com/example/Foo.java":
  /workspace/core/src/main/java/com/example/Foo.java      → miss
  /workspace/client/src/main/java/com/example/Foo.java     → miss
  /workspace/api/src/main/java/com/example/Foo.java        → hit ✓
```

- **Reliable:** checks actual file existence, works for any build system
- **Fast:** one-time init scan + N × `Files.isRegularFile()` (N = number of modules)
- **Limitation:** non-standard source layouts (e.g. `src/` instead of `src/main/java/`); mitigated by checking common alternatives

---

### 7. Recursive workspace scan

| Perf | Reliability | Build system |
|------|-------------|--------------|
| 1/5  | 5/5         | All          |

Walk the entire workspace directory tree, checking standard source folders at every level.

```
walk /workspace/**:
  /workspace/deeply/nested/module/src/main/java/com/example/Foo.java → hit ✓
```

- **Reliable:** always finds the file if it exists
- **Slow:** full directory tree traversal; on Quarkus (~800 modules, thousands of directories) this can take seconds

---

## Recommended cascade (implemented)

```
┌─────────────────────────────────────┐
│ ScannedModulesStrategy              │  N × Files.isRegularFile(), reliable
│ (pre-scanned modules from bg Job)   │
└──────────────┬──────────────────────┘
               │ miss
┌──────────────▼──────────────────────┐
│ SourceAttachmentStrategy            │  single path check, fast
│ (source attachment → workspace dir) │
└──────────────┬──────────────────────┘
               │ miss
┌──────────────▼──────────────────────┐
│ JarPathStrategy                     │  artifactId match, fast
│ (JAR filename → module artifactId)  │
└──────────────┬──────────────────────┘
               │ miss
               ▼
             null (source not in workspace)
```

**ScannedModulesStrategy** is tried first. At startup, a background `Job` scans the
workspace for all `pom.xml` / `build.gradle` directories and collects source folders.
If the scan is already done, this strategy checks each module's source folders for
the relative path — reliable and fast.

**SourceAttachmentStrategy** checks the JAR's source attachment path. Instant when
available, but source attachment is usually `null` or points to a `-sources.jar`.

**JarPathStrategy** extracts the `artifactId` from the JAR filename and matches it
against scanned modules. Fast but fragile (naming conventions).

## Source folder detection

For each scanned module, source folders are detected as follows:

1. **Standard paths first**: check if `src/main/java` or `src/test/java` exist
2. **Maven fallback**: if no standard folder exists and the module has a `pom.xml`,
   parse `<sourceDirectory>` and `<testSourceDirectory>` from pom.xml
3. **Gradle**: standard conventions only (custom source sets in `build.gradle`
   are not parsed — rare in practice)

This keeps the scan lightweight (no full XML/Groovy parsing) while handling
non-standard Maven layouts configured via `<sourceDirectory>`.

## Implementation

- `ModuleScanner`: background scan, runs in a `Job` at init, thread-safe `CopyOnWriteArrayList`
- `ModuleInfo`: lightweight record (`directory`, `artifactId`, `buildType`, `sourceFolders`)
- `IClassFileSourceStrategy`: strategy interface with `findSource()` + `getName()`
- `OnDemandImportManager.tryImportForClassFile()`: cascade entry point, called from `JDTUtils.toLocation()`
- Hook in `JDTUtils.toLocation()`: when `unit == null && cf != null`, try on-demand import
