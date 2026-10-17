# UIKit timeline regression checks

`TimelineControllerTests` is an app-hosted XCTest scheme. It drives the production
controller with native `TimelineContent` inputs and real text/media cells. Paging
adaptation stays in `TimelineContent`; tests supply row arrays and record visible
paging access without a network account or private controller hooks.

```sh
xcodegen generate --spec appleApp/project.yml
xcodebuild test -project appleApp/Flare.xcodeproj -scheme TimelineControllerTests \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

Use a current simulator runtime for app-hosted XCTest. On iOS 18.6 with Xcode 27,
XCTest's class discovery crashes while realizing the existing iOS 26-only
`WebLoginViewModel`, before it discovers these tests. This is why the suite has its
own scheme; the existing lightweight unit and interaction schemes still run on
older runtimes. The controller cases can also run in a diagnostic app after the
normal `AppleSharedHelper` initialization, without XCTest class enumeration.

The tests cover list switching, reloads through placeholders, prepend/append, complete
replacement, likes, coalesced inputs, refresh begin/end, footer updates, VVO's
shared numeric offset, and image geometry through repeated column/width changes.
Pull-refresh results stay queued until the elastic gesture settles, and only the
latest input commits. Native interaction tests also cover prepends during refresh
reveal and measured height changes to a partly hidden card while dragging or
decelerating; the next visible card must keep its screen position.
Consecutive-prepend cases insert ten pages with intervening height refinements,
without caller-side layout passes between updates. They check the current item,
offset, complete item order and ongoing native pan/deceleration in one and two columns.
They also verify that fractional column widths use measured cell heights instead
of retaining the 240pt estimate when layout and cache widths round differently.
Measured-prepend cases let real cell layout report fresh heights while snapshots
and offset compensation are running. Pages arrive on separate scroll frames;
checks use actual cell positions, finger-relative motion and continuing inertia.
Controller regressions use real text posts in a window: 70 upward scroll steps
interleave ten pages and stop at the first unexpected displacement. Both dragging
and deceleration paths verify the final post order and completed restoration.
Layout regressions also reject cell configuration for a temporary viewport and
require one geometry calculation per prepend or height update. The same checks
run during real pan and deceleration gestures. Geometry is compared against the
previous waterfall layout, including fractional widths and tall overlapping rows.
Metadata checks cover reused immutable inputs, changed payloads with the same ID,
reordering, placeholders and pruning of removed items.
The fixtures yield the main actor while settling: a nested synchronous run loop
cannot drain the controller's serialized main-queue submissions reliably.

The implementation has three state owners:

- The controller installs one content/snapshot generation at a time, retaining
  only the latest pending input. It builds full snapshots for structural changes;
  payload changes only reconfigure affected IDs. Accessories use the same commit.
  Unchanged immutable objects reuse their native ID and render hash; the metadata
  cache retains only the current input.
- `TimelineCollectionView` owns native scrolling, refresh presentation and
  transient layout bookmarks. Explicit scrolling supersedes restoration; passive
  geometry bookmarks preserve the original offset through a width roundtrip.
  Its layout applies the active reading anchor before UIKit requests cells and
  reuses geometry for the resulting offset-only invalidation. Timeline headers
  and footers remain ordinary full-width sections.
- Cells measure their current width and report once per fresh measurement.
  Controller-local height storage stages the latest measurement per item until
  the collection captures its viewport. Geometry is published inside that
  preserved layout transaction, retaining at most two committed geometries per item.
  Cells reuse pending measurements without exposing them to the layout or measuring again.
  Snapshot measurements join the same update; unchanged reports do not schedule another layout.
  `TimelineAutoplay` handles playback without controlling list offsets.

Changing a generic list identity creates a fresh list at the top. Home timeline
tabs additionally restore their persisted post and viewport offset through the
shared reading session; queries and detail lists do not. The current list keeps
its position through refreshes, loading placeholders and layout changes. Home
session tests cover restoration after loading, a top-origin post and cancellation
when the reader starts scrolling.
