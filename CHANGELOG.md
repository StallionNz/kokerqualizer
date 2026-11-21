# Changelog

All notable changes to **Kokerqualizer** are documented in this file.

## [1.5.5] - 2026-11-21
### Fixed
- Prevented tapping the floating 3D orb to close its popup from immediately bouncing back open (`ACTION_OUTSIDE` + orb tap debounce).
- Added clean `YouTubePlayerView.stopAndReset()` when deleting the last saved playlist.
- Added defensive null-preset guards in `EqEngine`.

## [1.5.4] - 2026-11-21
### Performance & Fixes
- Eliminated per-frame `RadialGradient` / `LinearGradient` allocations inside `onDraw()` across the 3D orb, Visualizer, EQ Curve, and Rotary Knobs.
- Added screen-off battery saver to pause the 3D orb's 30fps loop when the screen is non-interactive.
- Cancelled snap/morph/knob `ValueAnimator`s cleanly on user touch and view detach.
- Added `NaN` / `Infinity` protection to `EqStore.clamp()`.
- Fixed YouTube mobile SPA `<video>` hook re-binding, auto-pausing YouTube during Sound Test tones, and live `FLAG_KEEP_SCREEN_ON` syncing in Car Mode.

## [1.5.3] - 2026-11-21
### Changed
- Updated main signature theme across the app, launcher icon, Home Screen widget, 3D orb, and default presets to electric **Neon Shocking Green (`#39FF14`)**.

## [1.5.2] - 2026-11-21
### Added
- Upgraded floating companion bubble into a 3D depth-of-field crystal glass orb with volumetric shading, subsurface rim bounce light, curved glass reflection, and orbiting bokeh stardust forming a pulsating glowing **K**.

## [1.5.1] - 2026-11-21
### Fixed
- Added Back key handling to collapse the floating bubble popup card.
- Fixed landscape/portrait orb edge snapping and made the floating popup scrollable and height-capped.
- Guarded all `WindowManager` overlay operations on the main thread.

## [1.5.0] - 2026-11-21
### Added
- Floating **Bubble Popup Companion** (`SYSTEM_ALERT_WINDOW`) with edge-snapping drag, drop-to-hide zone, quick preset switcher, Power/A-B/Highway Boost toggles, live Bass/MBC/Preamp sliders, and Now Playing media controls.

## [1.4.0 - 1.4.3] - 2026-11-20 / 2026-11-21
### Added & Fixed
- Multi-playlist **In-App YouTube Player** with automatic startup sync, 15-minute live refresh, YouTube `lockupViewModel` + RSS parsing, label-blocked embed fallback, Car Mode background playback continuity, persistent sign-in cookie flushing, and `moveTaskToBack` preservation on Back.

## [1.3.0 - 1.3.2] - 2026-11-20
### Added & Fixed
- Native `MediaSession` transport controls (`KokerMediaListenerService`) with live **Now Playing** display in Car Mode.
- Android 16 Predictive Back (`OnBackInvokedCallback`), landscape-to-portrait inset drift fix, two-tier MBC HAL fallback, zero-allocation reactive card glow, and scroll-through Preset Lock without toast spam.

## [1.2.0] - 2026-11-20
### Added
- True 3-band **Multiband Compressor (`Punch MBC`)**, **Wired/USB-C headphone auto-switching**, Car Mode **Highway Boost (+3 dB)** & live visualizer backdrop, animated preset morphing, bass-reactive neon glow, live Headroom/Auto-Gain telemetry, and **Audio Health Check**.

## [1.0.0 - 1.1.0] - 2026-11-20
### Added
- Initial release of Kokerqualizer: switchable 5/10-band ISO & Car-Sub EQ (±18 dB), vertical sliders/curve + Brush + Undo/Redo + Lock, live Bars/Wave/VU visualizer, retro rotary knobs, adaptive Car Mode, Bluetooth per-device automation, Sound Test suite, Quick Settings tile, interactive notification, Home Screen widget, and JSON backup/share.
