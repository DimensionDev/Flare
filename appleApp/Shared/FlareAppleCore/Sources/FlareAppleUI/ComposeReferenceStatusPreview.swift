@preconcurrency import KotlinSharedUI
import SwiftUI

public struct ComposeReferenceStatusPreview: View {
    private let data: UiTimelineV2.Post

    public init(data: UiTimelineV2.Post) {
        self.data = data
    }

    public var body: some View {
        StatusView(
            data: data,
            isQuote: true,
            isClickable: false,
            showMedia: false,
            forceHideActions: true
        )
            .padding()
            .clipShape(.rect(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.flareSeparator, lineWidth: 1)
            )
    }
}
