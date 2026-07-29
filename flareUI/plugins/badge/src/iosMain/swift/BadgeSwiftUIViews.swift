@preconcurrency import FlareUI
import FlareUISwiftUI
import SwiftUI

extension SwiftUIBadgeNode: FlareSwiftUIRenderableNode {
  @MainActor
  func render(context: FlareSwiftUIRenderContext) -> some View {
    Button {
      self.performOnClick()
    } label: {
      Text(text)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(
          backgroundColor(for: tone),
          in: RoundedRectangle(cornerRadius: 12)
        )
    }
    .buttonStyle(.plain)
  }

  private func backgroundColor(for tone: BadgeTone) -> Color {
    if tone == .positive {
      return Color.green.opacity(0.28)
    }
    if tone == .warning {
      return Color.orange.opacity(0.34)
    }
    return Color.secondary.opacity(0.16)
  }
}
