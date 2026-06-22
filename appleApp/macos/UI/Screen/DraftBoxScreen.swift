import FlareAppleUI
import SwiftUI

struct DraftBoxScreen: View {
    var body: some View {
        DraftBoxContentView(
            rowMode: .compact,
            showsEditAction: false
        )
    }
}
