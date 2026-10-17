# macOS reading-position regressions

Run from a macOS GUI session with Xcode command-line tools:

```sh
sh appleApp/macosControllerTests/run-window-checks
```

The existing Desktop CI macOS job runs this command. The check opens isolated
SwiftUI windows and exits with a nonzero status on failure. It needs WindowServer;
a headless SSH session without a GUI login cannot exercise native window layout.
No account, network service, Gradle build or signing identity is needed.

The runner compiles the production `MacTimelineReadingScroll.swift` and
`TimelineReadingAnchor.swift` directly. `Fixtures/KotlinSharedUI.swift` supplies
only deterministic paging inputs and a reading-state protocol. It does not copy
the scroll implementation. The fixture uses the same lazy stack, coordinate
space, snapshot presentation and scroll notifications as `TimelinePagingView`.
The separate `TimelineReadingScrollCheck.swift` exercises the bridge with the real
Kotlin framework when linked against the app's native dependencies.

The window checks cover ten consecutive pages, ten single-row inserts, variable
row heights, an anchor outside the lazy layout window, updates at post boundaries,
and coalescing updates until live scrolling ends,
viewport-height and taller rows with fractional top insets, a new snapshot during
restoration, user interruption, View latest, removal of the old post, explicit
saved-position restoration, a new page arriving with shared's asynchronous
restoration acknowledgement, and cumulative fractional-offset drift during tail
appends or height updates. Each preservation check compares the first visible
post and fractional offset with native AppKit view coordinates, then verifies the
reported reading position and a subsequent layout pass. Full row order is also
checked after consecutive pages. Hidden SwiftUI reuse views are excluded from the
native measurement.

Content-space preferences are intentional. A viewport-space preference can arrive
after the native clip has already moved; using it would save a stale offset even
when the screen looks correct. The scroll bridge combines content geometry with
current clip bounds and finishes compensation at the end of the main run-loop
layout pass. It retains its anchor while layout is incomplete, and cancels
queued compensation when the reader starts another gesture or the view leaves.
Compensation aligns the native clip to the window's backing pixels, avoiding
repeated subpixel rounding by SwiftUI on later layouts.
Half-point layout rounding is kept out of the next preservation target, while
viewport reports still contain the actual visible offset.

The fixture's live-scroll transitions use native notifications, not injected
trackpad gestures. These checks do not replace a signed-in full-app interaction
or energy test. Shared cursor scheduling, ordering and persistence are covered by
the Kotlin regression suites.
