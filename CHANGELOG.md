# Changelog

All notable changes to Jiangshi are listed here. Each release's notes are also on its GitHub release page, and the in-app updater shows them.

## [Unreleased]

### Additions
- Bookmarks on Suwayomi series stay in step with the server, in both directions, like read status. The first time a series syncs, chapters bookmarked on either side end up bookmarked on both. Bookmarks that can't be sent, such as while offline, are kept, sent on the next sync and listed in Waiting to send
- Suwayomi series can download chapters on the server. Long-press a chapter or volume to download it on the server or remove it, or use Server downloads in the series menu for all unread chapters or to remove read ones. Chapter rows say when a chapter is on the server, queued there or downloading, and follow the server's progress
- Browsing a Kavita source has a Want to Read button next to On Deck, listing the series on your Want to Read list on the server. Long-press a series there to take it off. Kavita series pages can add a series to Want to Read or remove it from their menu. On phones, the browse buttons other than the selected one show only their icon, so they all fit
- Statistics can track a reading goal, such as 20 chapters a week or 30 minutes a day, with progress for the current day, week, month or year and whether you're on track
- Statistics warns when you haven't read yet today and your streak would end
- A year in review for each year you've read in: time read compared with the year before, chapters, pages, new series, days read, longest streak, busiest month and day, favourite time of day, top series and genres, and comics, manga and webtoons compared
- Series pages say roughly how long the unread chapters would take at your pace. It goes by pages wherever their number is known, such as for Kavita series, so a long volume counts for more than a single issue. The pace comes from the series' own reading once there's enough of it, and from series like it before that. Tap the reading line to see each day you read the series, for how long and which chapters

### Changes
- Suwayomi tracking and sync read the per-user fields on servers with user accounts, which replace older fields Suwayomi has deprecated, and still work with servers from before user accounts

## [1.6.0] - 2026-11-19

### Additions
- Kavita series send the page you reach to the server as you read, so Kavita's reader picks up where you left off. Finishing a chapter marks it read on Kavita, including series outside the library or not linked to the tracker. Reading in incognito mode isn't sent
- Library updates bring in chapters read on a Suwayomi server, so unread counts stay current without opening each series. This includes series the update restrictions skip, as it only asks your own server
- Browsing a Kavita source has an On Deck button next to Popular and Latest, listing the series you're partway through on the server, most recently read first. EPUBs are left out, as they can't be opened. Long-press a series there to take it off On Deck
- Settings → Tracking shows updates still waiting to reach a tracker, Kavita or Suwayomi, such as ones made offline. Send them now and see whether they went through, or discard any that never will

### Changes
- Less space above the large title on screens like Library, when "Expanded toolbar" is on
- Marking Suwayomi chapters read or unread reaches the server whatever "Update tracking when marked as read" is set to, and finishing a chapter in the reader marks it read there. The server's own trackers are still only updated as your tracking settings say
- Chapters marked unread on a Suwayomi server go back to unread in the app for any series, not only those linked to the tracker. The first time a series syncs on this version, chapters read in the app but not on the server are sent to it, so they aren't undone. Marks that can't be sent, such as while offline, are kept and sent on the next sync

### Bug Fixes
- Fixed finished Kavita chapters marked unread turning back to read the next time the series was opened. Marking chapters read or unread now reaches the server from the series page, the library, Recents and the reader. Marks that can't be sent, such as while offline, are kept and sent before the series next syncs

## [1.5.0] - 2026-11-19

### Additions
- A new Statistics page about your reading: time read, chapters and pages, streaks, time per day or month, a reading calendar, when you read, most read series with the issues, chapters or volumes read, top genres, and comics, manga and webtoons compared. A Library section shows unread chapters, roughly how long they'd take at your pace, and series not opened in 30 days
- Reading time is recorded from this version on, per session, with gaps over 5 minutes without a page turn capped so a reader left open doesn't count. Incognito reading isn't recorded. Stats are kept apart from history, so clearing history doesn't clear them, and they're included in backups with history
- Each series page shows how long you've spent reading it, over how many sessions, and when you first and last read it. The line appears once the series has been read on this version or later

### Other
- The old statistics page and its detailed breakdown are removed

## [1.4.0] - 2026-11-15

### Additions
- The Suwayomi tracker in the tracking sheet lists the trackers linked to the series on the server, with their status and last chapter read. Trackers behind the server or signed out there are marked in red

### Bug Fixes
- Fixed Suwayomi tracking failing with "Unauthorized" on servers that use UI Login. Tracking now signs in the same way as the Suwayomi extension
- The skipped updates notification says why series were skipped, and tapping it opens the list of skipped series in place of a help page that no longer exists

## [1.3.1] - 2026-11-08

### Bug Fixes
- Fixed chapters marked unread on a Suwayomi server staying read in the app. This applies to series linked to the Suwayomi tracker with "Update tracking when marked as read" on
- Library updates no longer change read status for Suwayomi series, which could undo chapters marked unread on the server. It syncs when a series is opened or refreshed

## [1.3.0] - 2026-11-08

### Additions
- Suwayomi series bring in chapters read and the page you reached on the server when opened, and send your page back as you read. Marking chapters read or unread reaches the server too, with "Update tracking when marked as read" on

## [1.2.1] - 2026-11-08

### Bug Fixes
- Fixed "The image couldn't be loaded" when rotating to landscape on AVIF pages, on devices whose system decoder can't read them
- Fixed Suwayomi tracking failing with HTTP 404 on servers that no longer have the REST API. Progress still reaches any trackers linked on the server

## [1.2.0] - 2026-10-29

### Additions
- Tap a volume row to collapse or expand its chapters. Long-press still opens the volume's menu. Fully read volumes start collapsed, and each series remembers the volumes you open or close
- Series whose tags and source don't set a reading mode switch to long strip when their pages turn out to be webtoon slices, with art that carries on from one page into the next. Separately drawn pages stay in paged mode

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
