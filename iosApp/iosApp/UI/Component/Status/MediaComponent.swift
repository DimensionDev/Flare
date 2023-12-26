import SwiftUI
import shared
import NetworkImage

struct MediaComponent: View {
    @State var hideSensitive: Bool
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
        ZStack(alignment:.topLeading) {
            LazyVGrid(columns: columns) {
                if medias.count == 1 {
                    Button(action: {
                        onMediaClick(medias[0])
                    }, label: {
                        switch onEnum(of: medias[0]) {
                        case .image(let image):
                            MediaItemComponent(media: medias[0])
                                .aspectRatio(.init(image.aspectRatio), contentMode: .fill)
                        case .video(let video):
                            MediaItemComponent(media: medias[0])
                                .aspectRatio(.init(video.aspectRatio), contentMode: .fill)
                        case .gif(let gif):
                            MediaItemComponent(media: medias[0])
                                .aspectRatio(.init(gif.aspectRatio), contentMode: .fill)
                        case .audio(_):
                            MediaItemComponent(media: medias[0])
                        }
                    })
                    .buttonStyle(.borderless)
                } else {
                    ForEach(1...medias.count, id: \.self) { index in
                        Button(action: {
                            onMediaClick(medias[index - 1])
                        }, label: {
                            MediaItemComponent(media: medias[index - 1])
                                .aspectRatio(1, contentMode: .fill)
                        })
                        .buttonStyle(.borderless)
                    }
                }
            }
            .if(hideSensitive, transform: { view in
                view.blur(radius: 32)
            })
            if hideSensitive {
                Button(action: {
                    withAnimation {
                        hideSensitive = false
                    }
                }, label: {
                    Color.clear
                })
                .buttonStyle(.borderless)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                Button(action: {
                    withAnimation {
                        hideSensitive = false
                    }
                }, label: {
                    Text("Show Media")
                })
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            } else {
                Button(action: {
                    withAnimation {
                        hideSensitive = true
                    }
                }, label: {
                    Image(systemName: "eye.slash")
                })
                .padding()
                .buttonStyle(.borderedProminent)
                .tint(Color.primary)
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct MediaItemComponent: View {
    let media: UiMedia
    var body: some View {
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
        .clipped()
    }
}
