import AVKit
import Kingfisher
import shared
import SwiftUI

// medias
struct MediaComponentV2: View {
    @State var hideSensitive: Bool
    @State private var aiDetectedSensitive: Bool = false
    @Environment(\.appSettings) private var appSettings
    @Environment(\.isInCaptureMode) private var isInCaptureMode

    let medias: [Media]              // 使用Swift Media类型
    let onMediaClick: (Int, Media) -> Void  // 使用Swift Media类型
    let sensitive: Bool

    var body: some View {
        let showSensitiveButton = medias.allSatisfy { media in
            media.type == .image || media.type == .video  // 使用Swift Media类型判断
        } && (sensitive || aiDetectedSensitive)

        // MARK: - 媒体遮罩逻辑

        // 决定是否对媒体内容应用模糊遮罩
        // 逻辑：
        // 1. 截图模式下禁用模糊（避免渲染错误）
        // 2. 原本敏感内容 + 用户开启遮罩 → 应用模糊
        // 3. AI检测敏感 + 用户开启AI分析 → 应用模糊
        // 注意：时间范围的敏感内容过滤在 StatusItemView.shouldHideInTimeline 中处理
        //      这里只负责媒体遮罩层的显示/隐藏
        let shouldBlur = !isInCaptureMode && (hideSensitive || (aiDetectedSensitive && appSettings.otherSettings.sensitiveContentAnalysisEnabled))

        // 添加详细日志
        let _ = print("🎬 [MediaComponentV2] 开始渲染")
        let _ = print("🎬 [MediaComponentV2] medias.count: \(medias.count)")
        let _ = print("🎬 [MediaComponentV2] medias: \(medias)")
        let _ = print("🎬 [MediaComponentV2] hideSensitive: \(hideSensitive)")
        let _ = print("🎬 [MediaComponentV2] sensitive: \(sensitive)")
        let _ = print("🎬 [MediaComponentV2] shouldBlur: \(shouldBlur)")

        ZStack(alignment: .topLeading) {
            // 使用FeedMediaViewModel.from转换方法
            let mediaViewModels = medias.map { media -> FeedMediaViewModel in
                let viewModel = FeedMediaViewModel.from(media)
                print("🎬 [MediaComponentV2] 转换媒体: \(media.url) -> \(viewModel.id)")
                return viewModel
            }

            let _ = print("🎬 [MediaComponentV2] mediaViewModels.count: \(mediaViewModels.count)")

            // build tweet medias layout
            TweetMediaGridView(
                action: { ctx in
                    let media = medias[ctx.index]
                    onMediaClick(ctx.index, media)  // 直接使用Swift Media类型
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
                        // if sensitive {
                        hideSensitive.toggle()
                        // } else if aiDetectedSensitive {
                        //     // 对于AI检测的敏感内容，可以临时显示
                        //     aiDetectedSensitive = false
                        // }
                    }
                )
            }
        }
        .frame(maxWidth: 600)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .task {
            // 只有在启用AI分析且原本不是敏感内容时才进行分析
            if appSettings.otherSettings.sensitiveContentAnalysisEnabled, !sensitive {
                await analyzeMediaContent()
            }
        }
    }

    private func analyzeMediaContent() async {
        for media in medias {
            if media.type == .image {  // 使用Swift Media类型判断
                let isSensitive = await SensitiveContentAnalyzer.shared.analyzeImage(url: media.url)
                if isSensitive {
                    await MainActor.run {
                        aiDetectedSensitive = true
                    }
                    break // 只要有一个敏感 就覆盖
                }
            }
        }
    }
}

// MARK: - FeedMediaViewModel扩展，添加从Swift Media转换的方法
extension FeedMediaViewModel {
    /// 从Swift Media类型创建FeedMediaViewModel
    /// - Parameter media: Swift Media对象
    /// - Returns: FeedMediaViewModel实例
    static func from(_ media: Media) -> FeedMediaViewModel {
        // 直接创建FeedMediaViewModel，不依赖UiMedia
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

        // 创建一个自定义的FeedMediaViewModel实例
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

// MARK: - FeedMediaViewModel自定义初始化器
extension FeedMediaViewModel {
    /// 自定义初始化器，用于从Swift Media创建FeedMediaViewModel
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
        self.isActive = true
    }
}
