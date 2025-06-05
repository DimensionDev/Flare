import os.log
import shared
import SwiftUI

struct FlareTabItem<Content: View>: View {
    @ObservedObject var router: FlareRouter

    let tabType: FlareHomeTabs

    let content: (FlareRouter) -> Content

    @EnvironmentObject private var appState: FlareAppState
    @Environment(FlareTheme.self) private var theme

    init(router: FlareRouter, tabType: FlareHomeTabs, @ViewBuilder content: @escaping (FlareRouter) -> Content) {
        self.router = router
        self.tabType = tabType
        self.content = content

        os_log("[FlareTabItem] Initialized with router: %{public}@, tab: %{public}@",
               log: .default, type: .debug,
               String(describing: ObjectIdentifier(router)),
               String(describing: tabType))
    }

    var body: some View {
        NavigationStackWrapper(path: router.navigationPathFor(tabType)) {
            content(router)
                .navigationDestination(for: FlareDestination.self) { destination in
                    FlareDestinationView(destination: destination, router: router, appState: appState)
                }
                .background(theme.primaryBackgroundColor)
                .foregroundColor(theme.labelColor)
        }
        .onAppear {
            router.activeTab = tabType
            os_log("[FlareTabItem] View appeared with router: %{public}@, tab: %{public}@, depth: %{public}d",
                   log: .default, type: .debug,
                   String(describing: ObjectIdentifier(router)),
                   String(describing: tabType),
                   router.navigationDepth)
        }
        .environment(\.openURL, OpenURLAction { url in
            if router.handleDeepLink(url) {
                .handled
            } else {
                .systemAction
            }
        })
        .environmentObject(router)
    }
}
