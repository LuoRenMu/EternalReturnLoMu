# EternalReturnLoMu Context

## Command runtime

- **Core command**: a host command that must always remain available. `help` is the current core command.
- **Command plugin**: a replaceable business Module that owns its command handlers and command-specific NutDraw templates.
- **Plugin registry**: the atomic alias-to-command snapshot read by every bot adapter and the admin command runner.
- **Built-in plugin**: a plugin packaged with the application as the safe default Implementation.
- **External plugin**: a jar in `plugins/` that can replace a built-in plugin with the same id at runtime.
- **Plugin reload**: validate and enable a new Implementation, publish one registry snapshot, then release the previous classloader.

The stable Interface lives in `core`; the reusable image engine lives in `nutdraw`; business Locality lives under `plugins/<feature>`.
