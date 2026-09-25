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

The tests cover source changes, page bookmark lifetime, prepend/append, complete
replacement, likes, coalesced inputs, refresh begin/end, footer updates, VVO's
shared numeric offset, and image geometry through repeated column/width changes.
Pull-refresh results stay queued until the elastic gesture settles, and only the
latest input commits. Native interaction tests also cover prepends during refresh
reveal and measured height changes to a partly hidden card while dragging or
decelerating; the next visible card must keep its screen position.
They also verify that fractional column widths use measured cell heights instead
of retaining the 240pt estimate when layout and cache widths round differently.
The fixtures yield the main actor while settling: a nested synchronous run loop
cannot drain the controller's serialized main-queue submissions reliably.

The implementation has four state owners:

- Page containers keep `TimelineReadingState` only for their live tabs/queries.
  Closing a detail navigation entry discards its bookmark. Restoration never
  issues paging requests for data no longer retained by the source.
- The controller installs one content/snapshot generation at a time, retaining
  only the latest pending input. It builds full snapshots for structural changes;
  payload changes only reconfigure affected IDs. Accessories use the same commit.
- `TimelineCollectionView` owns native scrolling, refresh presentation and
  transient layout bookmarks. Explicit scrolling supersedes restoration; passive
  geometry bookmarks preserve the original offset through a width roundtrip.
- Cells measure their current width and report once per fresh measurement.
  Controller-local height storage retains at most two geometries per live item.
  `TimelineAutoplay` handles playback without controlling list offsets.
