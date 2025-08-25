import SwiftUI

struct TimelineWaterfallLoadingView: View {
    @Environment(FlareTheme.self) private var theme

    let columns: Int
    let itemCount: Int

    init(columns: Int = 2, itemCount: Int = 6) {
        self.columns = columns
        self.itemCount = itemCount
    }

    var body: some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible()), count: columns),
            spacing: 8
        ) {
            ForEach(0 ..< itemCount, id: \.self) { _ in
                Rectangle()
                    .fill(theme.secondaryBackgroundColor)
                    .aspectRatio(CGFloat.random(in: 0.7 ... 1.3), contentMode: .fit)
                    .cornerRadius(8)
            }
        }
        .redacted(reason: .placeholder)
        .padding(.horizontal, 16)
        .frame(minHeight: 400)
    }
}
