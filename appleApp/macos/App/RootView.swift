import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct RootView: View {
    @State private var selection: HomeTabsPresenterStateHomeTabs? = .home
    @StateObject private var homeTabsPresenter = KotlinPresenter(presenter: HomeTabsPresenter())

    var body: some View {
        HStack(spacing: 0) {
            SidebarView(
                selection: $selection,
                homeTabsPresenter: homeTabsPresenter
            )
            DetailShell(
                destination: selection ?? .home,
                selection: $selection,
                homeTabsPresenter: homeTabsPresenter
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(minWidth: 300, minHeight: 620)
    }
}

#Preview {
    RootView()
}
