import SwiftUI
import KotlinSharedUI

struct HomeTimelineScreen: View {
    let accountType: AccountType
    @Environment(\.openURL) private var openURL
    @StateObject var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(accountType: AccountType) {
        self.accountType = accountType
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: HomeTimelineTabItem(accountType: accountType))))
    }
    
    var body: some View {
        ZStack {
            TimelineView(key: presenter.key, data: presenter.state, onOpenLink: { link in openURL(.init(string: link)!) })
                .background(Color(.systemGroupedBackground))
                .ignoresSafeArea()
        }
    }
}
