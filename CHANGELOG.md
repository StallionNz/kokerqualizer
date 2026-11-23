# Changelog

All notable changes to **Kokerqualizer** are documented in this file.

## [2.0.3] - 2026-11-23
### Refactored (Modular Architecture Split)
- **MainActivity Split (2,656 → 1,459 lines)**: Extracted `MainYouTubeController.java` (player card, playlist sync, RemoteBridge), `MainKnobRackController.java` (Mastering Rack knobs & DSP toggles), `MainNavigationController.java` (slide-out drawer & sub-panel overlay), and `MainDialogsHelper.java` (0.1 dB fine-tuner, A/B/C Slot B, Sleep Timer dialogs).
- **DrawerPanels Split (2,115 → 226 lines)**: Turned `DrawerPanels.java` into a slim facade & shared helper kit delegating to `PresetPanels.java`, `RoutingAndSettingsPanels.java`, `AudioToolsPanels.java`, and `YouTubeDrawerPanel.java`.
- **BubbleOverlayManager Split (1,230 → 450 lines)**: Extracted `BubbleOrbView.java` (3D crystal glass orb custom view) and `BubblePopupController.java` (floating mini-mixer popup card & media transport).

## [2.0.2] - 2026-11-23
### Fixed (Universal Audit Pass #1–#12 + #14)
- **WebView & URL Hardening**: Validated `loadDirectUrl()` against HTTPS + trusted YouTube/Google hosts, and sanitized `currentPlaylistId` in `setFullWebMode()`.
- **Backup & State Integrity**: Included Per-App `appRules` in JSON export/import (`formatVersion=4`), batched `btRules`/`appRules` import writes, excluded raw Bluetooth MACs from Downloads backups for privacy, and cleared `comparePresetId` when deleting the Slot B preset.
- **Concurrency, Performance & Battery**: Synchronized Bluetooth debounce state in `EqService`, disabled background `appWatchTick` completely when `perAppAutoSwitch` is off, added generation-counter cancellation to `YouTubePlaylistSync`, capped `playNextQueue` at 50 tracks, cached `AutoEqCatalog` profiles as an immutable static list, fixed shared `LayoutParams` in `DrawerPanels`, and restored window brightness cleanly in `CarModeActivity.onPause()`.
- **Automated Unit Tests**: Added `tests/KokerUnitTests.java` covering 10↔15-band resampling, curve smoothing, `NaN`/`Infinity` clamping, UTF-16 emoji monogram safety, `KOKER1:` share-code round-trips, Auto-EQ catalog caching, and YouTube playlist URL parsing.

## [2.0.1] - 2026-11-22
### Security & Audit Hardening
- **WebView Sandbox Hardening**: Restricted in-app `WebView` navigation to HTTPS YouTube/Google Auth hosts (routing external links safely to the system browser and blocking `file://`/`http://`/mixed content), disabled `allowFileAccess` & `allowContentAccess`, and sanitized `videoId`/`playlistId` on mobile URL construction.
- **Privacy & Thread Safety**: Set `android:allowBackup="false"` in `AndroidManifest.xml`, marked `YouTubePlayerView.activeRemoteBridge` and `SleepTimerManager.listener` `volatile`, and added `NETWORK_PROVIDER` fallback in `CarExtrasHelper` when only Approximate Location is granted.

## [2.0.0] - 2026-11-22
### Added — All 40 Pro Upgrades
- **DSP & Audio (#1–#10)**: Sub-Sonic Rumble Filter (`SubCut`), Analog Warmth exciter (`Warmth`), 1-Tap `∿ Smooth` curve, built-in searchable **Auto-EQ Headphone & Speaker Library** (15 models), Headphone Crossfeed (`XFeed`), Late-Night Whisper Boost (`🌙 Night`), Smooth 250ms Gain Ramp, Selectable Limiter Ceiling (`-0.1 Loud / -1.0 Safe / -3.0 Studio`), Quick Bass & Treble Tilt macro sliders (`⚖ Tilt`), and Live Peak dBFS + Clipping Counter with 1-tap `-2 dB` Auto-Trim.
- **Visuals & Deck UX (#11–#20)**: FabFilter-style live EQ Curve Overlay on Spectrum, 4th **Mirror** spectrum visualizer mode, Custom Peak-Decay Speed (`Slow/Med/Fast`), Multi-Color Neon Gradient bars, Compact `🧘 Zen` Deck mode, A/B/C Preset Comparison (`Slot B` via long-press), Preset Search/Tags/Notes, Swipe-to-Switch preset banner, Per-Band Solo Audition inside the `0.1 dB` fine tuner, and Bass-Reactive Bottom Bar Pulse.
- **Floating 3D Crystal Orb (#21–#26)**: Beat-Reactive Neon Green aura & stardust, Adjustable Orb Size (`Small/Med/Large`), Idle Auto-Fade (45% opacity), Double-Tap Shortcut (`Popup/Next/Toggle EQ/Car`), Custom Stardust Monogram Letter/Symbol, Auto-Tuck Edge Tab in landscape, and Mini Media Volume Slider inside the orb popup.
- **Car Mode Dash (#27–#31)**: Full-Screen Swipe Gestures (Left/Right skip, Up/Down volume), Voice-Announced Preset Changes (TTS), Live Clock + Battery % + GPS Speed pill, Night-Dim Low-Glare toggle, and GPS Speed-Linked Highway Boost (`>= 70 km/h`).
- **In-App YouTube Player (#32–#36)**: Sleep Timer (`15m/30m/60m/End of Track`) with smooth 5s fade-out + `⏻` shutdown, Track Repeat (`🔁`), Playback Speed (`0.75x–1.5x`), Auto-Skip unavailable tracks + Play History, and Long-Press Play Next Queue.
- **Automation, Sharing & Widgets (#37–#40)**: Per-App Auto-Preset Switching (Spotify, YT Music, YouTube, Audible, SoundCloud), 1-Tap Backup to `Downloads/Kokerqualizer/`, Compact 1-Line Copy/Paste Preset Share Codes (`KOKER1:...`), and 1x1 Compact Orb Home Widget.

## [1.6.7] - 2026-11-21
### Fixed
- **Universal Audio-Focus Stop on `⏻` Shutdown**: Briefly claims and releases exclusive `AudioFocusRequest` on shutdown so every playing audio app pauses immediately even without Notification Access, plus refreshes widget & Quick Settings tile.
- **Top Header Layout Polish**: Stopped sharing a single `LayoutParams` instance between `bubblePill` and `statusPill`, and added single-line ellipsizing so `KOKERQUALIZER` + pills + `⏻` stay crisply aligned on all screen widths.
- **YouTube Unmute Timer Deduplication**: Cleared prior `window.__kokerUnmuteInt` interval on rapid Next skips and cleared `initialIframeVideoId` cleanly on reset/mode switch.

## [1.6.6] - 2026-11-21
### Added
- **One-Tap Stop All Music & Shut Down Button (`⏻`)**: Added a quick red power-off button to the Main Deck top bar, Slide-out Drawer (`⏻ Stop All Music & Exit App`), Floating 3D Orb Popup, and Car Mode Dash (`⏻ STOP`). One tap pauses all active music (in-app YouTube + external Spotify/YT Music apps), preserves YouTube sign-in cookies, releases DSP, hides the floating bubble, clears the service notification, and exits the app cleanly.

## [1.6.5] - 2026-11-21
### Fixed
- **Fixed Songs Muting on Next/Skip**: Cleared YouTube mobile web's persisted muted `localStorage`/`sessionStorage` state, enforced unmute (`video.muted=false`, `volume=1.0`, `movie_player.unMute()`, and clicking YouTube's unmute overlay) on track transitions, `volumechange`, and `yt-navigate-finish`, and added `player.unMute()` + `setVolume(100)` across YouTube IFrame API playback hooks.

## [1.6.4] - 2026-11-21
### Fixed
- **YouTube Player Queue & Autoplay Respect**: Fixed queued track switching during IFrame boot (`pendingVideoId` vs `initialIframeVideoId`) without unnecessary HTML reloads, and respected `autoPlayOnStartup=false` in Full YouTube Web Mode.
- **Zero-Jitter Long-Press & No-Op Tap Guards**: Ignored sub-slop finger micro-jitter during 480ms EQ band long-press, skipped redundant saves on single knob taps without dragging, and prevented double `onMorphFinished` callbacks on Morph Pad double-tap reset.
- **State & Memory Guards**: Reset drag locks cleanly on Bubble collapse and Car Mode pause/rotation, sanitized blank imported preset IDs/names, and capped JSON import (2 MB) and HTTP sync reads (6 MB) against OOM.

## [1.6.3] - 2026-11-21
### Fixed
- **No More Sub-Panel Teardown Mid-Interaction**: Prevented background `ACTION_STATE_BROADCAST` updates (such as volume changes or test-tone sessions) from destroying active SeekBars, Sound Test Suite, Calibration Wizard, or YouTube search input.
- **Exact Long-Press Restore & No-Op Tap Guard**: Restored exact 10/15-band snapshots on long-press fine-tune cancel and skipped redundant saves when tapping a slider thumb without dragging.
- **Crash & Thread-Safety Hardening**: Eliminated TOCTOU null race on `SoundTestEngine.freqCallback`, guarded all `startActivity` chooser/overlay launches with `try/catch`, reused static `Handler` in `KokerMediaListenerService`, and restricted `CarModeActivity` to `android:exported="false"`.

## [1.6.2] - 2026-11-21
### Fixed
- **True Flat A/B Compare**: Disabled `DynamicsProcessing.Limiter` during A/B bypass and synced A/B, Loudness, Mono & Auto-Gain toggles across `EqService`, the floating 3D orb, notification, and widget.
- **Zero-Overhead Reverb**: Lazily instantiate `PresetReverb` only when acoustic space reverb is enabled and release it cleanly when turned `Off`.
- **2D Morph Pad & 15-Band Precision**: Fixed 2nd-drag Undo capture, sculpted 15-band curves directly without resampling blur, and auto-recentered morph base when sliders/knobs/presets change.
- **0.1 dB Fine Tuner & Sliders**: Prevented accidental audio jumps when long-pressing a slider thumb, synced manually typed text before nudge buttons, and fixed `-0` / `+0` display for small sub-1 dB offsets.
- **Lifecycle & Crash Hardening**: Cleared `EqEngine.statusListener` in `MainActivity.onPause()`, cleared `YouTubePlayerView` handler/listener on destroy, stopped `SoundTestEngine` when switching drawer panels, hid floating bubble dismiss zone on screen rotation, guarded async YouTube sync callbacks against destroyed Activities, and eliminated remote-bridge null races in Car Mode & Bubble popup.

## [1.6.1] - 2026-11-21
### Fixed
- Locked the floating 3D crystal orb and header bubble indicator to vivid **Neon Shocking Green (`#39FF14`)** (and dimmed emerald green when EQ is bypassed instead of turning grey), replacing the dark slate-blue sphere body with deep emerald-obsidian 3D glass shading and a stronger volumetric neon-green aura.

## [1.6.0] - 2026-11-21
### Added
- **Switchable 5 / 10 / 15-Band Pro EQ** (classic 2/3-octave studio ISO layout: `25 Hz – 16 kHz`) with **Long-Press `0.1 dB` Precision Fine Tuner** dialog.
- **Volume-Aware Auto-Loudness** (Fletcher-Munson contour that dynamically enriches bass & sparkle at low media volumes and tapers smoothly at high volume).
- **2D X/Y Sound Morph Pad** (collapsible zero-allocation glowing vector pad to drag-sculpt Deep Sub / Crystal Vocal / Warm / Punchy live).
- **Guided Speaker & Car Calibration Wizard** (7 stepped reference tones with safe level slider that auto-generates a compensated custom preset).
- **Acoustic Space Reverb** (`Off`, `Studio`, `Club`, `Hall`, `Cabin`) + **Mono Speaker** optimization toggle.
- **Restore Factory Presets** button inside Preset Library.

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
