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
| [x] | **Bookmark sync with Suwayomi** (unreleased) | Bookmarks match on both sides, using `isBookmarked` and the same approach as read status | Small |
| [x] | **Kavita Want to Read** (unreleased) | A Want to Read button when browsing a Kavita source, and add or remove from the series page | Small to medium |
| [x] | **Suwayomi server downloads** (unreleased) | Download chapters on the server or remove them from the series page, with each row saying whether the server has it | Medium |
| [x] | **Reading goals** (unreleased) | A goal for minutes or chapters a day, week, month or year on the Statistics page, with a warning when today's streak is at risk | Small to medium |
| [x] | **Year in review** (unreleased) | A summary of each year's reading, opened from the Statistics page | Small to medium |
| [x] | **Series reading history** (unreleased) | Tap the reading line on a series page for each day it was read | Small |
| [x] | **Time left at your pace** (unreleased) | Series pages estimate how long the unread chapters would take | Small |
| [ ] | **Kavita ratings** | The score in the Kavita tracking sheet is saved on the server | Small |
| [ ] | **Kavita collections and reading lists** | Browse a collection or reading list from the Kavita source, in its own order | Medium |
| [ ] | **Suwayomi categories** | Series take on their categories from the server | Medium |
| [x] | **Streak reminder notification** (unreleased) | A notification in the evening when the streak would end, rather than only a note on the Statistics page | Small |
| [x] | **Check for new chapters on the server** | Already done by the Suwayomi extension: a refresh asks the server to fetch chapters from the source, while its "Fetch Data From Source" setting is on (the default) | Small to medium |
| [x] | **Kavita On Deck** (unreleased) | An On Deck button when browsing a Kavita source, listing series partway through on the server | Small to medium |
| [x] | **Remove from On Deck** (unreleased) | Long-press a series in On Deck to take it off | Small |
| [x] | **Queue failed Kavita marks** (unreleased) | Kavita read marks that can't be sent are kept, retried and listed in Waiting to send, like Suwayomi's | Small to medium |
| [x] | **Pending tracker updates** (unreleased) | Settings → Tracking lists tracker updates and Suwayomi read marks still waiting to be sent, with Send now and discard | Small |
| [x] | **Show server-linked trackers** (1.4.0) | The tracking sheet shows what's linked on the server and whether it updated, using `trackRecords` | Medium |

## More ideas

Added 2026-11-22, after 1.7.0. Not in any order yet.

| Done | Idea | What you'd get | Effort |
| --- | --- | --- | --- |
| [x] | **Queue the next chapters on the server** (unreleased) | Finishing a Suwayomi chapter asks the server to download the next few unread ones, so they're ready before you get there. Optional, with how many to keep ahead | Small |
| [ ] | **Remove read chapters from the server** | Optionally remove a chapter's server download once it's read, keeping the last few, so the server's disk doesn't fill up | Small |
| [ ] | **Suwayomi server library** | A button when browsing the Suwayomi source, listing the series in the server's library, like Kavita's On Deck | Small to medium |
| [x] | **Server status** (unreleased) | Settings shows whether Kavita and Suwayomi can be reached, their version and whether you're signed in, with sign-in errors explained in plain words | Small |
| [ ] | **Older reading in Statistics** | Chapters read before 1.5.0 count towards Statistics, estimated from the time read and date already in history, and marked as estimated | Small to medium |
| [x] | **Edit reading sessions** (unreleased) | See each session for a day and delete wrong ones, such as a reader left open, so the stats can be fixed. Opened from a day in the reading calendar | Small |
| [ ] | **Rest days** | Choose one or two days a week that don't break the streak if you don't read | Small |
| [x] | **Weekly recap** (unreleased) | An optional notification on Sunday evening with the week's time read and chapters, compared with the week before | Small |
| [ ] | **Month in review** | The year in review, for a single month | Small |
| [ ] | **Statistics widget** | A home screen widget with the current streak and today's progress towards the goal. The app already has a widget module to build on | Medium |
| [ ] | **Finish by a date** | Set a date to finish a series, and its page says how much a day that needs, next to the time left | Small |
| [ ] | **Time left in the chapter** | The reader's menu says roughly how long the rest of the chapter would take at your pace | Small |
| [ ] | **Export statistics** | Save reading sessions as a CSV file | Small |
| [x] | **One series on both servers** (unreleased) | Link a series in Kavita with the same series in Suwayomi, so reading one marks the matching chapters read in the other | Medium to large |

## Known follow-ups

- [x] **Deprecated Suwayomi field.** The tracker reads `lastReadChapter`, which Suwayomi has deprecated in favour of `user.lastReadChapter`. Queries now use the `user` fields and fall back to the old ones on servers from before user accounts (unreleased).
- [ ] **Year in review entry names.** Top series in the year in review count issues for comics and chapters otherwise, where the Statistics page asks Kavita whether entries are volumes. A Kavita series read by volume shows "0 issues read".
- [ ] **Linked series during library updates.** Library updates bring in chapters read on a Suwayomi server, but don't pass them on to a linked Kavita series. That happens when the series is opened.
- [ ] **New features partly tested on a device.** On the tablet (2026-11-22), server status reached both servers, the settings appear, the sessions list opens with chapter names, and Linked series lists the Suwayomi series. Still to try: actually linking two series, a recap notification, downloading ahead after finishing a Suwayomi chapter, and deleting a session.
- [ ] **Untested on a device.** Marking chapters read or unread from the library and Recents screens uses the same code as the series screen, but hasn't been tried on a real device.
