# Klipper Control

Android application for managing multiple Klipper printers through Moonraker.

## Current implementation

The starter implements:

- Android minSdk 19, Android 4.4.2
- Multiple Moonraker printer profiles
- Printer switching
- Klipper state monitoring
- Print state monitoring
- Hotend and bed temperature monitoring
- Print progress
- Pause, resume and cancel
- Emergency stop
- GCode upload using Android document picker
- USB OTG compatible storage through the Android Storage Access Framework

## Architecture

MoonrakerClient uses the HTTP API for commands and file upload.

For a production version, the status layer should be changed to a persistent WebSocket subscription. Moonraker explicitly provides asynchronous printer object updates through WebSocket.

The UI intentionally avoids Jetpack Compose because the application must support API 19. A compatibility UI layer can select visual resources at runtime:

- API 19 to 20: platform widgets and legacy theme
- API 21 to 27: Material style
- API 28 to 30: Material 2 style
- API 31 and newer: Material 3 style

The logical screen structure remains identical across all versions.

## First configuration

Change the example printer URL in MainActivity:

http://192.168.1.50:7125

Then build the application.

## Important

This is a functional foundation, not a finished production client. The next production layer should add:

- persistent printer storage
- WebSocket subscriptions
- automatic reconnect
- printer discovery
- file browser
- print queue
- macro controls
- camera streams
- authentication
- Material 3 theme for modern devices
- legacy theme resources for API 19 and 20
- lifecycle aware connection management
