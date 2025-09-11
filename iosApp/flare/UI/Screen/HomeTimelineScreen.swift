import SwiftUI
import KotlinSharedUI

struct HomeTimelineScreen: View {
    let accountType: AccountType
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @State private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    
    init(accountType: AccountType) {
        self.accountType = accountType
        _presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter(accountType: accountType)))
    }
    
    var body: some View {
        StateView(state: presenter.state.tabState) { state in
            let tabs: [TimelineItemPresenterState] = state.cast(TimelineItemPresenterState.self)
            let tab = tabs[selectedTabIndex]
            ZStack(
                alignment: .topLeading
            ) {
                TimelineItemView(key: tab.timelineTabItem.key, data: tab, topPadding: 50, onOpenLink: { link in openURL(.init(string: link)!) })
                    .background(Color(.systemGroupedBackground))
                    .ignoresSafeArea()
                HStack {
                    ScrollView(.horizontal) {
                        HStack {
                            ForEach(tabs.indices) { index in
                                let tab = tabs[index]
                                Button {
                                    selectedTabIndex = index
                                } label: {
                                    Label {
                                        TabTitle(title: tab.timelineTabItem.metaData.title)
                                    } icon: {
                                        TabIcon(icon: tab.timelineTabItem.metaData.icon, accountType: tab.timelineTabItem.account)
                                    }
                                }
                                .buttonStyle(.glass)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom)
                    }
                    .frame(maxWidth: .infinity)
                    if case .error(_) = onEnum(of: presenter.state.user) {
                        Button {
                            
                        } label: {
                            Text("Login")
                        }
                        .padding(.horizontal)
                        .padding(.bottom)
                        .buttonStyle(.glass)
                    }
                }
            }
        }
    }
}
