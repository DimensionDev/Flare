@preconcurrency import FlareUIDemoKit
import FlareUIDemoKotlinBridge
import FlareUIAppKit
import FlareUISwiftUI
import SwiftUI

@main
struct FlareUIDemoMacApp: App {
    var body: some Scene {
        WindowGroup {
            MacDemoRoot()
        }
        .defaultSize(width: 640, height: 480)
    }
}

private struct MacDemoRoot: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Choose the macOS renderer") {
                    NavigationLink {
                        SwiftUIDemoScreen()
                    } label: {
                        Text("SwiftUI")
                    }
                    NavigationLink {
                        AppKitDemoScreen()
                    } label: {
                        Text("AppKit")
                    }
                }
            }
            .navigationTitle("Flare UI Demo")
        }
    }
}

private struct SwiftUIDemoScreen: View {
    var body: some View {
        ScrollView {
            FlareSwiftUIHost(
                resources: .init(bundle: .main)
            ) {
                FlareUIKotlinTreeHost(
                    host: FlareUiAppleDemo.shared.createHost()
                )
            }
            .padding(24)
            .frame(
                maxWidth: .infinity,
                alignment: .topLeading
            )
        }
        .navigationTitle("SwiftUI")
    }
}

private struct AppKitDemoScreen: View {
    var body: some View {
        ScrollView {
            FlareAppKitDemo()
                .padding(24)
                .frame(
                    maxWidth: .infinity,
                    alignment: .topLeading
                )
        }
        .navigationTitle("AppKit")
    }
}

private struct FlareAppKitDemo: NSViewRepresentable {
    func makeNSView(context: Context) -> FlareAppKitHostView {
        FlareAppKitHostView(
            host: FlareUIKotlinTreeHost(
                host: FlareUiAppleDemo.shared.createHost()
            ),
            resources: .init(bundle: .main)
        )
    }

    func updateNSView(
        _ nsView: FlareAppKitHostView,
        context: Context
    ) {
    }
}
