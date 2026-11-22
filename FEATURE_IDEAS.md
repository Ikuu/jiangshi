# Feature ideas

Ideas from the Suwayomi and Kavita sync work, in suggested order. Added 2026-11-08.

## Check first

- [x] **Browse Suwayomi series from Jiangshi.** Suwayomi removed most of its REST API on 2026-11-01, and the Suwayomi extension may still use it. Reading chapters works, but browsing hasn't been tested on a server without the REST API. If it's broken, fix it before the ideas below. Confirmed working (2026-11-19).

## Ideas

| Done | Idea | What you'd get | Effort |
| --- | --- | --- | --- |
| [x] | **Kavita unread fix** (unreleased) | Marking chapters read or unread reaches Kavita, so a finished chapter marked unread stays unread. The reader also sends the page reached and finished chapters. Tested on the tablet 2026-11-19 | Small to medium |
| [x] | **Suwayomi sync during library updates** (unreleased) | Unread counts in the library stay current without opening each series. Removes the 1.3.1 trade-off, where read status only syncs when a series is opened or refreshed | Small |
| [x] | **Suwayomi sync separate from "Update tracking when marked as read"** (unreleased) | Manual marks reach Suwayomi without also updating other trackers, and default settings work properly | Small |
| [ ] | **Bookmark sync with Suwayomi** | Bookmarks match on both sides, using `isBookmarked` and the same approach as read status | Small |
| [x] | **Check for new chapters on the server** | Already done by the Suwayomi extension: a refresh asks the server to fetch chapters from the source, while its "Fetch Data From Source" setting is on (the default) | Small to medium |
| [x] | **Kavita On Deck** (unreleased) | An On Deck button when browsing a Kavita source, listing series partway through on the server | Small to medium |
| [x] | **Remove from On Deck** (unreleased) | Long-press a series in On Deck to take it off | Small |
| [x] | **Queue failed Kavita marks** (unreleased) | Kavita read marks that can't be sent are kept, retried and listed in Waiting to send, like Suwayomi's | Small to medium |
| [x] | **Pending tracker updates** (unreleased) | Settings → Tracking lists tracker updates and Suwayomi read marks still waiting to be sent, with Send now and discard | Small |
| [x] | **Show server-linked trackers** (1.4.0) | The tracking sheet shows what's linked on the server and whether it updated, using `trackRecords` | Medium |

## Known follow-ups

- [x] **Deprecated Suwayomi field.** The tracker reads `lastReadChapter`, which Suwayomi has deprecated in favour of `user.lastReadChapter`. Queries now use the `user` fields and fall back to the old ones on servers from before user accounts (unreleased).
- [ ] **Untested on a device.** Marking chapters read or unread from the library and Recents screens uses the same code as the series screen, but hasn't been tried on a real device.
