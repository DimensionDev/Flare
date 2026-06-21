import SwiftUI
import KotlinSharedUI

struct RawMediaScreen: View {
    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?

    var body: some View {
        MediaViewerScreen(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview
        )
    }
}

struct MediaScreen: View {
    let url: String
    let preview: String?
    let customHeaders: [String: String]?

    init(
        url: String,
        preview: String? = nil,
        customHeaders: [String: String]?
    ) {
        self.url = url
        self.preview = preview
        self.customHeaders = customHeaders
    }

    var body: some View {
        RawMediaScreen(
            medias: [media],
            initialIndex: 0,
            preview: preview ?? url
        )
    }

    private var media: UiMediaImage {
        UiMediaImage(
            url: url,
            previewUrl: preview ?? url,
            description: nil,
            height: 0,
            width: 0,
            sensitive: false,
            customHeaders: customHeaders
        )
    }
}
