import os.log
import shared
import SwiftUI

struct FlareTabItem<Content: View>: View {
    @ObservedObject var router: FlareRouter

    let tabType: HomeTabs

    let content: (FlareRouter) -> Content

    @EnvironmentObject private var appState: FlareAppState

    init(router: FlareRouter, tabType: HomeTabs, @ViewBuilder content: @escaping (FlareRouter) -> Content) {
        self.router = router
        self.tabType = tabType
        self.content = content

        os_log("[FlareTabItem] Initialized with router: %{public}@, tab: %{public}@",
               log: .default, type: .debug,
               String(describing: ObjectIdentifier(router)),
               String(describing: tabType))
    }

    var body: some View {
        NavigationStack(path: router.navigationPathFor(tabType)) {
            content(router)
                .navigationDestination(for: FlareDestination.self) { destination in
                    FlareDestinationView(destination: destination, router: router, appState: appState)
                }
        }
        .onAppear {
            router.activeTab = tabType

            os_log("[FlareTabItem] View appeared with router: %{public}@, tab: %{public}@, depth: %{public}d",
                   log: .default, type: .debug,
                   String(describing: ObjectIdentifier(router)),
                   String(describing: tabType),
                   router.navigationDepth)
        }
        .onChange(of: router.navigationPathFor(tabType).wrappedValue.count) { oldCount, newCount in
            os_log("[FlareTabItem] Navigation path changed for tab %{public}@: %{public}d -> %{public}d",
                   log: .default, type: .debug,
                   String(describing: tabType),
                   oldCount,
                   newCount)
        }
        .sheet(isPresented: $router.isSheetPresented) {
            if let destination = router.activeDestination {
                NavigationStack {
                    FlareDestinationView(destination: destination, router: router, appState: appState)
                }
            }
        }
        .fullScreenCover(isPresented: $router.isFullScreenPresented) {
            if let destination = router.activeDestination {
                NavigationStack {
                    FlareDestinationView(destination: destination, router: router, appState: appState)
                }
                .modifier(SwipeToDismissModifier(onDismiss: {
                    router.dismissFullScreenCover()
                }))
                .presentationBackground(.black)
                .environment(\.colorScheme, .dark)
            }
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

struct SwipeToDismissModifier: ViewModifier {
    var onDismiss: () -> Void
    @State private var offset: CGSize = .zero

    func body(content: Content) -> some View {
        content
            .offset(y: offset.height)
            .animation(.interactiveSpring(), value: offset)
            .simultaneousGesture(
                DragGesture()
                    .onChanged { gesture in
                        if gesture.translation.width < 50 {
                            offset = gesture.translation
                        }
                    }
                    .onEnded { _ in
                        if abs(offset.height) > 100 {
                            offset = .zero
                            onDismiss()
                        } else {
                            offset = .zero
                        }
                    }
            )
    }
}
