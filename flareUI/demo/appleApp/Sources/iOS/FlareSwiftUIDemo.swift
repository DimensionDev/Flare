@preconcurrency import FlareUI
import FlareUIBadgeSwiftUI
import FlareUIFoundationSwiftUI
import FlareUISwiftUI
import Foundation
import Observation
import SwiftUI

struct FlareSwiftUIDemoView: View {
  @State private var model = FlareSwiftUITreeModel()

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

@Observable
private final class FlareSwiftUITreeModel: FlareSwiftUIInvalidationModel {
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

  let host: FlareDemoSwiftUIHost
  let registry: FlareSwiftUIRendererRegistry

  @ObservationIgnored
  private let observer: Observer

  @ObservationIgnored
  private let revisions =
    NSMapTable<AnyObject, FlareSwiftUIRevision>(
      keyOptions: .weakMemory,
      valueOptions: .strongMemory
    )

  init() {
    let plugins: [any FlareSwiftUIPlugin] = [
      FoundationSwiftUIPlugin(),
      BadgeSwiftUIPlugin(),
    ]
    let host =
      FlareDemoSwiftUIHost(
        plugins: plugins.map { $0 as FlareSwiftUINodePlugin }
      )
    let observer = Observer()
    self.host = host
    registry = FlareSwiftUIRendererRegistry(plugins: plugins)
    self.observer = observer
    observer.onNodeDidChange = { [weak self] node in
      self?.revisions.object(forKey: node)?.invalidate()
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

  isolated deinit {
    host.setObserver(observer: nil)
    host.dispose()
  }
}
