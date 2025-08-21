import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
 
import SwiftUI
import UIKit

struct StatusVisibilityComponent: View {
    let visibility: UiTimelineItemContentStatusTopEndContentVisibility.Type_

    var body: some View {
        switch visibility {
        case .public:
            Awesome.Classic.Solid.globe.image.opacity(0.6)
        case .home:
            Awesome.Classic.Solid.lockOpen.image
        case .followers:
            Awesome.Classic.Solid.lock.image
        case .specified:
            Awesome.Classic.Solid.at.image
        }
    }
}
