import SwiftUI
import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @Environment(\.openURL) private var openURL
    @StateObject var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: TimelineTabItem) {
        self.tabItem = tabItem
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
        ZStack {
            TimelineView(key: presenter.key, data: presenter.state, onOpenLink: { link in openURL(.init(string: link)!) })
                .background(Color(.systemGroupedBackground))
                .ignoresSafeArea()
            
        }
    }
}
