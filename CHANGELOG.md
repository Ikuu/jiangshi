# Changelog

All notable changes to Jiangshi are listed here. Each release's notes are also on its GitHub release page, and the in-app updater shows them.

## [Unreleased]

### Additions
- Kavita series show a row for each volume above its chapters, using the volumes on the Kavita server. Other sources use "Vol. 2" in chapter names. Comics whose volumes are years (1998, 2025) stay a flat list
- The chapter count says what the entries are, like "3 volumes · 7 chapters" or "25 issues"

### Changes
- Series in a Kavita comic library, or with comic issues, say "About this comic" instead of "About this manga"
- Range download and mark as read count only chapters, not volume rows

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
