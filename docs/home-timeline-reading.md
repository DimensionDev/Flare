# Home timeline reading sessions

Home timelines on Android, iOS and macOS save the selected tab ID and each tab's
visible post key plus viewport offset. A bookmark at the top is still a bookmark
for that post. It is never a persistent instruction to follow future posts.
Search, detail and discovery feeds keep their existing behavior.

## Refresh behavior

| Action | Data | Reading position |
| --- | --- | --- |
| Open a cached tab | Restore a local window around the bookmark; optionally request a fresh page using the existing launch-refresh setting | Restore the post and offset before launch refresh; new content only shows a notice |
| Pull to refresh / refresh command | Request a fresh page | Keep the current post and offset |
| Scheduled refresh | Request only while the home timeline is visible and the app is active | Follow new content only if continuity is proven and the reader is still stationary at the actual head when the request completes |
| View latest | Atomically replace the current rows and cursors with the prepared snapshot | Go to the latest visible post; no return-to-old-position history |

The notice says “New content” (有新内容), without a count. The action is “View latest”
(查看最新). Network failure does not replace a readable list. A partial failure in a
mixed refresh does not publish a partial snapshot.

A forward cursor and matching newest item must prove that a fresh page connects
to the current list before insertion is permitted. The first version performs
one bounded forward request. Sources without this proof, including mixed feeds,
keep their current list and offer View latest. A timestamp or overlap in a ranked
feed is not treated as proof of continuity. Scheduled refresh also preserves the
old list when there is a gap.

## Storage and lifecycle

`TimelineReadingSession` owns the current and pending paging namespaces. A mixed
session's child cursors and time-merge staging rows belong to the same namespace;
child cursor identities survive reordering of the top-level tabs. Post bodies,
actions and translations continue to use the shared status cache.

`TimelineReadingPagingSource` reads a bounded local window around the saved key,
with local paging in both directions. It does not reload the entire prefix to
restore a distant bookmark. Removed or filtered anchors fall forward to a visible
neighbor, then back to the available beginning if necessary.

Positions are checkpointed after scrolling settles and on page/background exit.
A crash can recover only the last completed checkpoint. Beginning a user scroll
cancels an outstanding restoration. Restoring or refreshing does not overwrite a
bookmark with a loading placeholder.

Bookmarks have no time-based expiry. Removing a tab/account, changing its sources
or ordering configuration, or clearing the cache invalidates the corresponding
session. Renaming and reordering top-level tabs do not change their identities.
Ordinary cache loss may also invalidate bookmarks. The cache schema follows the
app's existing destructive-upgrade policy; no durable copy outside the cache is
maintained.

## Regression coverage

- `TimelineReadingSessionTest`: cold restoration, bounded deep windows, staged
  refreshes, cursor promotion, continuous insertion, timer interaction races,
  repeated refreshes, filtering, mixed-source failures and stale responses after
  cache deletion.
- `TimelineNewPostsScrollTest`: actual Compose grid restoration with a leading
  header and reading offset, insertion preservation and explicit top
  navigation.
- `TimelineControllerTests`: native UIKit restoration through initial loading,
  a top-origin post bookmark and user cancellation, alongside existing refresh,
  layout and scroll-preservation regressions.
- `macosControllerTests/TimelineReadingScrollCheck.swift`: AppKit assertions for
  fractional offsets, insertion preservation, active scrolling and View latest.

After building the macOS Debug scheme with `-derivedDataPath /tmp/flare-reading-macos`,
run the AppKit check from the repository root (the app library supplies the existing
Firebase link dependencies; this does not launch the app):

```sh
swiftc -parse-as-library \
  -F apple-shared/build/xcode-frameworks/Debug/macosx27.0 \
  -F /tmp/flare-reading-macos/Build/Products/Debug \
  -framework FlareAppleUI -lsqlite3 -Xlinker -dead_strip \
  /tmp/flare-reading-macos/Build/Products/Debug/Flare.app/Contents/MacOS/Flare.debug.dylib \
  -Xlinker -rpath -Xlinker /tmp/flare-reading-macos/Build/Products/Debug \
  -Xlinker -rpath -Xlinker /tmp/flare-reading-macos/Build/Products/Debug/Flare.app/Contents/MacOS \
  appleApp/macos/UI/Component/MacTimelineReadingScroll.swift \
  appleApp/macosControllerTests/TimelineReadingScrollCheck.swift \
  -o /tmp/flare-reading-mac-check
/tmp/flare-reading-mac-check
```

Use the installed SDK version in the Kotlin framework path if it differs from 27.0.

Manual acceptance should also cover account switching, cache clearing, source
editing, long media-heavy posts, card/gallery layouts, and suspend/resume on each
platform. Unit and simulator checks do not replace physical-device acceptance.
