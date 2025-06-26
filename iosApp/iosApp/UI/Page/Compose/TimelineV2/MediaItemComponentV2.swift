import AVKit
import Kingfisher
import shared
import SwiftUI

struct MediaItemComponentV2: View {
    let media: Media  // 使用Swift Media类型

    var body: some View {
        let viewModel = FeedMediaViewModel.from(media)  // 使用MediaComponentV2中定义的转换方法
        SingleMediaView(
            viewModel: viewModel,
            isSingleVideo: true,
            fixedAspectRatio: nil,
            action: {}
        )
    }
}


