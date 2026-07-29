@preconcurrency import FlareUI
import Observation
import SwiftUI

/// A generated node only implements this protocol with its actual native SwiftUI body.
public protocol FlareSwiftUIRenderableNode: AnyObject {
  associatedtype RenderedView: View

  @MainActor
  @ViewBuilder
  func render(context: FlareSwiftUIRenderContext) -> RenderedView
}

/// One plugin owns both its Kotlin-runtime node factories and its native SwiftUI views.
public protocol FlareSwiftUIPlugin: FlareSwiftUINodePlugin {
  @MainActor
  func installViews(into registry: FlareSwiftUIRendererRegistry)
}

public protocol FlareSwiftUIInvalidationModel: AnyObject {
  func revision(for key: AnyObject) -> FlareSwiftUIRevision
}

/// Inputs shared by every renderer invocation.
public struct FlareSwiftUIRenderContext {
  fileprivate let registry: FlareSwiftUIRendererRegistry
  fileprivate let model: any FlareSwiftUIInvalidationModel

  public init(
    registry: FlareSwiftUIRendererRegistry,
    model: any FlareSwiftUIInvalidationModel
  ) {
    self.registry = registry
    self.model = model
  }
}

/// Strong metatype registry. `AnyView` is used only after the concrete node type is validated.
@MainActor
public final class FlareSwiftUIRendererRegistry {
  private struct AnyRenderer {
    let render: @MainActor
      (
        FlareSwiftUINode,
        FlareSwiftUIRenderContext
      ) -> AnyView

    @MainActor
    init<Node>(_ nodeType: Node.Type)
    where Node: FlareSwiftUINode, Node: FlareSwiftUIRenderableNode {
      render = { node, context in
        guard let typedNode = node as? Node else {
          return AnyView(
            FlareUnsupportedNodeView(node: node)
          )
        }
        return AnyView(
          typedNode.render(context: context)
          .modifier(
            FlareNodeModifier(testTag: node.testTag)
          )
        )
      }
    }
  }

  private var renderers: [ObjectIdentifier: AnyRenderer] = [:]

  public init(plugins: [any FlareSwiftUIPlugin]) {
    for plugin in plugins {
      plugin.installViews(into: self)
    }
  }

  public func register<Node>(_ nodeType: Node.Type)
  where Node: FlareSwiftUINode, Node: FlareSwiftUIRenderableNode {
    let key = ObjectIdentifier(nodeType)
    precondition(
      renderers[key] == nil,
      "Duplicate SwiftUI renderer for \(nodeType)."
    )
    renderers[key] = AnyRenderer(nodeType)
  }

  fileprivate func view(
    for node: FlareSwiftUINode,
    context: FlareSwiftUIRenderContext
  ) -> AnyView {
    let key = ObjectIdentifier(type(of: node))
    return renderers[key]?.render(node, context)
      ?? AnyView(FlareUnsupportedNodeView(node: node))
  }
}

public struct FlareSwiftUIChildrenView: View {
  let children: FlareSwiftUIChildren
  let context: FlareSwiftUIRenderContext
  private let revision: FlareSwiftUIRevision

  public init(
    children: FlareSwiftUIChildren,
    context: FlareSwiftUIRenderContext
  ) {
    self.children = children
    self.context = context
    revision = context.model.revision(for: children)
  }

  public var body: some View {
    let _ = revision.value
    ForEach(children.nodes, id: \.self) { node in
      FlareSwiftUINodeView(
        node: node,
        context: context
      )
    }
  }
}

private struct FlareSwiftUINodeView: View {
  let node: FlareSwiftUINode
  let context: FlareSwiftUIRenderContext
  private let revision: FlareSwiftUIRevision

  init(
    node: FlareSwiftUINode,
    context: FlareSwiftUIRenderContext
  ) {
    self.node = node
    self.context = context
    revision = context.model.revision(for: node)
  }

  var body: some View {
    let _ = revision.value
    context.registry.view(
      for: node,
      context: context
    )
  }
}

@Observable
public final class FlareSwiftUIRevision {
  public private(set) var value = 0

  public init() {}

  public func invalidate() {
    value &+= 1
  }
}

private struct FlareNodeModifier: ViewModifier {
  let testTag: String?

  @ViewBuilder
  func body(content: Content) -> some View {
    if let testTag {
      content.accessibilityIdentifier(testTag)
    } else {
      content
    }
  }
}

private struct FlareUnsupportedNodeView: View {
  let node: FlareSwiftUINode

  var body: some View {
    Text("Missing SwiftUI renderer: \(String(reflecting: type(of: node)))")
      .foregroundStyle(.red)
  }
}
