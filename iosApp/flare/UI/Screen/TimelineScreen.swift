import SwiftUI
import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @Environment(\.openURL) private var openURL
    @State var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: TimelineTabItem) {
        self.tabItem = tabItem
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
        ZStack {
            TimelineItemView(
                key: presenter.key,
                data: presenter.state,
                topPadding: 0,
                onOpenLink: { link in openURL(.init(string: link)!) },
                onExpand: {},
                onCollapse: {},
            )
                .background(Color(.systemGroupedBackground))
                .ignoresSafeArea()
            
        }
    }
}
