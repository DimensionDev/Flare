#if os(macOS)
    import AVKit
    import Kingfisher
    import NetworkImage
    import SwiftUI

    struct ImageViewWindow: View {
        let url: String?
        var body: some View {
            if let url {
                KFImage(URL(string: url))
                    .resizable()
                    .scaledToFit()
                // NetworkImage(url: URL(string: url)) { image in
                //    image.resizable().scaledToFit()
                // }
            }
        }
    }

    struct VideoViewWindow: View {
        let url: String?
        var body: some View {
            if let url {
                VideoPlayer(player: AVPlayer(url: URL(string: url)!))
            }
        }
    }
#endif
