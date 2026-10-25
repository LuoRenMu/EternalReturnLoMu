# ADR 0003: Explicit business HTTP transport

## Status

Accepted — 2026-10-23

## Context

A player query (`/search`) reported `The number of released permits cannot be greater than 100` from Ktor 2.3.12 CIO's `ConnectionFactory.release` / `Endpoint.connect`. This matches the failure path reported in [KTOR-5992](https://youtrack.jetbrains.com/issue/KTOR-5992/CIO-engine-concurrent-request-limit-semaphore-released-too-many-permits). A local 2,400-request TLS-failure/cancellation probe did not reproduce the intermittent permit failure; this is a transport mitigation, not a proven reproduction or an upstream-version fix.

The business client and Simbot SDK clients share a runtime classpath. Adding another engine can also change SDK defaults through service discovery, including WebSocket transport selection.

## Decision

- `RequestManager` explicitly uses Ktor's JDK Java engine on the existing Java 17 baseline, avoiding CIO's connection-permit bookkeeping for business queries and resource downloads. Keep Ktor 2.3.12 to avoid an unrelated SDK compatibility upgrade.
- Preserve request/connect deadlines, JSON conversion, caching, user agent and bounded transient/server-error retries. The 60-second total request deadline bounds requests; Java transport does not provide CIO's independent socket-idle timeout.
- Caller `CancellationException` is never classified as transient because of a nested I/O cause. Do not swallow permit-accounting exceptions or increase semaphore limits to hide the symptom.
- Explicitly keep Simbot QQ and OneBot API/WebSocket factories on CIO, preserving their pre-existing transport behavior independently of service-loader order. This does not claim to eliminate CIO failures inside SDK-owned clients.

## Verification and consequences

Local fixtures exercise JSON POST/response handling, 5xx retry, incomplete-response retry, cache coalescing, deadlines, cancellation, and concurrent failed TLS handshakes followed by successful requests. Engine-selection and cancellation-classification contracts failed before the change. These checks do not prove the production race was reproduced.

`RequestManager` and adapter transport wiring are parent-loaded main-program code. Updating only command plugins cannot activate this change; deployment requires replacing and restarting the main application. Production recovery must be checked separately after deployment.
