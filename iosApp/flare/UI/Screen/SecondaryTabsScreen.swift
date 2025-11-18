import SwiftUI
import KotlinSharedUI

struct SecondaryTabsScreen: View {
    let tabs: [TabItem]
    @StateObject private var activeAccountPresenter = KotlinPresenter(presenter: ActiveAccountPresenter())
    var body: some View {
        Router { _ in
            List {
                StateView(state: activeAccountPresenter.state.user) { user in
                    NavigationLink(value: Route.profileUser(AccountType.Specific(accountKey: user.key), user.key)) {
                        UserCompatView(data: user)
                    }
                }

                Section {
                    ForEach(tabs, id: \.key) { tab in
                        NavigationLink(value: Route.tabItem(tab)) {
                            Label {
                                TabTitle(title: tab.metaData.title)
                            } icon: {
                                TabIcon(icon: tab.metaData.icon, accountType: tab.account, iconOnly: true)
                            }
                        }
                    }
                }

                Section {
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
    }
}
