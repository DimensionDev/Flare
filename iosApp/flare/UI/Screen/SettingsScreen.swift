import SwiftUI
import KotlinSharedUI

struct SettingsScreen: View {
    @Environment(\.tabKey) private var tabKeyEnv
    @Environment(\.isActive) private var isActive
    @StateObject private var presenter = KotlinPresenter(presenter: ActiveAccountPresenter())

    var body: some View {
        ScrollViewReader { proxy in
            List {
                Group {
                    StateView(state: presenter.state.user) { user in
                        if #available(iOS 26.0, *) {
                            NavigationLink(value: Route.accountManagement) {
                                Label {
                                    Text("account_management_title")
                                    Text("account_management_description")
                                } icon: {
                                    AvatarView(data: user.avatar)
                                        .frame(width: 44, height: 44)
                                }
                                .labelReservedIconWidth(44)
                            }
                        } else {
                            NavigationLink(value: Route.accountManagement) {
                                Label {
                                    Text("account_management_title")
                                    Text("account_management_description")
                                } icon: {
                                    AvatarView(data: user.avatar)
                                        .frame(width: 20, height: 20)
                                }
                            }
                        }
                    }
                }
                .id("top")

                Section {
                    NavigationLink(value: Route.appearance) {
                        Label {
                            Text("appearance_title")
                            Text("appearance_description")
                        } icon: {
                            Image("fa-palette")
                        }
                    }
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        Link(destination: url) {
                            Label {
                                Text("system_settings_title")
                                Text("system_settings_description")
                            } icon: {
                                Image(.faGear)
                            }
                        }
                    }
                }

                Section {
                    StateView(state: presenter.state.user) { _ in
                        NavigationLink(value: Route.localFilter) {
                            Label {
                                Text("local_filter_title")
                                Text("local_filter_description")
                            } icon: {
                                Image("fa-filter")
                            }
                        }
                        NavigationLink(value: Route.localHostory) {
                            Label {
                                Text("local_history_title")
                                Text("local_history_description")
                            } icon: {
                                Image("fa-clock-rotate-left")
                            }
                        }
                    }
                    NavigationLink(value: Route.rssManagement) {
                        Label {
                            Text("settings_rss_management_title")
                            Text("settings_rss_management_description")
                        } icon: {
                            Image("fa-square-rss")
                        }
                    }
                    NavigationLink(value: Route.storage) {
                        Label {
                            Text("storage_title")
                            Text("storage_description")
                        } icon: {
                            Image("fa-database")
                        }
                    }
                }

                Section {
                    NavigationLink(value: Route.aiConfig) {
                        Label {
                            Text("ai_config_title")
                            Text("ai_config_description")
                        } icon: {
                            Image("fa-robot")
                        }
                    }
                }

                Section {
                    NavigationLink(value: Route.about) {
                        Label {
                            Text("about_title")
                            Text("about_description")
                        } icon: {
                            Image("fa-circle-info")
                        }
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .scrollToTop)) { notification in
                let targetTab = notification.userInfo?["tab"] as? String
                if isActive && (targetTab == nil || targetTab == tabKeyEnv) {
                    withAnimation {
                        proxy.scrollTo("top", anchor: .top)
                    }
                }
            }
        }
        .navigationTitle("settings_title")
    }
}