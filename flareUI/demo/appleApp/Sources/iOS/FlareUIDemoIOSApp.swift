@preconcurrency import FlareUI
import SwiftUI
import UIKit

@main
struct FlareUIDemoIOSApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                FlareBackendList()
            }
        }
    }
}

private struct FlareBackendList: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Select a renderer backend")
                .font(.title2)

            NavigationLink {
                FlareUIKitDemoPage()
            } label: {
                BackendRow(
                    title: "UIKit",
                    detail: "Native UIView renderer"
                )
            }

            NavigationLink {
                FlareSwiftUIDemoPage()
            } label: {
                BackendRow(
                    title: "SwiftUI",
                    detail: "Native SwiftUI renderer"
                )
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Flare UI Demo")
    }
}

private struct BackendRow: View {
    let title: String
    let detail: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(.tertiary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            Color(uiColor: .secondarySystemGroupedBackground),
            in: RoundedRectangle(cornerRadius: 12)
        )
    }
}

private struct FlareUIKitDemoPage: View {
    var body: some View {
        FlareUIKitDemoView()
            .padding()
            .frame(
                maxWidth: .infinity,
                maxHeight: .infinity,
                alignment: .topLeading
            )
            .navigationTitle("Flare UI · UIKit")
            .navigationBarTitleDisplayMode(.inline)
    }
}

private struct FlareSwiftUIDemoPage: View {
    var body: some View {
        FlareSwiftUIDemoView()
            .padding()
            .frame(
                maxWidth: .infinity,
                maxHeight: .infinity,
                alignment: .topLeading
            )
            .navigationTitle("Flare UI · SwiftUI")
            .navigationBarTitleDisplayMode(.inline)
    }
}

private struct FlareUIKitDemoView: UIViewRepresentable {
    final class Coordinator {
        let host = FlareDemoHost()

        deinit {
            host.dispose()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        context.coordinator.host.view
    }

    func updateUIView(
        _ uiView: UIView,
        context: Context
    ) {
    }
}
