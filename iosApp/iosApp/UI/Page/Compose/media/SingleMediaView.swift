import Kingfisher
import SwiftUI

// tweet 方格 单个 Media View ，最底层的了
public struct SingleMediaView: View {
    let viewModel: FeedMediaViewModel
    let isSingleVideo: Bool
    let fixedAspectRatio: CGFloat?
    let action: () -> Void
    @Environment(FlareTheme.self) private var theme

    public var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let previewUrl = viewModel.previewUrl {
                    KFImage(previewUrl)
                        .flareTimelineMedia(size: CGSize(width: geometry.size.width, height: geometry.size.height))
                        .placeholder {
                            Rectangle()
                                .foregroundColor(.gray.opacity(0.2))
                        }
                        .resizable()
                        .aspectRatio(fixedAspectRatio, contentMode: .fill)
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
                        .font(.system(size: isSingleVideo ? 38 : 24))
                        .foregroundColor(theme.tintColor)
                        .shadow(radius: 8)
                        .padding(.bottom, 16)
                        .cornerRadius(8)
                        .padding(.leading, 16)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                }
            }
            .onTapGesture {
                FlareHapticManager.shared.buttonPress()
                action()
            }
        }
    }
}
