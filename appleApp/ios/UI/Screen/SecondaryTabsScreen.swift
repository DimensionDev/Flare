import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct SecondaryTabsScreen: View {
    @Environment(\.dismiss) private var dismiss
    let onTabSelected: (Route) -> Void
    @StateObject private var presenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    @StateObject private var aiAgentEnabledPresenter = KotlinPresenter(presenter: AiAgentEnabledPresenter())
    var body: some View {
        Router { _ in
            List {
                StateView(state: presenter.state.items) { data in
                    let items = data.cast(SecondaryTabsPresenter.Item.self)
                    if !items.isEmpty {
                        Section {
                            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                                DisclosureGroup {
                                    ForEach(item.tabs, id: \.self) { tab in
                                        if let route = route(for: tab) {
                                            Button {
                                                onTabSelected(route)
                                            } label: {
                                                Label {
                                                    Text(tab.title.text)
                                                } icon: {
                                                    Image(fontAwesome: tab.icon.fontAwesomeIcon)
                                                }
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                } label: {
                                    StateView(state: item.user) { user in
                                        UserCompatView(data: user)
                                    }
                                }
                            }
                        } header: {
                            Text("account_management_title")
                        }
                    }
                }

                Section {
                    NavigationLink(value: Route.draftBox) {
                        Label {
                            Text("Drafts")
                        } icon: {
                            Image(fontAwesome: .penToSquare)
                        }
                    }
                    NavigationLink(value: Route.rssManagement) {
                        Label {
                            Text("settings_rss_management_title")
                        } icon: {
                            Image(fontAwesome: .squareRss)
                        }
                    }
                    NavigationLink(value: Route.localHostory) {
                        Label {
                            Text("local_history_title")
                        } icon: {
                            Image(fontAwesome: .clockRotateLeft)
                        }
                    }
                    if aiAgentEnabledPresenter.state.enabled {
                        NavigationLink(value: Route.agentHistory) {
                            Label {
                                Text("agent_history_title")
                            } icon: {
                                Image(fontAwesome: .robot)
                            }
                        }
                    }
                    NavigationLink(value: Route.settings) {
                        Label {
                            Text("settings_title")
                        } icon: {
                            Image(fontAwesome: .gear)
                        }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    dismiss()
                } label: {
                    Image(fontAwesome: .xmark)
                }
            }
        }
    }
}

func route(for tab: SecondaryTabsPresenter.Tab) -> Route? {
    switch onEnum(of: tab.destination) {
    case .route(let destination):
        return Route.fromDeepLinkRoute(deeplinkRoute: destination.route)
    case .timeline(let destination):
        return .timeline(destination.tabItem)
    }
}
