import FlareUISwiftUI
import SwiftUI

extension SwiftUIColumnNode: FlareSwiftUIRenderableNode {
  @MainActor
  func render(context: FlareSwiftUIRenderContext) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      FlareSwiftUIChildrenView(
        children: content,
        context: context
      )
    }
  }
}

extension SwiftUIRowNode: FlareSwiftUIRenderableNode {
  @MainActor
  func render(context: FlareSwiftUIRenderContext) -> some View {
    HStack(alignment: .center, spacing: 8) {
      FlareSwiftUIChildrenView(
        children: content,
        context: context
      )
    }
  }
}

extension SwiftUITextNode: FlareSwiftUIRenderableNode {
  @MainActor
  func render(context: FlareSwiftUIRenderContext) -> some View {
    Text(text)
  }
}

extension SwiftUINativeButtonNode: FlareSwiftUIRenderableNode {
  @MainActor
  func render(context: FlareSwiftUIRenderContext) -> some View {
    Button(label) {
      self.performOnClick()
    }
    .disabled(!enabled)
  }
}
