import shared
import SwiftUI

struct FlareTabItem<Content: View>: View {
    @StateObject private var router = FlareRouter()

    let content: (FlareRouter) -> Content

    @EnvironmentObject private var appState: FlareAppState

    var body: some View {
        NavigationStack(path: $router.navigationPath) {
            content(router)
                .navigationDestination(for: FlareDestination.self) { destination in
                    FlareDestinationView(destination: destination, appState: appState)
                        .environmentObject(router)
                }
        }
        .sheet(isPresented: $router.isSheetPresented) {
            if let destination = router.activeDestination {
                NavigationStack {
                    FlareDestinationView(destination: destination, appState: appState)
                        .environmentObject(router)
                }
            }
        }
        .fullScreenCover(isPresented: $router.isFullScreenPresented) {
            if let destination = router.activeDestination {
                NavigationStack {
                    FlareDestinationView(destination: destination, appState: appState)
                        .environmentObject(router)
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
