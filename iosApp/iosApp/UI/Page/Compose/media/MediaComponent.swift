import AVKit
import Kingfisher
import shared
import SwiftUI

// medias
struct MediaComponent: View {
    @State var hideSensitive: Bool
    @State private var aiDetectedSensitive: Bool = false
    @Environment(\.appSettings) private var appSettings

    let medias: [UiMedia]
    let onMediaClick: (Int, UiMedia) -> Void
    let sensitive: Bool

    var body: some View {
        let showSensitiveButton = medias.allSatisfy { media in
            media is UiMediaImage || media is UiMediaVideo
        } && (sensitive || aiDetectedSensitive)

        // 如果原本就是敏感内容或AI检测到敏感内容，则应用模糊
        let shouldBlur = hideSensitive || (aiDetectedSensitive && appSettings.otherSettings.sensitiveContentAnalysisEnabled)

        ZStack(alignment: .topLeading) {
            let mediaViewModels = medias.map { media -> FeedMediaViewModel in
                switch media {
                case let image as UiMediaImage:
                    return FeedMediaViewModel(media: image)
                case let video as UiMediaVideo:
                    return FeedMediaViewModel(media: video)
                case let audio as UiMediaAudio:
                    return FeedMediaViewModel(media: audio)
                case let gif as UiMediaGif:
                    return FeedMediaViewModel(media: gif)
                default:
                    fatalError("Unsupported media type")
                }
            }

            // build tweet medias layout
            TweetMediaGridView(
                action: { ctx in
                    let media = medias[ctx.index]
                    onMediaClick(ctx.index, media)
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

            if showSensitiveButton {
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
            if let imageMedia = media as? UiMediaImage {
                let isSensitive = await SensitiveContentAnalyzer.shared.analyzeImage(url: imageMedia.url)
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
