# Security Policy

## Privacy & Local-Only Design

- **No telemetry or tracking**: Kokerqualizer collects zero analytics, crash telemetry, or advertising identifiers.
- **Audio capture (`RECORD_AUDIO`)**: Used solely in real time via Android's `Visualizer` API to render on-screen Spectrum, Wave, and VU meters. Audio is never recorded, stored, or transmitted.
- **YouTube WebView cookies**: Stored locally inside Android's standard `WebView` `CookieManager` on your device only.
- **Settings & Presets**: Stored locally in private `SharedPreferences` and exported only when you explicitly tap Export or Share.

## Supported Versions

| Version | Supported |
|---|---|
| 1.5.x | Yes |
| < 1.5.0 | Please update to latest release |

## Reporting a Vulnerability

If you discover a security issue, please report it privately to the repository maintainer via GitHub Security Advisories rather than opening a public issue. Include clear reproduction steps and affected Android version.
