import FlareAppleCore
import FlareAppleUI
import SwiftUI

struct DraftBoxScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.timelineAppearance) private var timelineAppearance

    let onEditDraft: ((String) -> Void)?

    init(onEditDraft: ((String) -> Void)? = nil) {
        self.onEditDraft = onEditDraft
    }

    var body: some View {
        DraftBoxContentView(
            rowMode: .compact,
            showsEditAction: onEditDraft != nil,
            referenceShareImageRenderer: MacReferenceShareImageRenderer(
                colorScheme: colorScheme,
                timelineAppearance: timelineAppearance
            ),
            onEditDraft: onEditDraft
        )
    }
}
