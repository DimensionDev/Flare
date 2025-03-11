import shared
import SwiftUI

// 为主标签栏创建列表项
enum ListTabItem {
    static func createTab(accountType: AccountType) -> some View {
        NavigationView {
            AllListsView(accountType: accountType)
        }
        .tabItem {
            Label("List", systemImage: "list.bullet")
        }
    }
}
