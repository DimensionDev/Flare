import SwiftUI
import shared
import NetworkImage

struct MediaComponent: View {
    let medias: [UiMedia]
    var body: some View {
        // TODO: keep media aspect ratio when only 1 media
        AdaptiveGrid {
            ForEach(1...medias.count, id: \.self) { index in
                MediaItemComponent(media: medias[index - 1])
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
        }.clipped()
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
