import AVKit
import Kingfisher
import shared
import SwiftUI

struct MediaItemComponentV2: View {
    let media: Media

    var body: some View {
        let viewModel = FeedMediaViewModel.from(media)
        SingleMediaView(
            viewModel: viewModel,
            isSingleVideo: true,
            fixedAspectRatio: nil,
            action: {}
        )
    }
}
