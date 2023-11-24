import SwiftUI
import shared
import NetworkImage

struct MediaComponent: View {
    let medias: [UiMedia]
    let onMediaClick: (UiMedia) -> Void
    var body: some View {
        let columns = if medias.count == 1 {
            [GridItem(.flexible())]
        } else if medias.count < 5 {
            [GridItem(.flexible()), GridItem(.flexible())]
        } else {
            [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

        }
        LazyVGrid(columns: columns) {
            if medias.count == 1 {
                switch onEnum(of: medias[0]) {
                case .image(let image):
                    MediaItemComponent(media: medias[0], onMediaClick: { onMediaClick(medias[0])})
                        .aspectRatio(.init(image.aspectRatio), contentMode: .fill)
                case .video(let video):
                    MediaItemComponent(media: medias[0], onMediaClick: { onMediaClick(medias[0])})
                        .aspectRatio(.init(video.aspectRatio), contentMode: .fill)
                case .gif(let gif):
                    MediaItemComponent(media: medias[0], onMediaClick: { onMediaClick(medias[0])})
                        .aspectRatio(.init(gif.aspectRatio), contentMode: .fill)
                case .audio(_):
                    MediaItemComponent(media: medias[0], onMediaClick: { onMediaClick(medias[0])})
                }
            } else {
                ForEach(1...medias.count, id: \.self) { index in
                    MediaItemComponent(media: medias[index - 1], onMediaClick: { onMediaClick(medias[index - 1])})
                        .aspectRatio(1, contentMode: .fill)
                }
            }
        }
    }
}

struct MediaItemComponent: View {
    let media: UiMedia
    let onMediaClick: () -> Void
    var body: some View {
        Button(action: {
            onMediaClick()
            }, label: {
            ZStack {
                Color.clear.overlay {
                    switch onEnum(of: media) {
                    case .image(let data):
                        NetworkImage(url: URL(string: data.previewUrl)){ image in
                            image.resizable().scaledToFill()
                    }
                    case .video(let video):
                        NetworkImage(url: URL(string: video.thumbnailUrl)){ image in
                            image.resizable().scaledToFill()
                    }
                    case .audio(_):
                        Text("")
                    case .gif(let gif):
                        NetworkImage(url: URL(string: gif.previewUrl)){ image in
                            image.resizable().scaledToFill()
                    }
                    }
                }
            }
        })
        .buttonStyle(.borderless)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .clipped()
    }
}
