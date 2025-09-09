import SwiftUI
import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @StateObject var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: TimelineTabItem) {
        self.tabItem = tabItem
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
        ZStack {
            TimelineView(key: presenter.key, data: presenter.state)
                .background(Color(.systemGroupedBackground))
                .ignoresSafeArea()
            
        }
    }
}
