# Changelog

All notable changes to Jiangshi are listed here. Each release's notes are also on its GitHub release page, and the in-app updater shows them.

## [1.0.0] - 2026-10-25

The first release of Jiangshi.

### Additions
- Added a default reading mode per source, set in the source's extension settings. Sources without one use the global default
- Added APKs for armeabi-v7a, x86 and x86_64 alongside arm64-v8a, plus a universal APK that runs on any device
- The in-app updater checks Jiangshi's GitHub releases and shows Jiangshi's release notes

### Changes
- New name, icons and application ID, so Jiangshi installs alongside Yōkai
- Removed Firebase, Crashlytics and analytics from the app, along with the crash report toggle. Jiangshi sends no telemetry
- Tracker requests identify as Jiangshi in their User-Agent
- The About screen drops Yōkai's community links and changelog, and shows which Yōkai release the build is based on

### Bug Fixes
- Fixed the reader going back a page when rotating into double page mode, including when the current page turns out to be a spread
- Fixed rotating the device reopening the previously viewed page instead of the current one, including in incognito mode
- Fixed Suwayomi tracking with newer servers that leave `meta` out of their responses
