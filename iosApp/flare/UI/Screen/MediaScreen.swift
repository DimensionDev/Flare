import SwiftUI
import KotlinSharedUI
import LazyPager

struct MediaScreen: View {
    let url: String
    let customHeaders: [String: String]?
    @Environment(\.dismiss) var dismiss
    @State var opacity: CGFloat = 1 // Dismiss gesture background opacity
    @State private var shareFileURL: URL?
    @State private var shareFileSourceURL: String?
    @State private var isLandscapeViewing: Bool = false

    var body: some View {
        LazyPager(data: [url]) { item in
            AdaptiveKFImage(data: item, placeholder: nil, customHeader: customHeaders)
        }
        .onDismiss(backgroundOpacity: $opacity) {
            dismiss()
        }
        .zoomable(min: 1, max: 5)
        .settings { config in
            config.preloadAmount = 99
        }
        .task(id: url) {
            await loadShareFile(url: url, customHeaders: customHeaders)
        }
        .onChange(of: isLandscapeViewing) { _, newValue in
            MediaOrientationController.setLandscape(newValue)
        }
        .onDisappear {
            if isLandscapeViewing {
                MediaOrientationController.setLandscape(false)
            }
        }
        .background(.black.opacity(opacity))
        .background(ClearFullScreenBackground())
        .ignoresSafeArea()
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image("fa-xmark")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isLandscapeViewing.toggle()
                    }
                } label: {
                    Image(systemName: isLandscapeViewing ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                }
                .accessibilityLabel(Text(verbatim: isLandscapeViewing ? "Exit landscape view" : "Landscape view"))
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    MediaSaver.shared.saveImage(url: url, customHeaders: customHeaders)
                } label: {
                    Image("fa-download")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                if let shareFileURL, shareFileSourceURL == url {
                    ShareLink(item: shareFileURL) {
                        Image("fa-share-nodes")
                    }
                    .accessibilityLabel("Share image")
                } else {
                    Button {
                    } label: {
                        Image("fa-share-nodes")
                    }
                    .disabled(true)
                    .accessibilityLabel("Share image")
                }
            }
        }
    }

    private func loadShareFile(url: String, customHeaders: [String: String]?) async {
        shareFileURL = nil
        shareFileSourceURL = url

        do {
            let fileURL = try await OriginalImageShareFile.make(url: url, customHeaders: customHeaders)
            guard !Task.isCancelled, shareFileSourceURL == url else {
                return
            }
            shareFileURL = fileURL
        } catch {
            guard !Task.isCancelled, shareFileSourceURL == url else {
                return
            }
            shareFileURL = nil
        }
    }
}
