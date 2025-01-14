#if os(macOS)
import SwiftUI
import NetworkImage
import AVKit
import Kingfisher

struct ImageViewWindow: View {
    let url: String?
    var body: some View {
        if let url = url {
             KFImage(URL(string: url))
                .resizable()
                .scaledToFit()
            // NetworkImage(url: URL(string: url)) { image in
            //     image.resizable().scaledToFit()
            // }
        }
    }
}

struct VideoViewWindow: View {
    let url: String?
    var body: some View {
        if let url = url {
            VideoPlayer(player: AVPlayer(url: URL(string: url)!))
        }
    }
}
#endif
