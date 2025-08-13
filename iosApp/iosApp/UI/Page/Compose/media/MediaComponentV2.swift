import AVKit
import Kingfisher
import shared
import SwiftUI

struct MediaComponentV2: View {
    @State var hideSensitive: Bool
    @Environment(\.appSettings) private var appSettings
    @Environment(\.isInCaptureMode) private var isInCaptureMode

    let medias: [Media]
    let onMediaClick: (Int, Media) -> Void
    let sensitive: Bool

    var body: some View {
        let showSensitiveButton = medias.allSatisfy { media in
            media.type == .image || media.type == .video
        } && sensitive

        // - 媒体遮罩逻辑

        // 决定是否对媒体内容应用模糊遮罩
        // 逻辑：
        // 1. 截图模式下禁用模糊（避免渲染错误）
        // 2. 原本敏感内容 + 用户开启遮罩 → 应用模糊
        // 3. AI检测敏感 + 用户开启AI分析 → 应用模糊
        // 注意：时间范围的敏感内容过滤在 StatusItemView.shouldHideInTimeline 中处理
        //      这里只负责媒体遮罩层的显示/隐藏
        let shouldBlur = !isInCaptureMode && hideSensitive

        // 添加详细日志
//        let _ = FlareLog.debug("MediaComponentV2 开始渲染")
//        let _ = FlareLog.debug("MediaComponentV2 medias.count: \(medias.count)")
//        let _ = FlareLog.debug("MediaComponentV2 medias: \(medias)")
//        let _ = FlareLog.debug("MediaComponentV2 hideSensitive: \(hideSensitive)")
//        let _ = FlareLog.debug("MediaComponentV2 sensitive: \(sensitive)")
//        let _ = FlareLog.debug("MediaComponentV2 shouldBlur: \(shouldBlur)")

        ZStack(alignment: .topLeading) {
            // 使用FeedMediaViewModel.from转换方法
            let mediaViewModels = medias.map { media -> FeedMediaViewModel in
                let viewModel = FeedMediaViewModel.from(media)
                // let _ = FlareLog.debug("MediaComponentV2 转换媒体: \(media.url) -> \(viewModel.id)")
                return viewModel
            }

            // let _ = FlareLog.debug("MediaComponentV2 mediaViewModels.count: \(mediaViewModels.count)")

            // build tweet medias layout
            TweetMediaGridView(
                action: { ctx in
                    let media = medias[ctx.index]
                    onMediaClick(ctx.index, media) // 直接使用Swift Media类型
                },
                viewModels: mediaViewModels,
                idealWidth: 600,
                idealHeight: 280,
                horizontalPadding: 0,
                preferredPaddingGridLayoutOnly: false,
                preferredApplyCornerRadius: true
            )
            .if(shouldBlur, transform: { view in
                view.blur(radius: 32)
            })

            // 在截图模式下隐藏敏感内容按钮，避免渲染冲突
            if showSensitiveButton, !isInCaptureMode {
                SensitiveContentButton(
                    hideSensitive: shouldBlur,
                    action: {
                        hideSensitive.toggle()
                    }
                )
            }
        }
        .frame(maxWidth: 600)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

extension FeedMediaViewModel {
    static func from(_ media: Media) -> FeedMediaViewModel {
        let id = media.url
        let url = URL(string: media.url) ?? URL(string: "about:blank")!
        let previewUrl = URL(string: media.previewUrl ?? media.url)
        let mediaKind: MediaKind
        let videoMedia: VideoMedia?

        switch media.type {
        case .image:
            mediaKind = .image
            videoMedia = nil
        case .video:
            mediaKind = .video
            videoMedia = VideoMedia(state: .loading)
        case .gif:
            mediaKind = .gif
            videoMedia = nil
        case .audio:
            mediaKind = .audio
            videoMedia = nil
        }

        return FeedMediaViewModel(
            id: id,
            url: url,
            previewUrl: previewUrl,
            mediaKind: mediaKind,
            videoMedia: videoMedia,
            playableAsset: nil,
            width: Float(media.width ?? 0),
            height: Float(media.height ?? 0),
            description: media.altText
        )
    }
}

extension FeedMediaViewModel {
    init(
        id: String,
        url: URL,
        previewUrl: URL?,
        mediaKind: MediaKind,
        videoMedia: VideoMedia?,
        playableAsset: PlayableAsset?,
        width: Float,
        height: Float,
        description: String?
    ) {
        self.id = id
        self.url = url
        self.previewUrl = previewUrl
        self.mediaKind = mediaKind
        self.videoMedia = videoMedia
        self.playableAsset = playableAsset
        self.width = width
        self.height = height
        self.description = description
        isActive = true
    }
}
