import FlareAppleCore
import FlareAppleUI
import SwiftUI

struct DraftBoxScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.timelineAppearance) private var timelineAppearance

    let onEditDraft: (String) -> Void

    var body: some View {
        DraftBoxContentView(
            rowMode: .regular,
            showsEditAction: true,
            referenceShareImageRenderer: IOSReferenceShareImageRenderer(
                colorScheme: colorScheme,
                timelineAppearance: timelineAppearance
            ),
            onEditDraft: onEditDraft
        )
    }
}
