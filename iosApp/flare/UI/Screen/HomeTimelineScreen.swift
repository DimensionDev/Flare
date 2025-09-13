import SwiftUI
import KotlinSharedUI

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
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom)
                    }
                    .frame(maxWidth: .infinity)
                    if case .error(_) = onEnum(of: presenter.state.user) {
                        Button {
                            toServiceSelect()
                        } label: {
                            Text("Login")
                        }
                        .padding(.horizontal)
                        .padding(.bottom)
                        .buttonStyle(.bordered)
                    }
                }
                
                PagingView(data: tab.listState) { item in
                    TimelineView(data: item)
                }
            }
            
//            TimelineItemWithHeaderView(data: tab) {
//                HStack {
//                    ScrollView(.horizontal) {
//                        HStack {
//                            ForEach(tabs.indices) { index in
//                                let tab = tabs[index]
//                                Button {
//                                    selectedTabIndex = index
//                                } label: {
//                                    Label {
//                                        TabTitle(title: tab.timelineTabItem.metaData.title)
//                                    } icon: {
//                                        TabIcon(icon: tab.timelineTabItem.metaData.icon, accountType: tab.timelineTabItem.account)
//                                    }
//                                }
//                                .buttonStyle(.bordered)
//                            }
//                        }
//                        .padding(.horizontal)
//                        .padding(.bottom)
//                    }
//                    .frame(maxWidth: .infinity)
//                    if case .error(_) = onEnum(of: presenter.state.user) {
//                        Button {
//                            toServiceSelect()
//                        } label: {
//                            Text("Login")
//                        }
//                        .padding(.horizontal)
//                        .padding(.bottom)
//                        .buttonStyle(.bordered)
//                    }
//                }
//            }
        }
    }
}
