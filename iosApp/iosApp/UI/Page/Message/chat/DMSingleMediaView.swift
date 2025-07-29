import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI

struct DMSingleMediaView: View {
    let viewModel: DMMediaViewModel
    let media: UiMedia

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let previewUrl = viewModel.previewUrl {
                    KFImage(previewUrl)
                        .flareMediaPreview(size: CGSize(width: geometry.size.width * 2, height: geometry.size.height * 2))
                        .placeholder {
                            Rectangle()
                                .foregroundColor(.gray.opacity(0.2))
                        }
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                } else {
                    Rectangle()
                        .foregroundColor(.gray.opacity(0.2))
                }
            }
            .contentShape(Rectangle())
            .overlay {
                if viewModel.mediaKind == .video {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 44))
                        .foregroundColor(.white)
                        .shadow(radius: 8)
                }
            }
            .onTapGesture {
                PhotoBrowserManager.shared.showPhotoBrowser(
                    media: media,
                    images: [media],
                    initialIndex: 0,
                    headers: media.customHeaders ?? [:]
                )
            }
            .onAppear {
                let modifier = AnyModifier { request in
                    var r = request
                    for (key, value) in media.customHeaders ?? [:] {
                        r.setValue(value, forHTTPHeaderField: key)
                    }
                    return r
                }

                KingfisherManager.shared.defaultOptions = [
                    .requestModifier(modifier)
                ]
            }
        }
    }
}

struct DMMediaViewModel {
    let previewUrl: URL?
    let mediaKind: DMMediaKind
    let width: CGFloat
    let height: CGFloat

    static func from(_ media: UiMedia) -> DMMediaViewModel {
        switch media {
        case let image as UiMediaImage:
            DMMediaViewModel(
                previewUrl: URL(string: image.url),
                mediaKind: .image,
                width: CGFloat(image.width),
                height: CGFloat(image.height)
            )
        case let video as UiMediaVideo:
            DMMediaViewModel(
                previewUrl: URL(string: video.thumbnailUrl),
                mediaKind: .video,
                width: CGFloat(video.width),
                height: CGFloat(video.height)
            )
        case let gif as UiMediaGif:
            DMMediaViewModel(
                previewUrl: URL(string: gif.url),
                mediaKind: .gif,
                width: CGFloat(gif.width),
                height: CGFloat(gif.height)
            )
        default:
            DMMediaViewModel(
                previewUrl: nil,
                mediaKind: .unknown,
                width: 200,
                height: 200
            )
        }
    }
}

enum DMMediaKind {
    case image
    case video
    case gif
    case unknown
}
