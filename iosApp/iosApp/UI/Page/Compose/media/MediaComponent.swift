import AVKit
import Kingfisher
import shared
import SwiftUI

// medias
struct MediaComponent: View {
    @State var hideSensitive: Bool
    let medias: [UiMedia]
    let onMediaClick: (Int, UiMedia) -> Void
    let sensitive: Bool

    var body: some View {
        let showSensitiveButton = medias.allSatisfy { media in
            media is UiMediaImage || media is UiMediaVideo
        } && sensitive

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
            // 视频sensitive 模糊遮照层
            .if(hideSensitive, transform: { view in
                view.blur(radius: 32)
            })

            if showSensitiveButton {
                SensitiveContentButton(
                    hideSensitive: hideSensitive,
                    action: { hideSensitive.toggle() }
                )
            }
        }
        .frame(maxWidth: 600)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

 
