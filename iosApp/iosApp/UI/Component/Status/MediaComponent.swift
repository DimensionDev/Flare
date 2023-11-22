import SwiftUI
import shared
import NetworkImage

struct MediaComponent: View {
    let medias: [UiMedia]
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
            } else {
                ForEach(1...medias.count, id: \.self) { index in
                    MediaItemComponent(media: medias[index - 1])
                        .aspectRatio(1, contentMode: .fill)
                }
            }
        }
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
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .clipped()
    }
}

#Preview {
    VStack {
        MediaComponent(medias: [UiMediaImage(url: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", previewUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", description: nil, height: 500, width: 1500)])
        
        MediaComponent(
            medias: [UiMediaImage(url: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", previewUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", description: nil, height: 500, width: 1500),UiMediaImage(url: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", previewUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", description: nil, height: 500, width: 1500),UiMediaImage(url: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", previewUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", description: nil, height: 500, width: 1500)]
        )
        .border(Color.black)
    }
}
