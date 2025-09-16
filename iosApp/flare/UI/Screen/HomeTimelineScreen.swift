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
                TimelinePagingView(data: tab.listState)
                    .listRowSeparator(.hidden)
                    .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                    .padding(.horizontal)
            }
            .listRowSpacing(2)
            .listStyle(.plain)
            .background(Color(.systemGroupedBackground))
            .toolbar {
                ToolbarItem(placement: .principal) {
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
                                        TabIcon(icon: tab.timelineTabItem.metaData.icon, accountType: tab.timelineTabItem.account, size: 24)
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
                                        .foregroundColor(.label)
                                }
                                .buttonStyle(.glass)
                            }
                        }
                    }
                    .scrollClipDisabled()
                }
                .sharedBackgroundVisibility(.hidden)
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
                                .foregroundColor(.label)
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
