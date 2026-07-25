@preconcurrency import FlareUIDemoKit
import FlareUIDemoKotlinBridge
import FlareUISwiftUI
import FlareUIUIKit
import SwiftUI

@main
struct FlareUIDemoIOSApp: App {
    var body: some Scene {
        WindowGroup {
            IOSDemoRoot()
        }
    }
}

private struct IOSDemoRoot: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Choose the iOS renderer") {
                    NavigationLink {
                        SwiftUIDemoScreen()
                    } label: {
                        Text("SwiftUI")
                    }
                    NavigationLink {
                        UIKitDemoScreen()
                    } label: {
                        Text("UIKit")
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
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .navigationTitle("SwiftUI")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct UIKitDemoScreen: View {
    var body: some View {
        FlareUIKitDemo()
            .padding()
            .frame(
                maxWidth: .infinity,
                maxHeight: .infinity,
                alignment: .topLeading
            )
            .navigationTitle("UIKit")
            .navigationBarTitleDisplayMode(.inline)
    }
}

private struct FlareUIKitDemo: UIViewRepresentable {
    func makeUIView(context: Context) -> FlareUIKitHostView {
        FlareUIKitHostView(
            host: FlareUIKotlinTreeHost(
                host: FlareUiAppleDemo.shared.createHost()
            ),
            resources: .init(bundle: .main)
        )
    }

    func updateUIView(
        _ uiView: FlareUIKitHostView,
        context: Context
    ) {
    }
}
