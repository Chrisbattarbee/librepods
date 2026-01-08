# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LibrePods unlocks Apple's exclusive AirPods features on non-Apple devices (Linux and Android). The project is a monorepo with three platform implementations:

- **linux-rust/** - Native Linux app in Rust with Iced GUI (active development, current branch)
- **linux/** - Legacy Qt/C++ implementation (maintained but superseded)
- **android/** - Android app with Xposed module (requires root)

## Build Commands

### Linux-Rust (Primary)

```bash
cd linux-rust

# Development build
cargo build

# Release build
cargo build --release

# Run with debug logging
cargo run -- --debug

# Build AppImage (requires linuxdeploy and appimagetool)
just build-appimage

# Run clippy linter
cargo clippy
```

CLI flags: `--debug/-d`, `--no-tray`, `--start-minimized`, `--le-debug`, `--version/-v`

### Nix Development

```bash
# Enter dev shell with all dependencies
nix develop

# Build package
nix build

# Format nix files
nix fmt
```

### Android

```bash
cd android

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Legacy Linux (Qt/C++)

```bash
cd linux
mkdir build && cd build
cmake ..
make -j $(nproc)
```

## Architecture

### Linux-Rust Module Structure

```
linux-rust/src/
├── main.rs              # Entry point, CLI parsing, thread orchestration
├── bluetooth/
│   ├── aacp.rs          # AirPods Application Continuity Protocol
│   ├── att.rs           # Attribute Protocol for GATT
│   ├── discovery.rs     # Device discovery
│   ├── le.rs            # BLE monitoring with BlueZ
│   └── managers.rs      # Device connection management
├── devices/
│   ├── airpods.rs       # AirPods-specific protocol logic
│   ├── nothing.rs       # Nothing earbuds (future)
│   └── enums.rs         # Device type definitions
├── ui/
│   ├── window.rs        # Main Iced window
│   ├── tray.rs          # System tray (ksni/AppIndicator)
│   ├── airpods.rs       # AirPods UI components
│   └── messages.rs      # UI message types
├── media_controller.rs  # MPRIS media controls
└── utils.rs
```

### Threading Model

- Main thread: Iced UI rendering
- Async thread: Tokio runtime for Bluetooth I/O
- Communication via `tokio::sync::mpsc` channels between UI and Bluetooth layers

### Key Dependencies (Rust)

- **bluer** - BlueZ D-Bus bindings for Bluetooth
- **iced** - Native GUI framework
- **tokio** - Async runtime
- **ksni** - System tray (StatusNotifierItem)
- **libpulse-binding** - PulseAudio integration
- **aes** - AES encryption for AirPods protocol

### Android Architecture

- Xposed hook system (requires root + Xposed framework)
- Jetpack Compose UI with Material 3
- Native C++ components via CMake
- Root module in `root-module/` for Magisk

## Protocol Notes

- AirPods communicate via AACP (AirPods Application Continuity Protocol) over Bluetooth L2CAP
- Device identification uses manufacturer ID spoofing for multi-device and hearing aid features
- Hearing aid features require DeviceID to be set as Apple in `/etc/bluetooth/main.conf`
