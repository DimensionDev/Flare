import SwiftUI
@preconcurrency import KotlinSharedUI

struct HomeTimelineScreen: View {
    let toServiceSelect: () -> Void
    let accountType: AccountType
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @StateObject private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    @State private var showTopBar = true

    init(accountType: AccountType, toServiceSelect: @escaping () -> Void) {
        self.accountType = accountType
        self.toServiceSelect = toServiceSelect
        self._presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter(accountType: accountType)))
    }

    var body: some View {
        StateView(state: presenter.state.tabState) { state in
            let tabs: [TimelineTabItem] = state.cast(TimelineTabItem.self)
            let tab = tabs[selectedTabIndex]
            ZStack {
                TimelineScreen(tabItem: tab)
                    .id(tab.key)
            }
            .toolbar {
                ToolbarItem(placement: .principal) {
                    ScrollView(.horizontal) {
                        GlassEffectContainer {
                            HStack {
                                ForEach(0..<tabs.count, id: \.self) { index in
                                    let tab = tabs[index]
                                    Button {
                                        selectedTabIndex = index
                                    } label: {
                                        Label {
                                            TabTitle(title: tab.metaData.title)
                                        } icon: {
                                            TabIcon(icon: tab.metaData.icon, accountType: tab.account, size: 24)
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
                                        Image("fa-plus")
                                    }
                                    .buttonStyle(.glass)
                                }
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
//                    .scrollClipDisabled()
                }
                .sharedBackgroundVisibility(.hidden)
                ToolbarItem(placement: .topBarTrailing) {
                    if case .error = onEnum(of: presenter.state.user) {
                        Button {
                            toServiceSelect()
                        } label: {
                            Text("Login")
                        }
                    } else {
                        Button {
                        } label: {
                            Image("fa-pen-to-square")
                        }
                    }
                }
            }
            .navigationBarBackButtonHidden()
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
