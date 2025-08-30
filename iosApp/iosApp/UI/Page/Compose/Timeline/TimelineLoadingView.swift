import SwiftUI

struct TimelineLoadingView: View {
    @Environment(FlareTheme.self) private var theme

    let itemCount: Int

    init(itemCount: Int = 5) {
        self.itemCount = itemCount
    }

    var body: some View {
        ForEach(0 ..< itemCount, id: \.self) { _ in
            TimelineStatusViewV2(
                item: createSampleTimelineItem(),
                timelineViewModel: nil
            )
            .redacted(reason: .placeholder)
            .listRowBackground(theme.primaryBackgroundColor)
            .listRowInsets(EdgeInsets())
            .listRowSeparator(.hidden)
        }
    }
}
