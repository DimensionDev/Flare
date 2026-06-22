import FlareAppleUI
import SwiftUI

struct DraftBoxScreen: View {
    let onEditDraft: ((String) -> Void)?

    init(onEditDraft: ((String) -> Void)? = nil) {
        self.onEditDraft = onEditDraft
    }

    var body: some View {
        DraftBoxContentView(
            rowMode: .compact,
            showsEditAction: onEditDraft != nil,
            onEditDraft: onEditDraft
        )
    }
}
