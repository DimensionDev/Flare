import KotlinSharedUI
import SwiftUI

struct RawMediaScreen: View {
    let medias: [any UiMedia]
    let initialIndex: Int
    let preview: String?
    let shareContext: MacMediaShareContext?
    var accountType: AccountType? = nil

    var body: some View {
        MacMediaViewerScreen(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview,
            previewProxy: medias.indices.contains(initialIndex) ? medias[initialIndex].proxy : ProxyRef.Unbound.shared,
            shareContext: shareContext,
            accountType: accountType
        )
    }
}

struct MediaScreen: View {
    let url: String
    let preview: String?
    let customHeaders: [String: String]?
    let proxy: ProxyRef

    init(
        url: String,
        preview: String? = nil,
        customHeaders: [String: String]?,
        proxy: ProxyRef
    ) {
        self.url = url
        self.preview = preview
        self.customHeaders = customHeaders
        self.proxy = proxy
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
            customHeaders: customHeaders,
            proxy: proxy
        )
    }
}
