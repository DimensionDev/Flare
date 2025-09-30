import SwiftUI
import KotlinSharedUI

struct SecondaryTabsScreen: View {
    let tabs: [HomeTabsPresenterStateHomeTabState.HomeTabItem]
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
                    ForEach(tabs, id: \.tabItem.key) { tab in
                        NavigationLink(value: Route.tabItem(tab.tabItem)) {
                            Label {
                                TabTitle(title: tab.tabItem.metaData.title)
                            } icon: {
                                TabIcon(icon: tab.tabItem.metaData.icon, accountType: tab.tabItem.account, iconOnly: true)
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
