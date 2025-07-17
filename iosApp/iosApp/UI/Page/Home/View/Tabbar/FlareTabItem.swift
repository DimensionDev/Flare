import os.log
import shared
import SwiftUI

struct FlareTabItem<Content: View>: View {
    @Environment(FlareRouter.self) private var router

    let tabType: FlareHomeTabs

    let content: () -> Content

    @Environment(FlareAppState.self) private var appState
    @Environment(FlareTheme.self) private var theme

     @State private var didAppear: Bool = false
    @State private var isInitialized: Bool = false

    init(tabType: FlareHomeTabs, @ViewBuilder content: @escaping () -> Content) {
        self.tabType = tabType
        self.content = content

        os_log("[FlareTabItem] Initialized with tab: %{public}@",
               log: .default, type: .debug,
               String(describing: tabType))
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

            if !didAppear {
                didAppear = true
                isInitialized = true

                os_log("[FlareTabItem] First time initialization for tab: %{public}@",
                       log: .default, type: .debug,
                       String(describing: tabType))


                performInitialSetup()
            } else {
                os_log("[FlareTabItem] Tab reappeared, skipping initialization for tab: %{public}@",
                       log: .default, type: .debug,
                       String(describing: tabType))
            }


            router.selectedTab = tabType

            os_log("[FlareTabItem] View appeared with router: %{public}@, tab: %{public}@, depth: %{public}d, initialized: %{public}@",
                   log: .default, type: .debug,
                   String(describing: ObjectIdentifier(router)),
                   String(describing: tabType),
                   router.navigationDepth,
                   String(describing: isInitialized))
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

     private func performInitialSetup() {

        switch tabType {
        case .timeline:

            os_log("[FlareTabItem] Initializing Timeline tab",
                   log: .default, type: .debug)
            

        case .menu:
             os_log("[FlareTabItem] Initializing Menu tab",
                   log: .default, type: .debug)

        case .notification:
             os_log("[FlareTabItem] Initializing Notification tab",
                   log: .default, type: .debug)

        case .discover:
             os_log("[FlareTabItem] Initializing Discover tab",
                   log: .default, type: .debug)

        case .profile:
             os_log("[FlareTabItem] Initializing Profile tab",
                   log: .default, type: .debug)
            case .compose:
                os_log("[FlareTabItem] Initializing Profile tab",
                       log: .default, type: .debug)

        }

         os_log("[FlareTabItem] Initial setup completed for tab: %{public}@",
               log: .default, type: .debug,
               String(describing: tabType))
    }
}
