@preconcurrency import FlareUI
import FlareUIFoundationSwiftUI
import FlareUISwiftUI
import Foundation
import Observation
import SwiftUI
import UIKit
import XCTest

final class NativeVsFlareSwiftUIBenchmarkTests: XCTestCase {
  @MainActor
  func testMount100TextNativeSwiftUI() {
    let parent = benchmarkParentViewController()

    measure(
      metrics: [XCTClockMetric()],
      options: manualMeasurementOptions()
    ) {
      var controllers: [UIHostingController<NativeSwiftUITextTree>] = []
      controllers.reserveCapacity(Self.mountBatchSize)

      startMeasuring()
      for _ in 0..<Self.mountBatchSize {
        let model = NativeSwiftUITextModel()
        let controller =
          UIHostingController(
            rootView: NativeSwiftUITextTree(model: model)
          )
        attach(controller, to: parent)
        layout(controller.view)
        controllers.append(controller)
      }
      stopMeasuring()

      XCTAssertGreaterThan(controllers.last?.view.bounds.height ?? 0, 0)
      controllers.forEach(detach)
    }
  }

  @MainActor
  func testMount100TextFlareSwiftUIWarmRuntime() {
    let runtime = FlareAppleBenchmarkRuntime()
    let parent = benchmarkParentViewController()
    defer { runtime.dispose() }

    measure(
      metrics: [XCTClockMetric()],
      options: manualMeasurementOptions()
    ) {
      var mounted: [MountedFlareSwiftUITree] = []
      mounted.reserveCapacity(Self.mountBatchSize)

      startMeasuring()
      for _ in 0..<Self.mountBatchSize {
        let tree = MountedFlareSwiftUITree(runtime: runtime)
        waitForFlareMount(model: tree.model)
        attach(tree.controller, to: parent)
        layout(tree.controller.view)
        mounted.append(tree)
      }
      stopMeasuring()

      XCTAssertGreaterThan(mounted.last?.controller.view.bounds.height ?? 0, 0)
      mounted.forEach {
        detach($0.controller)
        $0.dispose()
      }
    }
  }

  @MainActor
  func testMount100TextFlareSwiftUIColdRuntime() {
    let parent = benchmarkParentViewController()

    measure(
      metrics: [XCTClockMetric()],
      options: manualMeasurementOptions()
    ) {
      startMeasuring()
      let runtime = FlareAppleBenchmarkRuntime()
      let tree = MountedFlareSwiftUITree(runtime: runtime)
      waitForFlareMount(model: tree.model)
      attach(tree.controller, to: parent)
      layout(tree.controller.view)
      stopMeasuring()

      XCTAssertEqual(tree.model.host.renderedRootCount(), 1)
      detach(tree.controller)
      tree.dispose()
      runtime.dispose()
    }
  }

  @MainActor
  func testUpdateOneOf100TextNativeSwiftUI() {
    let model = NativeSwiftUITextModel()
    let parent = benchmarkParentViewController()
    let controller =
      UIHostingController(
        rootView: NativeSwiftUITextTree(model: model)
      )
    attach(controller, to: parent)
    layout(controller.view)
    var nextText = Self.updatedText

    measure(
      metrics: [XCTClockMetric()],
      options: automaticMeasurementOptions()
    ) {
      for _ in 0..<Self.updateBatchSize {
        model.text = nextText
        nextText =
          nextText == Self.updatedText
            ? Self.initialText
            : Self.updatedText
        flushSwiftUI(controller.view)
      }
    }

    XCTAssertEqual(model.text, Self.initialText)
    detach(controller)
  }

  @MainActor
  func testUpdateOneOf100TextFlareSwiftUI() {
    let runtime = FlareAppleBenchmarkRuntime()
    let tree = MountedFlareSwiftUITree(runtime: runtime)
    let parent = benchmarkParentViewController()
    waitForFlareMount(model: tree.model)
    attach(tree.controller, to: parent)
    layout(tree.controller.view)
    var nextText = Self.updatedText

    measure(
      metrics: [XCTClockMetric()],
      options: automaticMeasurementOptions()
    ) {
      for _ in 0..<Self.updateBatchSize {
        let previousRevision = tree.model.nodeRevision
        tree.model.host.updateText(value: nextText)
        nextText =
          nextText == Self.updatedText
            ? Self.initialText
            : Self.updatedText
        waitForFlareUpdate(
          after: previousRevision,
          model: tree.model
        )
        flushSwiftUI(tree.controller.view)
      }
    }

    XCTAssertEqual(tree.model.host.currentText(), Self.initialText)
    detach(tree.controller)
    tree.dispose()
    runtime.dispose()
  }

  @MainActor
  private func waitForFlareMount(
    model: FlareSwiftUIBenchmarkModel
  ) {
    let deadline = Date(timeIntervalSinceNow: Self.mountTimeout)
    while model.host.renderedRootCount() != 1 && Date() < deadline {
      RunLoop.main.run(
        until: min(deadline, Date(timeIntervalSinceNow: 0.001))
      )
    }
    precondition(
      model.host.renderedRootCount() == 1,
      "Flare SwiftUI mount did not emit its renderer root."
    )
  }

  @MainActor
  private func waitForFlareUpdate(
    after revision: Int,
    model: FlareSwiftUIBenchmarkModel
  ) {
    let deadline = Date(timeIntervalSinceNow: Self.updateTimeout)
    while model.nodeRevision == revision && Date() < deadline {
      RunLoop.main.run(
        until: min(deadline, Date(timeIntervalSinceNow: 0.001))
      )
    }
    precondition(
      model.nodeRevision != revision,
      "Flare SwiftUI update did not reach its renderer node."
    )
  }

  @MainActor
  private func flushSwiftUI(_ view: UIView) {
    view.setNeedsLayout()
    view.layoutIfNeeded()
  }

  @MainActor
  private func layout(_ view: UIView) {
    view.frame = Self.layoutFrame
    flushSwiftUI(view)
  }

  @MainActor
  private func benchmarkParentViewController() -> UIViewController {
    let window =
      UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .flatMap(\.windows)
      .first(where: \.isKeyWindow)
    return try! XCTUnwrap(window?.rootViewController)
  }

  @MainActor
  private func attach<Content: View>(
    _ controller: UIHostingController<Content>,
    to parent: UIViewController
  ) {
    parent.addChild(controller)
    parent.view.addSubview(controller.view)
    controller.didMove(toParent: parent)
  }

  @MainActor
  private func detach<Content: View>(
    _ controller: UIHostingController<Content>
  ) {
    controller.willMove(toParent: nil)
    controller.view.removeFromSuperview()
    controller.removeFromParent()
  }

  @MainActor
  private func manualMeasurementOptions() -> XCTMeasureOptions {
    let options = automaticMeasurementOptions()
    options.invocationOptions = [.manuallyStart, .manuallyStop]
    return options
  }

  @MainActor
  private func automaticMeasurementOptions() -> XCTMeasureOptions {
    let options = XCTMeasureOptions()
    options.iterationCount = Self.measurementIterations
    return options
  }

  private static let itemCount = 100
  private static let updatedIndex = itemCount / 2
  private static let initialText = "Item 50 A"
  private static let updatedText = "Item 50 B"
  private static let initialItemTexts =
    (0..<itemCount).map { index in
      index == updatedIndex ? initialText : "Item \(index)"
    }
  // Keep only one attached hosting controller per sample. Accumulating ten visible controllers
  // changes the parent window's layout workload and no longer measures independent mounts.
  private static let mountBatchSize = 1
  private static let updateBatchSize = 20
  private static let measurementIterations = 10
  private static let mountTimeout: TimeInterval = 1
  private static let updateTimeout: TimeInterval = 1
  private static let layoutFrame =
    CGRect(x: 0, y: 0, width: 390, height: 10_000)

  @MainActor
  @Observable
  final class NativeSwiftUITextModel {
    var text = initialText
  }

  @MainActor
  private struct NativeSwiftUITextTree: View {
    let model: NativeSwiftUITextModel

    var body: some View {
      VStack(alignment: .leading, spacing: 8) {
        ForEach(0..<itemCount, id: \.self) { index in
          if index == updatedIndex {
            NativeStatefulText(model: model)
          } else {
            Text(initialItemTexts[index])
          }
        }
      }
    }
  }

  @MainActor
  private struct NativeStatefulText: View {
    let model: NativeSwiftUITextModel

    var body: some View {
      Text(model.text)
    }
  }
}

@MainActor
private final class MountedFlareSwiftUITree {
  let model: FlareSwiftUIBenchmarkModel
  let controller: UIHostingController<FlareSwiftUIBenchmarkView>

  init(runtime: FlareAppleBenchmarkRuntime) {
    let model = FlareSwiftUIBenchmarkModel(runtime: runtime)
    self.model = model
    controller =
      UIHostingController(
        rootView: FlareSwiftUIBenchmarkView(model: model)
      )
  }

  func dispose() {
    model.dispose()
  }
}

@MainActor
private struct FlareSwiftUIBenchmarkView: View {
  let model: FlareSwiftUIBenchmarkModel

  var body: some View {
    FlareSwiftUIChildrenView(
      children: model.host.content,
      context: FlareSwiftUIRenderContext(
        registry: model.registry,
        model: model
      )
    )
  }
}

@MainActor
@Observable
private final class FlareSwiftUIBenchmarkModel:
  @preconcurrency FlareSwiftUIInvalidationModel
{
  private final class Observer: NSObject, FlareSwiftUITreeObserver {
    var onNodeDidChange: ((FlareSwiftUINode) -> Void)?
    var onChildrenDidChange: ((FlareSwiftUIChildren) -> Void)?

    func nodeDidChange(node: FlareSwiftUINode) {
      onNodeDidChange?(node)
    }

    func childrenDidChange(children: FlareSwiftUIChildren) {
      onChildrenDidChange?(children)
    }
  }

  let host: FlareSwiftUIBenchmarkHost
  let registry: FlareSwiftUIRendererRegistry
  private(set) var nodeRevision = 0

  @ObservationIgnored
  private let observer: Observer

  @ObservationIgnored
  private let revisions =
    NSMapTable<AnyObject, FlareSwiftUIRevision>(
      keyOptions: .weakMemory,
      valueOptions: .strongMemory
    )

  @ObservationIgnored
  private var disposed = false

  init(runtime: FlareAppleBenchmarkRuntime) {
    let plugins: [any FlareSwiftUIPlugin] = [
      FoundationSwiftUIPlugin(),
    ]
    let host =
      FlareSwiftUIBenchmarkHost(
        plugins: plugins.map { $0 as FlareSwiftUINodePlugin },
        runtime: runtime
      )
    let observer = Observer()
    self.host = host
    registry = FlareSwiftUIRendererRegistry(plugins: plugins)
    self.observer = observer
    observer.onNodeDidChange = { [weak self] node in
      guard let self else { return }
      nodeRevision &+= 1
      revisions.object(forKey: node)?.invalidate()
    }
    observer.onChildrenDidChange = { [weak self] children in
      self?.revisions.object(forKey: children)?.invalidate()
    }
    host.setObserver(observer: observer)
  }

  func revision(for key: AnyObject) -> FlareSwiftUIRevision {
    if let revision = revisions.object(forKey: key) {
      return revision
    }
    let revision = FlareSwiftUIRevision()
    revisions.setObject(revision, forKey: key)
    return revision
  }

  func dispose() {
    guard !disposed else { return }
    disposed = true
    host.setObserver(observer: nil)
    host.dispose()
  }

  isolated deinit {
    if !disposed {
      host.setObserver(observer: nil)
      host.dispose()
    }
  }
}
