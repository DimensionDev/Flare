import SwiftUI
import WishKit

struct WishlistView: View {
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        WishKit
            .FeedbackListView()
            .navigationTitle("wishlist")
            .navigationBarTitleDisplayMode(.inline)
            .background(theme.primaryBackgroundColor)
    }
}
