# Kokerqualizer

A standalone, zero-bloat Android graphic equalizer, mastering DSP deck, floating 3D orb companion, Car Mode dash, and live-synced YouTube playlist player (`com.buddyapps.kokerqualizer`). Built in pure Java against the Android Framework (API 28+ / Android 9–16) with zero third-party SDKs, zero ads, and zero trackers.

## Features

- **Audio DSP Engine (`DynamicsProcessing`)**
  - Switchable **5-band / 10-band / 15-band Pro** EQ with **ISO** (`31 Hz – 16 kHz`), **Car Sub** (`25/45/80 Hz...`), and **15-band 2/3-octave studio** layouts
  - **±18 dB** gain range with **Smart Auto-Gain**, **Volume-Aware Auto-Loudness**, and **Anti-Clipping Limiter**
  - True **3-band Multiband Compressor (`Punch MBC`)** (Low / Mid / High) with automatic two-tier HAL fallback
  - **Bass Boost** with per-preset character (**Tight**, **Deep**, **Boomy**), **Preamp**, **3D Virtualizer**, **L/R Stereo Balance**, **Acoustic Space Reverb** (`Studio`, `Club`, `Hall`, `Cabin`), **Mono Speaker** mode, and instant **A/B Flat** compare
  - **Dual Audio Hook Mode** (active media sessions + global output mix fallback)
- **AMOLED Neon Green Deck & Custom Canvas Controls**
  - Electric **Neon Shocking Green (`#39FF14`)** default theme with customizable per-preset neon glow colors
  - Switchable **Vertical Sliders** and **Draggable Smooth Spline Curve**, **Smooth Brush** swipe painting, **double-tap to zero**, **long-press 0.1 dB fine tuning**, **2D X/Y Sound Morph Pad**, **Preset Lock**, **Undo/Redo**, **Restore Factory Presets**, and smoothly animated preset transitions
  - **Guided 7-Step Speaker & Car Calibration Wizard** that plays stepped tones and auto-builds a compensated preset
  - Live **Spectrum Bars**, **Oscilloscope Wave**, and **Stereo L/R VU Meter + Clip LED** visualizer with **bass-reactive card glow**
  - Symmetrical 2×3 **Retro Hi-Fi Rotary Knob Rack** with subtle haptic feedback
- **Floating 3D Orb Companion**
  - System-wide floating 3D crystal glass orb with depth-of-field bokeh stardust that forms a pulsating glowing **K**, snaps to screen edges, sleeps when the screen is off, and pops open a floating AMOLED control card over any app
- **Adaptive Car Mode & Device Automation**
  - Adaptive portrait & landscape dash with giant pinned preset tiles, **🛣 Highway Boost (+3 dB)**, toggleable live visualizer backdrop, keep-screen-on, and native `MediaSession` Now Playing + song skip controls
  - Per-Bluetooth-device **Auto-Start**, assigned preset, optional **connect media volume**, and auto-launch Car Mode
  - **Wired & USB-C headphone/DAC** auto-switching + automatic return to your **Phone Speaker** preset on disconnect
  - Quick Settings Tile, interactive persistent notification, and Home Screen widget
- **In-App YouTube Playlists & Calibration Tools**
  - Multi-playlist live YouTube sync on startup & every 15 minutes, built-in player with auto-fallback for embed-restricted tracks, persistent Google sign-in cookies, and Car Mode sync
  - Built-in **Sound Test Suite** (20 Hz – 20 kHz sine tones, quick sub buttons, auto sub/full sweeps, Pink noise, and L/R speaker check)
  - One-tap **Audio Health Check** for DSP, permissions, battery keepalive, and Samsung Dolby/SoundAlive settings
  - **JSON Preset Backup, Restore & Android Share**

## Installation (Standalone APK)

1. Download `kokerqualizer-v1.5.5.apk` from the [GitHub Releases](../../releases) page.
2. Open the APK on any Android 9+ device and allow installation from your browser or file manager if prompted.
3. Verify the APK integrity against `SHA256SUMS.txt` attached to the release.
4. *(Samsung tip)* Turn off Samsung's built-in **Dolby Atmos / Equalizer** in Android Sound settings if it competes with Kokerqualizer, and open **Audio Health Check** inside the app to enable optional permissions (Mic for live visualizer, Bluetooth for car auto-start, Appear on top for the 3D orb, Notification access for song skip).

## Permissions Explained

| Permission | Purpose |
|---|---|
| `MODIFY_AUDIO_SETTINGS` | Applies real-time DSP equalizer, MBC, bass boost, and limiter |
| `RECORD_AUDIO` *(optional)* | Captures live audio mix FFT/waveform for Spectrum, Wave, VU & reactive glow (never recorded or uploaded) |
| `BLUETOOTH_CONNECT` *(optional)* | Lists paired car stereos/speakers for automatic profile & volume switching |
| `SYSTEM_ALERT_WINDOW` *(optional)* | Displays the floating 3D Orb companion over other apps |
| `POST_NOTIFICATIONS` *(optional)* | Persistent preset/bypass notification controls |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keeps DSP processing active reliably in the background |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Syncs and plays user-added YouTube playlists inside the app |
| `RECEIVE_BOOT_COMPLETED` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional start-on-boot and background keepalive |

## Building From Source

Kokerqualizer uses plain Java 8 and the standard Android 36 SDK (`android.jar`, `aapt2`, `d8`, `apksigner`) with no Gradle dependency:

```bash
# Requires OpenJDK 17+, Android build-tools (aapt2, d8, apksigner), and android-36 android.jar
# Project layout:
#   AndroidManifest.xml
#   app.properties
#   java/com/buddyapps/kokerqualizer/*.java
#   res/...
```

## Contributing & Security

- Contributions are welcome — please read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- See [CHANGELOG.md](CHANGELOG.md) for release notes and [SECURITY.md](SECURITY.md) for security reporting.

## License

Licensed under the [MIT License](LICENSE).
