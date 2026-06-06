# Security Policy

## Supported Versions

Only the latest released version of KtorMonitor is actively supported with security updates.

| Version | Supported          |
|---------|--------------------|
| latest  | :white_check_mark: |
| older   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in KtorMonitor, **please do not open a public GitHub issue**.

Instead, report it privately using one of the following methods:

- **GitHub Private Vulnerability Reporting:** Use the [Security Advisory](https://github.com/cosminmihu/ktor-monitor/security/advisories/new) feature on GitHub to submit a private report.
- **Email:** Send details to the maintainer at the email address listed on the [GitHub profile](https://github.com/cosminmihu).

### What to include

Please provide as much of the following information as possible to help us understand and resolve the issue quickly:

- A description of the vulnerability and its potential impact.
- The affected module(s) (e.g., `ktor-monitor-core`, `ktor-monitor-logging`, `ktor-monitor-okhttp-interceptor`, `ktor-monitor-http4k-filter`).
- Steps to reproduce or a proof-of-concept.
- The version(s) of KtorMonitor affected.
- Any suggested mitigation or fix, if available.

## Response Process

1. We will acknowledge receipt of your report within **72 hours**.
2. We will investigate the issue and keep you informed of our progress.
3. Once a fix is ready, we will coordinate a release and give credit to the reporter (unless you prefer to remain anonymous).
4. A public security advisory will be published after the fix is released.

## Scope

This security policy covers all published library modules under the `ro.cosminmihu.ktor` group:

- `ktor-monitor-core`
- `ktor-monitor-core-no-op`
- `ktor-monitor-logging`
- `ktor-monitor-logging-no-op`
- `ktor-monitor-okhttp-interceptor`
- `ktor-monitor-okhttp-interceptor-no-op`
- `ktor-monitor-http4k-filter`
- `ktor-monitor-http4k-filter-no-op`

Sample applications and documentation are **out of scope**.

## Security Considerations

KtorMonitor is a **debugging and development tool**. It is designed to intercept and display HTTP traffic, which by nature includes potentially sensitive data (headers, request/response bodies, authentication tokens, etc.).

**Important recommendations:**

- **Never use KtorMonitor in production builds.** Use the `*-no-op` artifacts (e.g., `ktor-monitor-logging-no-op`) for release/production builds. These are ABI-compatible but perform no interception or storage.
- All intercepted data is stored locally in an SQLite database on the device/machine; it is never transmitted externally by this library.
- Retained call data can be cleared at any time from the KtorMonitor UI.
- Limit the `retentionPeriod` and `maxContentLength` settings to reduce the amount of sensitive data stored on the device.

## License

KtorMonitor is licensed under the [Apache License 2.0](LICENSE).

