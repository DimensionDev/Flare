import SwiftUI
import KotlinSharedUI

struct SettingsScreen: View {
    var body: some View {
        List {
            NavigationLink(value: Route.accountManagement) {
                Label {
                    Text("account_management_title")
                    Text("account_management_description")
                } icon: {
                    Image(.faCircleUser)
                }
            }

            Section {
                NavigationLink(value: Route.appearanceTheme) {
                    Label {
                        Text("appearance_theme_group_title")
                        Text("appearance_theme_group_subtitle")
                    } icon: {
                        Image("fa-palette")
                    }
                }
                NavigationLink(value: Route.appearanceLayout) {
                    Label {
                        Text("appearance_layout_group_title")
                        Text("appearance_layout_group_subtitle")
                    } icon: {
                        Image("fa-table-list")
                    }
                }
                NavigationLink(value: Route.appearanceDisplay) {
                    Label {
                        Text("appearance_display_group_title")
                        Text("appearance_display_group_subtitle")
                    } icon: {
                        Image(.faNewspaper)
                    }
                }
                NavigationLink(value: Route.appearanceMedia) {
                    Label {
                        Text("appearance_media_group_title")
                        Text("appearance_media_group_subtitle")
                    } icon: {
                        Image(.faPhotoFilm)
                    }
                }
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    Button {
                        UIApplication.shared.open(url)
                    } label: {
                        Label {
                            Text("system_settings_title")
                            Text("system_settings_description")
                        } icon: {
                            Image(.faGear)
                        }
                    }
                }
                
//                StateView(state: presenter.state.user) { _ in
//                    NavigationLink(value: Route.moreMenuCustomize) {
//                        Label {
//                            Text("more_panel_customize")
//                        } icon: {
//                            Image("fa-table-list")
//                        }
//                    }
//                }
            }

            Section {
                NavigationLink(value: Route.localFilter) {
                    Label {
                        Text("local_filter_title")
                        Text("local_filter_description")
                    } icon: {
                        Image("fa-filter")
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
                NavigationLink(value: Route.translationConfig) {
                    Label {
                        Text("settings_translation_title")
                        Text("settings_translation_description")
                    } icon: {
                        Image("fa-language")
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
        .navigationTitle("settings_title")
    }
}
