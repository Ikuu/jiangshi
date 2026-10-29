# Changelog

All notable changes to Jiangshi are listed here. Each release's notes are also on its GitHub release page, and the in-app updater shows them.

## [Unreleased]

### Additions
- Tap a volume row to collapse or expand its chapters. Long-press still opens the volume's menu. Fully read volumes start collapsed, and each series remembers the volumes you open or close

### Bug Fixes
- Fixed Kavita series outside the library showing every chapter as unread. Opening a Kavita series now brings in what you've read on the server, and partly read chapters pick up at the page you reached

## [1.1.0] - 2026-10-28

### Additions
- Kavita series show a row for each volume above its chapters, using the volumes on the Kavita server. Other sources use "Vol. 2" in chapter names. Comics whose volumes are years (1998, 2025) stay a flat list
- The chapter count says what the entries are, like "3 volumes · 7 chapters" or "25 issues"
- Volume rows show how many of their chapters are read. Long-press one to download, mark as read or unread, or remove downloads for the whole volume
- Chapters whose names don't include their number get it in front, like `#24 · Part 6` for comics or `Ch. 24 · …` for manga
- Kavita chapters show their page count, and "Page 5 of 24" while partly read, in place of the "Chapter"/"Volume" label

### Changes
- The library's series type filter only offers the types in your library, and hides when there's only one. Webtoon gets its own button instead of sharing Manhwa's
- Kavita series in a comic library count as comics everywhere, including the library filter and search. The library looks up each series' library type once in the background and remembers it
- Series in a Kavita comic library, or with comic issues, say "About this comic" instead of "About this manga"
- Range download and mark as read count only chapters, not volume rows

### Bug Fixes
- Fixed tracking updates made offline, or before a VPN connects, being lost. They stay queued and are retried, and rereading an earlier chapter offline no longer lowers a pending update

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
