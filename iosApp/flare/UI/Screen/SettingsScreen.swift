import SwiftUI
import KotlinSharedUI

struct SettingsScreen: View {
    @State private var presenter = KotlinPresenter(presenter: ActiveAccountPresenter())

    var body: some View {
        List {
            StateView(state: presenter.state.user) { user in
                NavigationLink(value: Route.accountManagement) {
                    HStack {
                        AvatarView(data: user.avatar)
                            .frame(width: 44, height: 44)
                        Text("account_management_title")
                    }
                }
            }

            Section {
                NavigationLink(value: Route.appearance) {
                    Label {
                        Text("appearance_title")
                    } icon: {
                        Image("fa-palette")
                    }
                }
                StateView(state: presenter.state.user) { _ in
                    NavigationLink(value: Route.moreMenuCustomize) {
                        Label {
                            Text("more_panel_customize")
                        } icon: {
                            Image("fa-table-list")
                        }
                    }
                }
            }

            StateView(state: presenter.state.user) { _ in
                Section {
                    NavigationLink(value: Route.localFilter) {
                        Label {
                            Text("local_filter_title")
                        } icon: {
                            Image("fa-filter")
                        }
                    }
                    NavigationLink(value: Route.localHostory) {
                        Label {
                            Text("local_history_title")
                        } icon: {
                            Image("fa-clock-rotate-left")
                        }
                    }
                    NavigationLink(value: Route.storage) {
                        Label {
                            Text("storage_title")
                        } icon: {
                            Image("fa-database")
                        }
                    }
                }
            }

            Section {
                NavigationLink(value: Route.aiConfig) {
                    Label {
                        Text("ai_config_title")
                    } icon: {
                        Image("fa-robot")
                    }

                }
            }

            Section {
                NavigationLink(value: Route.about) {
                    Label {
                        Text("about_title")
                    } icon: {
                        Image("fa-circle-info")
                    }
                }
            }
        }
        .navigationTitle("settings_title")
    }
}
