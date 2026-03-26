import SwiftUI
import KotlinSharedUI

struct SecondaryTabsScreen: View {
    @Environment(\.dismiss) private var dismiss
    let onTabSelected: (TabItem) -> Void
    @StateObject private var presenter = KotlinPresenter(presenter: SecondaryTabsPresenter())
    var body: some View {
        Router { _ in
            List {
                StateView(state: presenter.state.items) { data in
                    let items = data.cast(SecondaryTabsPresenter.Item.self)
                    Section {
                        ForEach(items, id: \.self) { item in
                            DisclosureGroup {
                                ForEach(item.tabs) { tab in
                                    Button {
                                        onTabSelected(tab)
                                    } label: {
                                        Label {
                                            TabTitle(title: tab.metaData.title)
                                        } icon: {
                                            TabIcon(icon: tab.metaData.icon, accountType: tab.account, iconOnly: true)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                }
                            } label: {
                                StateView(state: item.user) { user in
                                    UserCompatView(data: user)
                                }
                            }
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
        .navigationTitle("Accounts")
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
