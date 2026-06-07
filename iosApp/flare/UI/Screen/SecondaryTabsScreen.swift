import SwiftUI
import KotlinSharedUI

struct SecondaryTabsScreen: View {
    @Environment(\.dismiss) private var dismiss
    let onTabSelected: (Route) -> Void
    @StateObject private var presenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
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
                                                    Image(tab.icon.imageName)
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
                            Text("Accounts")
                        }
                    }
                }

                Section {
                    NavigationLink(value: Route.draftBox) {
                        Label {
                            Text("Drafts")
                        } icon: {
                            Image(.faPenToSquare)
                        }
                    }
                    NavigationLink(value: Route.rssManagement) {
                        Label {
                            Text("settings_rss_management_title")
                        } icon: {
                            Image("fa-square-rss")
                        }
                    }
                    NavigationLink(value: Route.localHostory) {
                        Label {
                            Text("local_history_title")
                        } icon: {
                            Image("fa-clock-rotate-left")
                        }
                    }
                    NavigationLink(value: Route.agentHistory) {
                        Label {
                            Text("settings_agent_history_title")
                        } icon: {
                            Image("fa-robot")
                        }
                    }
                    NavigationLink(value: Route.settings) {
                        Label {
                            Text("settings_title")
                        } icon: {
                            Image("fa-gear")
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
                    Image("fa-xmark")
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
