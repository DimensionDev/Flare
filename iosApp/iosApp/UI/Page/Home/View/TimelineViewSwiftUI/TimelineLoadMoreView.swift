import shared
import SwiftUI

struct TimelineLoadMoreView: View {
    let isRefreshing: Bool

    var body: some View {
        HStack {
            if isRefreshing {
                ProgressView()
                    .scaleEffect(0.8)
                Text("Loading more...")
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else {
                Text("Pull to load more")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 50)
    }
}
