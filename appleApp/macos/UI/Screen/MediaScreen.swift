import KotlinSharedUI
import SwiftUI

struct RawMediaScreen: View {
    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?
    let shareContext: MacMediaShareContext?

    var body: some View {
        MacMediaViewerScreen(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview,
            shareContext: shareContext
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
            preview: preview ?? url,
            shareContext: nil
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
