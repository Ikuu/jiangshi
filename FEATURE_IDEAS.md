# Feature ideas

Ideas from the Suwayomi and Kavita sync work, started 2026-11-08, last added to on 2026-11-23 and checked against 1.11.0 on 2026-11-26. Open ideas are grouped by area, with done ones and the release they shipped in at the end.

## To do

### Kavita

| Idea | What you'd get | Effort |
| --- | --- | --- |
| **Kavita ratings** | The score in the Kavita tracking sheet is saved on the server | Small |
| **Kavita collections and reading lists** | Browse a collection or reading list from the Kavita source, in its own order | Medium |

### Suwayomi

| Idea | What you'd get | Effort |
| --- | --- | --- |
| **Remove read chapters from the server** | Optionally remove a chapter's server download once it's read, keeping the last few, so the server's disk doesn't fill up | Small |
| **Suwayomi server library** | A button when browsing the Suwayomi source, listing the series in the server's library, like Kavita's On Deck | Small to medium |
| **Suwayomi categories** | Series take on their categories from the server | Medium |

### Statistics and goals

| Idea | What you'd get | Effort |
| --- | --- | --- |
| **Rest days** | Choose one or two days a week that don't break the streak if you don't read | Small |
| **Export statistics** | Save reading sessions as a CSV file | Small |
| **Older reading in Statistics** | Chapters read before 1.5.0 count towards Statistics, estimated from the time read and date already in history, and marked as estimated | Small to medium |
| **Statistics widget** | A home screen widget with the current streak and today's progress towards the goal. The app already has a widget module to build on | Medium |

### Series pages and reader

| Idea | What you'd get | Effort |
| --- | --- | --- |
| **Finish by a date** | Set a date to finish a series, and its page says how much a day that needs, next to the time left | Small |
| **Time left in the chapter** | The reader's menu says roughly how long the rest of the chapter would take at your pace | Small |

## Known follow-ups

- [ ] **Linked series during library updates.** Library updates bring in chapters read on a Suwayomi server, but don't pass them on to a linked Kavita series. That happens when the series is opened.
- [ ] **1.8.0 features partly tested on a device.** On the tablet (2026-11-22), server status reached both servers, the settings appear, the sessions list opens with chapter names, and Linked series lists the Suwayomi series. Still to try: actually linking two series (including one outside the library, from 1.9.0), a recap notification, downloading ahead after finishing a Suwayomi chapter, and deleting a session.
- [ ] **Goal progress between chapters untested in the reader.** The Statistics milestones were checked on the tablet (2026-11-23), but the card on the page between chapters, the nudge and the milestone notification haven't been seen on a device yet.
- [ ] **1.11.0 untested on a device.** Month in review, a finished series reaching a milestone between chapters, and Carry on in a linked series after the last chapter haven't been tried on the tablet. The Read button sits on the page between chapters, so check a tap there opens the other series without the reader's tap zones getting in the way.
- [ ] **Untested on a device.** Marking chapters read or unread from the library and Recents screens uses the same code as the series screen, but hasn't been tried on a real device.

## Done

### Kavita

| Idea | What you got | Released |
| --- | --- | --- |
| **Kavita unread fix** | Marking chapters read or unread reaches Kavita, so a finished chapter marked unread stays unread. The reader also sends the page reached and finished chapters. Tested on the tablet 2026-11-19 | 1.6.0 |
| **Queue failed Kavita marks** | Kavita read marks that can't be sent are kept, retried and listed in Waiting to send, like Suwayomi's | 1.6.0 |
| **Kavita On Deck** | An On Deck button when browsing a Kavita source, listing series partway through on the server | 1.6.0 |
| **Remove from On Deck** | Long-press a series in On Deck to take it off | 1.6.0 |
| **Kavita Want to Read** | A Want to Read button when browsing a Kavita source, and add or remove from the series page | 1.7.0 |

### Suwayomi

| Idea | What you got | Released |
| --- | --- | --- |
| **Browse Suwayomi series from Jiangshi** | Suwayomi removed most of its REST API on 2026-11-01. Browsing was confirmed working on a server without it (2026-11-19) | No change needed |
| **Check for new chapters on the server** | Already done by the Suwayomi extension: a refresh asks the server to fetch chapters from the source, while its "Fetch Data From Source" setting is on (the default) | No change needed |
| **Show server-linked trackers** | The tracking sheet shows what's linked on the server and whether it updated, using `trackRecords` | 1.4.0 |
| **Suwayomi sync during library updates** | Unread counts in the library stay current without opening each series. Removes the 1.3.1 trade-off, where read status only synced when a series was opened or refreshed | 1.6.0 |
| **Suwayomi sync separate from "Update tracking when marked as read"** | Manual marks reach Suwayomi without also updating other trackers, and default settings work properly | 1.6.0 |
| **Bookmark sync with Suwayomi** | Bookmarks match on both sides, using `isBookmarked` and the same approach as read status | 1.7.0 |
| **Suwayomi server downloads** | Download chapters on the server or remove them from the series page, with each row saying whether the server has it | 1.7.0 |
| **Deprecated Suwayomi field** | The tracker used `lastReadChapter`, which Suwayomi deprecated in favour of `user.lastReadChapter`. Queries now use the `user` fields and fall back to the old ones on servers from before user accounts | 1.7.0 |
| **Queue the next chapters on the server** | Finishing a Suwayomi chapter asks the server to download the next few unread ones, so they're ready before you get there. Optional, with how many to keep ahead | 1.8.0 |

### Both servers

| Idea | What you got | Released |
| --- | --- | --- |
| **Pending tracker updates** | Settings → Tracking lists tracker updates and Suwayomi read marks still waiting to be sent, with Send now and discard | 1.6.0 |
| **Server status** | Settings shows whether Kavita and Suwayomi can be reached, their version and whether you're signed in, with sign-in errors explained in plain words | 1.8.0 |
| **One series on both servers** | Link a series in Kavita with the same series in Suwayomi, so reading one marks the matching chapters read in the other | 1.8.0 |
| **Link to a series outside the library** | Linked series searches the other server, so the series there doesn't need adding to the library first. Its chapters are fetched from its server when needed, and Clear database keeps it | 1.9.0 |
| **Carry on in the linked series** | After the last chapter of a linked series, the page between chapters offers the next unread chapter in the series on the other server, with a Read button | 1.11.0 |

### Statistics and goals

| Idea | What you got | Released |
| --- | --- | --- |
| **Reading goals** | A goal for minutes or chapters a day, week, month or year on the Statistics page, with a warning when today's streak is at risk | 1.7.0 |
| **Year in review** | A summary of each year's reading, opened from the Statistics page | 1.7.0 |
| **Streak reminder notification** | A notification in the evening when the streak would end, rather than only a note on the Statistics page | 1.7.0 |
| **Edit reading sessions** | See each session for a day and delete wrong ones, such as a reader left open, so the stats can be fixed. Opened from a day in the reading calendar | 1.8.0 |
| **Weekly recap** | An optional notification on the last evening of the week with the week's time read and chapters, compared with the week before | 1.8.0 |
| **Statistics tab** | Statistics has its own tab next to Browse, in place of its ⋮ menu item | 1.9.0 |
| **Larger reading calendar** | The last nine weeks as larger numbered squares with weekday and month names, with Show more for the smaller squares going back further | 1.9.0 |
| **Sessions with covers** | A day's sessions show the series cover, chapter, start time, length, pages and a tick for a finished chapter, and open the series when tapped | 1.9.0 |
| **Merged reading sessions** | Reading the same chapter again within 15 minutes adds to its last session, so turning the screen off or rotating no longer splits one sitting into many | 1.9.0 |
| **Milestones** | Round numbers reached for chapters, hours, pages, series and days in a row on the Statistics page, with the next ones to aim for, personal bests and an optional notification. Series finished aren't counted, as reading sessions don't say when a series is complete | 1.10.0 |
| **Month in review** | The year in review for a single month, with a chart of each day and arrows to step through the months | 1.11.0 |
| **Finished series** | Series that have finished publishing with every chapter read count as finished, dated by the session that finished the last chapter they needed. A milestone, on the Finished list for the period, and in the year and month reviews | 1.11.0 |

### Series pages

| Idea | What you got | Released |
| --- | --- | --- |
| **Series reading history** | Tap the reading line on a series page for each day it was read | 1.7.0 |
| **Time left at your pace** | Series pages estimate how long the unread chapters would take | 1.7.0 |
| **Goal progress between chapters** | The page between chapters shows the reading goal with the chapter just finished counted, the streak, and any milestone it reached | 1.10.0 |
| **"One more chapter" nudge** | When the goal is nearly met, the page between chapters says how much is left, estimating chapters from the series' pace for goals in minutes | 1.10.0 |
