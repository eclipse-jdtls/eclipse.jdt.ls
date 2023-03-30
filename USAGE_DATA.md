# Telemetry data collection

If `java.telemetry.enabled` is set to `true`, JDT-LS emits telemetry events.
These events can be collected by the LSP client program.

When telemetry events are enabled, the following information is emitted :

 * The name of the build tool used to import a project (eg. Maven, Gradle, Invisible (project), etc.)
 * The total number of Java projects within the workspace
 * The lowest and highest Java compiler source level used (eg. 11 & 17)
 * Whether the project(s) are being imported for the first time (eg. true)
 * The elapsed time (in milliseconds) at which the language server initialized the workspace project(s), declared as ready for requests, and completed building the project(s)
 * The number of libraries that were indexed after project initialization
 * The total size (in bytes) of libraries that were indexed after project initialization
