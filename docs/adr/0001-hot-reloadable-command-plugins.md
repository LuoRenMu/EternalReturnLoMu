# ADR 0001: Hot-reloadable command Modules

## Status

Accepted — 2026-08-11

## Context

Command discovery used one reflection-built lazy map, and image rendering used an umbrella Interface containing every command model. A single command or template change therefore required rebuilding and restarting the whole application.

## Decision

`core` is the plugin loader and owns the stable `CommandPlugin` Interface plus an atomic command registry. Business commands are grouped by Locality into `character`, `player`, `tier`, and `news` Modules. Each visual Module owns its NutDraw template. `help` remains a non-disableable core command.

Built-in factories provide a safe default. An external jar in `plugins/` may replace a built-in Module with the same id. The loader reads `META-INF/lomu-command-plugin.properties`, loads plugin Implementation packages child-first from a shadow copy, validates aliases, then atomically publishes the replacement. Shared core, NutDraw, Simbot, Kotlin, and native Skia classes remain parent-loaded.

## Consequences

- Enable and disable operations affect new command calls immediately without restarting adapters.
- Windows deployments can overwrite the original plugin jar because execution uses a versioned shadow copy.
- Failed validation leaves the currently published registry unchanged.
- Command-specific templates no longer deepen the NutDraw engine's public surface.
- In-flight calls may finish using the previous Implementation; plugin lifecycle hooks must not retain child-loaded objects after disable.
