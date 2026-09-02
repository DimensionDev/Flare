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
        Group {
            if #available(iOS 18.0, *) {
                Router { _ in
                    content
                }
            } else {
                content
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

    private var content: some View {
        List {
            StateView(state: presenter.state.items) { data in
                let items = data.cast(SecondaryTabsPresenter.Item.self)
                if !items.isEmpty {
                    Section {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            SecondaryAccountDisclosure(account: item, onTabSelected: onTabSelected)
                        }
                    } header: {
                        Text("account_management_title")
                    }
                }
            }

            Section {
                ForEach(SecondarySidebarStaticRoute.allCases.filter { route in
                    route != .agentHistory || aiAgentEnabledPresenter.state.enabled
                }, id: \.self) { item in
                    NavigationLink(value: item.route) {
                        Label {
                            Text(item.title)
                        } icon: {
                            Image(fontAwesome: item.icon)
                        }
                    }
                }
            }
        }
    }
}

struct SecondaryAccountDisclosure: View {
    let account: SecondaryTabsPresenter.Item
    let onTabSelected: (Route) -> Void

    var body: some View {
        DisclosureGroup {
            ForEach(account.tabs, id: \.self) { tab in
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
            StateView(state: account.user) { user in
                UserCompatView(data: user)
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
