# EternalReturnLoMu Context

## Command runtime

- **Core command**: a host command that must always remain available. `help` is the current core command.
- **Command plugin**: a replaceable business Module that owns its command handlers and command-specific NutDraw templates.
- **Plugin registry**: the atomic alias-to-command snapshot read by every bot adapter and the admin command runner.
- **Built-in plugin**: a plugin packaged with the application as the safe default Implementation.
- **External plugin**: a jar in `plugins/` that can replace a built-in plugin with the same id at runtime.
- **Plugin reload**: validate and enable a new Implementation, publish one registry snapshot, then release the previous classloader.

The stable Interface lives in `core`; the reusable image engine lives in `nutdraw`; business Locality lives under `plugins/<feature>`.

## HTTP transport

- **Business HTTP client**: the shared `RequestManager` explicitly uses the JDK Java engine. Caller cancellation is not a transient failure. See ADR 0003 for the CIO permit-error mitigation and verification limits.
- **SDK HTTP/WebSocket client**: Simbot QQ and OneBot explicitly retain CIO; engine discovery must not couple SDK transport to business dependencies.
- **Transport deployment**: these factories are main-program code, not hot-reloadable command plugin implementations.

## QQ menu and command panel administration

- **Custom menu**: QQ's global C2C bottom menu, not a group Markdown keyboard. Explicitly published snapshots replace the complete online menu; an explicit null clears it.
- **Command panel**: a QQ-hosted set of command/link items for `group`, `c2c`, `channel` or `dm`. Content updates and specific-target association updates are separate operations. Selecting a command fills the input box rather than executing it.
- **QQ admin bridge**: host-owned models/service and authenticated Web routes in core; Simbot API execution in qgbot. No automatic publication or ambiguous write retries; OneBot and disconnected clients report unavailable. See `docs/qq-menu-panel.md`.

## Live notifications

- **Live notification entitlement**: a durable sender ID claimed only in the designated Eternal Return group. Claiming does not require owner role or proactive-message permission; subscribing still requires both in the target group.
- **Live notification subscription**: `bili-live` binds one room per group and one distinct room per claimed sender across groups (the same room may serve multiple groups). The legacy `test_bili_live` table preserves cursors; unbound legacy rows are paused until a claimed owner explicitly re-subscribes.
- **Live end notification**: an observed `1 -> 0/2` transition, with the saved start time used to estimate duration at detection. The status CAS and durable known-session end claim commit before sending; persistent offline/rerun states never produce repeated end messages. Both start and end successes count as live pushes.
- **Proactive push count**: acknowledged QQ sends, not polls or attempts. Shared `qq_push_statistics` stores lifetime BIGINT counts by kind, group and room/player ID, with `qq_push_receipts` making bookkeeping retries idempotent. Cancellation/expiry does not delete totals. Counting starts at upgrade; a crash between external acknowledgement and local persistence may undercount.
- **Live notification authority**: both trusted sender role `owner` and an explicit live `allow_proactive_msg=true` response; unknown identity or permission is not authorization.
- **Initial live replay**: one durable pending cursor for a room already live at subscription time. An enabled monitor immediately queues a lifecycle-bound asynchronous send using the subscription snapshot, independently of the minute polling slot. Initial and polled sends share permission checks, send spacing and durable cursor claims; a duplicate subscription never resets the cursor. Pending cursors survive unload before the claim and recover through normal polling.
