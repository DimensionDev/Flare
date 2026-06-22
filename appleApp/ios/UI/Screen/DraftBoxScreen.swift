import FlareAppleUI
import SwiftUI

struct DraftBoxScreen: View {
    let onEditDraft: (String) -> Void

    var body: some View {
        DraftBoxContentView(
            rowMode: .regular,
            showsEditAction: true,
            onEditDraft: onEditDraft
        )
    }
}
