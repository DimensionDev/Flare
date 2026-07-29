# Flare UI benchmarks

These modules compare equivalent non-virtualized trees across all four renderers:

- Android View: `FrameLayout -> LinearLayout -> 100 TextView`;
- Jetpack Compose: `ComposeView -> Box -> Column -> 100 BasicText`;
- UIKit: `UIStackView -> UIStackView -> 100 UILabel`;
- SwiftUI: `UIHostingController -> VStack -> 100 Text`.

Each pair measures mounting 100 text nodes and updating the text of one existing node. Static item
strings are precomputed on both paths, setup and validation are outside the timed section, and the
changed state is read in its own restart scope. The update benchmark therefore does not accidentally
re-execute the surrounding 100-item loop.

Flare warm-mount and update benchmarks reuse a synchronous Recomposer. Cold-mount benchmarks create
that runtime inside the measured section. This keeps routine renderer overhead separate from the
one-time runtime cost and avoids measuring an arbitrary display-frame wait.

## Android

Run the Release/AOT benchmark on a physical, non-debuggable Android device:

```shell
./gradlew -p flareUI :benchmark:android:connectedReleaseAndroidTest
```

The Compose pair uses the same parent Recomposer and attaches each `ComposeView` to a real
`ComponentActivity`; lifecycle setup and teardown use `ActivityScenario`. Android Benchmark writes
the metrics JSON and Perfetto traces below:

```text
flareUI/benchmark/android/build/outputs/connected_android_test_additional_output/
```

For a fast correctness-only run on an emulator:

```shell
./gradlew -p flareUI :benchmark:android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true
```

For local, non-baseline measurements on an emulator, explicitly acknowledge the loss of accuracy:

```shell
./gradlew -p flareUI :benchmark:android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Do not commit emulator values as regression baselines.

## Apple

Generate the XCTest project and run its Release benchmark:

```shell
xcodegen generate --spec flareUI/benchmark/apple/project.yml
xcodebuild \
  -project flareUI/benchmark/apple/FlareUIAppleBenchmark.xcodeproj \
  -scheme FlareUIAppleBenchmark \
  -configuration Release \
  -destination 'platform=iOS,id=DEVICE_UDID' \
  test
```

The SwiftUI controllers are attached to the test host's visible `UIWindow`. Without that attachment,
`UIHostingController` can defer body evaluation and report only the cost of its lazy shell.

XCTest records ten samples after one unrecorded warm-up. UIKit warm mounts batch 10 operations and
all updates batch 20 operations, so divide those reported samples by 10 or 20 for per-operation
values. SwiftUI and cold mounts record one operation per sample; keeping one hosting controller
attached avoids changing the parent window's workload during a sample. UIKit and SwiftUI use the
same synchronous benchmark Recomposer; production `CADisplayLink` scheduling latency is
intentionally outside these CPU microbenchmarks.

Simulator measurements are useful for local paired comparisons only. Use the same physical device,
OS, Release configuration, and thermal conditions for tracked baselines.
