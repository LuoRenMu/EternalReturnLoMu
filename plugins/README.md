# Command plugins

Core applications and shared modules use version `3.0.0`; standalone command plugins use version `1.0.0`.
Both values are maintained centrally in `gradle.properties`.

Build qgbot, onebot, and three upload-ready distribution directories:

```powershell
.\gradlew.bat test stageDistributions
```

Artifacts are written to `build/distributions/qgbot`, `build/distributions/onebot`, and
`build/distributions/plugins`. GitHub Actions runs the same build automatically and uploads each directory separately.

Build hot-reloadable jars:

```powershell
.\gradlew.bat stageCommandPlugins
```

Artifacts are written to `build/command-plugins`. Copy a jar into the runtime `plugins/` directory, then use the admin **命令插件** page to scan/reload it. Each jar must expose its `CommandPlugin` entry class through the manifest `Main-Class` attribute.

Plugin ids currently available: `character`, `player`, `tier`, `news`, and `query-statistics`. The `core` plugin contains `help` and cannot be disabled or replaced.

Classpath plugins are discovered automatically with `ServiceLoader`. A plugin module contributes itself by adding
`src/main/resources/META-INF/services/cn.luorenmu.command.plugin.CommandPlugin`, containing the fully qualified
plugin implementation class name. No central Kotlin registry needs updating. Projects below `plugins:*` are
automatically included by both bot applications and the `stageCommandPlugins` task.

The admin plugin page can upload a jar directly, enable/disable it, reload it, and configure the reply returned when one of its disabled commands is called. Enabled state and disabled replies are persisted in `plugins/plugin-state.properties`.
