# Command plugins

Build hot-reloadable jars:

```powershell
.\gradlew.bat stageCommandPlugins
```

Artifacts are written to `build/command-plugins`. Copy a jar into the runtime `plugins/` directory, then use the admin **命令插件** page to scan/reload it. Each jar must expose its `CommandPlugin` entry class through the manifest `Main-Class` attribute.

Plugin ids currently available: `character`, `player`, `tier`, `news`, and `query-statistics`. The `core` plugin contains `help` and cannot be disabled or replaced.

The admin plugin page can upload a jar directly, enable/disable it, reload it, and configure the reply returned when one of its disabled commands is called. Enabled state and disabled replies are persisted in `plugins/plugin-state.properties`.
