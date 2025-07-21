import os.log
import shared
import SwiftUI

struct FlareTabItem<Content: View>: View {
    @Environment(FlareRouter.self) private var router

    let tabType: FlareHomeTabs

    let content: () -> Content

    @Environment(FlareAppState.self) private var appState
    @Environment(FlareTheme.self) private var theme

    init(tabType: FlareHomeTabs, @ViewBuilder content: @escaping () -> Content) {
        self.tabType = tabType
        self.content = content
    }

    var body: some View {
        NavigationStackWrapper(path: router.navigationPathFor(tabType)) {
            content()
                .navigationDestination(for: FlareDestination.self) { destination in
                    FlareDestinationView(destination: destination, router: router)
                        .environment(appState)
                }
                .background(theme.primaryBackgroundColor)
                .foregroundColor(theme.labelColor)
        }
        .onAppear {
            router.selectedTab = tabType
        }
        .environment(\.openURL, OpenURLAction { url in
            if router.handleDeepLink(url) {
                .handled
            } else {
                .systemAction
            }
        })
        .environment(router)
    }
}
