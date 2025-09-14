import SwiftUI
import Awesome
@preconcurrency import KotlinSharedUI

struct HomeTimelineScreen: View {
    let toServiceSelect: () -> Void
    let accountType: AccountType
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @State private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    @State private var showTopBar = true
    
    init(accountType: AccountType, toServiceSelect: @escaping () -> Void) {
        self.accountType = accountType
        self.toServiceSelect = toServiceSelect
        _presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter(accountType: accountType)))
    }
    
    var body: some View {
        StateView(state: presenter.state.tabState) { state in
            let tabs: [TimelineItemPresenterState] = state.cast(TimelineItemPresenterState.self)
            let tab = tabs[selectedTabIndex]
            List {
                Section {
                    PagingView(data: tab.listState) { item in
                        TimelineView(data: item)
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .principal) {
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
                            .if(selectedTabIndex == index) { button in
                                button.buttonStyle(.glassProminent)
                            } else: { button in
                                button.buttonStyle(.glass)
                            }
                        }
                        if case .success = onEnum(of: presenter.state.user) {
                            Button {
                            } label: {
                                Awesome.Classic.Solid.plus.image
                            }
                            .buttonStyle(.glass)
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if case .error(_) = onEnum(of: presenter.state.user) {
                        Button {
                            toServiceSelect()
                        } label: {
                            Text("Login")
                        }
                    } else {
                        Button {
                        } label: {
                            Awesome.Classic.Solid.penToSquare.image
                        }
                    }
                }
            }
            .navigationBarBackButtonHidden()
            .navigationBarTitleDisplayMode(.inline)
            .refreshable {
                if case .success(let success) = onEnum(of: presenter.state.tabState) {
                    if let tab = success.data[selectedTabIndex] as? TimelineItemPresenterState {
                        try? await tab.refreshSuspend()
                    }
                }
            }
        }
    }
}
