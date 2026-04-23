import SwiftUI
import KotlinSharedUI
import LazyPager
import Kingfisher

struct MediaScreen: View {
    let url: String
    @Environment(\.dismiss) var dismiss
    @State var opacity: CGFloat = 1 // Dismiss gesture background opacity
    @State private var shareImage: UIImage?
    @State private var shareImageURL: String?

    var body: some View {
        LazyPager(data: [url]) { item in
            AdaptiveKFImage(data: item, placeholder: nil)
        }
        .onDismiss(backgroundOpacity: $opacity) {
            dismiss()
        }
        .zoomable(min: 1, max: 5)
        .settings { config in
            config.preloadAmount = 99
        }
        .task(id: url) {
            await loadShareImage(url: url)
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
                    MediaSaver.shared.saveImage(url: url)
                } label: {
                    Image("fa-download")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                if let shareImage, shareImageURL == url {
                    ShareLink(
                        item: Image(uiImage: shareImage),
                        preview: SharePreview("Share image", image: Image(uiImage: shareImage))
                    ) {
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

    private func loadShareImage(url: String) async {
        shareImage = nil
        shareImageURL = url

        guard let imageURL = URL(string: url) else {
            return
        }

        do {
            let result = try await KingfisherManager.shared.retrieveImage(with: imageURL)
            guard !Task.isCancelled, shareImageURL == url else {
                return
            }
            shareImage = result.image
        } catch {
            guard !Task.isCancelled, shareImageURL == url else {
                return
            }
            shareImage = nil
        }
    }
}
